"""Reference data for ScreenLate: tokenizer ids and greedy translations.

The greedy ONNX loop here mirrors the Kotlin OnnxMarianTranslator step for step, so its
output is what the phone should produce. convert_model.py uses it to check the exported
model; run on its own it regenerates the files the Android tests compare against:

    python scripts/reference_translate.py --model-dir build/model/int8 --out-dir dist/reference
"""

import argparse
import difflib
import json
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort

MODEL_ID = "Helsinki-NLP/opus-mt-en-ru"
REVISION = "bb09c99d180016eac6819df3dae68edb1690fdee"
PAD_ID = 62517  # also the decoder start token; never generated
EOS_ID = 0
PHRASES = Path(__file__).with_name("reference_phrases.json")


def max_new_tokens(input_length: int) -> int:
    """Generation limit from the spec: about 1.5x the input plus 10 tokens."""
    return int(input_length * 1.5) + 10


def load_phrases() -> dict:
    return json.loads(PHRASES.read_text(encoding="utf-8"))


class OnnxGreedyTranslator:
    """Encoder once, then the merged decoder one token at a time with the KV cache."""

    def __init__(self, model_dir: Path, threads: int = 2):
        options = ort.SessionOptions()
        options.intra_op_num_threads = threads
        options.inter_op_num_threads = 1
        providers = ["CPUExecutionProvider"]
        self.encoder = ort.InferenceSession(str(model_dir / "encoder_model.onnx"), options, providers=providers)
        self.decoder = ort.InferenceSession(str(model_dir / "decoder_model_merged.onnx"), options, providers=providers)
        self.output_names = [o.name for o in self.decoder.get_outputs()]
        self.past_names = [i.name for i in self.decoder.get_inputs() if i.name.startswith("past_key_values.")]
        config = json.loads((model_dir / "config.json").read_text(encoding="utf-8"))
        self.heads = config["decoder_attention_heads"]
        self.head_dim = config["d_model"] // self.heads

    def describe(self) -> str:
        def fmt(items):
            return ", ".join(f"{i.name}{i.shape}" for i in items)

        return (
            f"encoder in: {fmt(self.encoder.get_inputs())}\n"
            f"decoder in: {fmt(self.decoder.get_inputs())}\n"
            f"decoder out: {fmt(self.decoder.get_outputs())}"
        )

    def translate_ids(self, input_ids: list[int]) -> list[int]:
        ids = np.array([input_ids], dtype=np.int64)
        mask = np.ones_like(ids)
        hidden = self.encoder.run(None, {"input_ids": ids, "attention_mask": mask})[0]

        # First step takes the no-cache branch. The past inputs are ignored there but must exist;
        # a length of 1 (as optimum uses) keeps the shared symbolic dims consistent, a length of 0 does not.
        past = {name: np.zeros((1, self.heads, 1, self.head_dim), dtype=np.float32) for name in self.past_names}
        token = PAD_ID
        use_cache = False
        result = []
        for _ in range(max_new_tokens(len(input_ids))):
            feeds = {
                "input_ids": np.array([[token]], dtype=np.int64),
                "encoder_attention_mask": mask,
                "encoder_hidden_states": hidden,
                "use_cache_branch": np.array([use_cache]),
                **past,
            }
            outputs = dict(zip(self.output_names, self.decoder.run(None, feeds)))
            logits = outputs["logits"][0, -1]
            logits[PAD_ID] = -np.inf
            token = int(np.argmax(logits))
            if token == EOS_ID:
                break
            result.append(token)
            past = {name: outputs[name.replace("past_key_values.", "present.")] for name in self.past_names}
            use_cache = True
        return result


def load_tokenizer(model_dir: Path):
    from transformers import MarianTokenizer

    return MarianTokenizer.from_pretrained(str(model_dir))


def load_torch_model():
    from transformers import MarianMTModel

    model = MarianMTModel.from_pretrained(MODEL_ID, revision=REVISION)
    model.eval()
    return model


def torch_translate(model, tokenizer, text: str) -> str:
    import torch

    encoded = tokenizer(text, return_tensors="pt")
    with torch.no_grad():
        output = model.generate(
            **encoded,
            num_beams=1,
            do_sample=False,
            max_new_tokens=max_new_tokens(encoded.input_ids.shape[1]),
        )
    return decode(tokenizer, output[0])


def decode(tokenizer, ids) -> str:
    """Detokenize exactly as the app does: drop special tokens, no extra space cleanup."""
    return tokenizer.decode(ids, skip_special_tokens=True, clean_up_tokenization_spaces=False)


def word_difference(a: str, b: str) -> int:
    """How many words differ between two translations (insertions, deletions and replacements)."""
    left, right = a.split(), b.split()
    matcher = difflib.SequenceMatcher(a=left, b=right, autojunk=False)
    return sum(max(i2 - i1, j2 - j1) for tag, i1, i2, j1, j2 in matcher.get_opcodes() if tag != "equal")


def tokenizer_reference(tokenizer) -> list[dict]:
    phrases = load_phrases()
    rows = []
    for text in phrases["translate"] + phrases["tokenize_extra"]:
        ids = tokenizer(text).input_ids
        rows.append({"text": text, "ids": ids, "pieces": tokenizer.convert_ids_to_tokens(ids)})
    return rows


def translation_reference(model_dir: Path, tokenizer, torch_model) -> list[dict]:
    translator = OnnxGreedyTranslator(model_dir)
    rows = []
    for text in load_phrases()["translate"]:
        input_ids = tokenizer(text).input_ids
        started = time.perf_counter()
        output_ids = translator.translate_ids(input_ids)
        millis = (time.perf_counter() - started) * 1000
        onnx_text = decode(tokenizer, output_ids)
        torch_text = torch_translate(torch_model, tokenizer, text)
        rows.append(
            {
                "text": text,
                "input_ids": input_ids,
                "output_ids": output_ids,
                "onnx": onnx_text,
                "torch": torch_text,
                "word_difference": word_difference(onnx_text, torch_text),
                "millis": round(millis, 1),
            }
        )
    return rows


def write_references(model_dir: Path, out_dir: Path, torch_model=None) -> list[dict]:
    tokenizer = load_tokenizer(model_dir)
    torch_model = torch_model or load_torch_model()
    out_dir.mkdir(parents=True, exist_ok=True)
    tokens = tokenizer_reference(tokenizer)
    translations = translation_reference(model_dir, tokenizer, torch_model)
    dump = {"ensure_ascii": False, "indent": 1}
    (out_dir / "tokenizer_reference.json").write_text(json.dumps(tokens, **dump), encoding="utf-8")
    (out_dir / "translation_reference.json").write_text(json.dumps(translations, **dump), encoding="utf-8")
    return translations


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("--out-dir", type=Path, required=True)
    args = parser.parse_args()
    for row in write_references(args.model_dir, args.out_dir):
        print(f"[{row['word_difference']}] {row['text']} -> {row['onnx']} | torch: {row['torch']}")


if __name__ == "__main__":
    main()
