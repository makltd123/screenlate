"""Export Helsinki-NLP/opus-mt-en-ru to int8 ONNX for ScreenLate and package it.

1. Export the encoder and the merged decoder with KV cache (optimum).
2. Check the fp32 export against PyTorch: greedy output must match exactly.
3. Dynamic int8 quantization (onnxruntime).
4. Check 20 reference phrases: at most 2 words may differ from PyTorch.
5. Zip the model with the tokenizer files, write the SHA-256, reference data and a report.

    python scripts/convert_model.py --work build/model --out dist
"""

import argparse
import hashlib
import json
import shutil
import sys
import zipfile
from pathlib import Path

from reference_translate import (
    EOS_ID,
    MODEL_ID,
    PAD_ID,
    REVISION,
    OnnxGreedyTranslator,
    decode,
    load_phrases,
    load_tokenizer,
    load_torch_model,
    torch_translate,
    write_references,
)

FORMAT = 1
ZIP_NAME = "opus-mt-en-ru-int8-v1.zip"
ONNX_FILES = ["encoder_model.onnx", "decoder_model_merged.onnx"]
TOKENIZER_FILES = ["source.spm", "target.spm", "vocab.json", "config.json"]
MAX_WORD_DIFFERENCE = 2


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def megabytes(path: Path) -> str:
    return f"{path.stat().st_size / 1e6:.1f} MB"


def export(fp32_dir: Path):
    from optimum.exporters.onnx import main_export

    main_export(MODEL_ID, output=fp32_dir, task="text2text-generation-with-past", revision=REVISION)
    produced = sorted(p.name for p in fp32_dir.iterdir())
    print("exported:", produced)
    missing = [name for name in ONNX_FILES + TOKENIZER_FILES if name not in produced]
    if missing:
        sys.exit(f"export did not produce {missing}")


def check_fp32(fp32_dir: Path, tokenizer, torch_model) -> list[str]:
    """Before quantization the ONNX greedy loop must reproduce PyTorch exactly; a mismatch means a decoding bug."""
    translator = OnnxGreedyTranslator(fp32_dir)
    print(translator.describe())
    mismatches = []
    for text in load_phrases()["translate"]:
        ids = translator.translate_ids(tokenizer(text).input_ids)
        onnx_text = decode(tokenizer, ids)
        torch_text = torch_translate(torch_model, tokenizer, text)
        if onnx_text != torch_text:
            mismatches.append(f"{text!r}: onnx {onnx_text!r} vs torch {torch_text!r}")
    return mismatches


def quantize(fp32_dir: Path, int8_dir: Path):
    from onnxruntime.quantization import QuantType, quantize_dynamic

    int8_dir.mkdir(parents=True, exist_ok=True)
    for name in ONNX_FILES:
        # EnableSubgraph reaches the weights inside the merged decoder's cache / no-cache branches.
        quantize_dynamic(
            fp32_dir / name,
            int8_dir / name,
            weight_type=QuantType.QInt8,
            extra_options={"EnableSubgraph": True},
        )
        print(f"{name}: {megabytes(fp32_dir / name)} -> {megabytes(int8_dir / name)}")
    for path in fp32_dir.iterdir():
        if path.suffix != ".onnx":
            shutil.copy2(path, int8_dir / path.name)


def package(int8_dir: Path, out_dir: Path) -> tuple[Path, str]:
    config = json.loads((int8_dir / "config.json").read_text(encoding="utf-8"))
    names = ONNX_FILES + TOKENIZER_FILES
    manifest = {
        "format": FORMAT,
        "model": MODEL_ID,
        "revision": REVISION,
        "encoder": "encoder_model.onnx",
        "decoder": "decoder_model_merged.onnx",
        "pad_id": PAD_ID,
        "eos_id": EOS_ID,
        "decoder_start_id": config["decoder_start_token_id"],
        "vocab_size": config["vocab_size"],
        "layers": config["decoder_layers"],
        "heads": config["decoder_attention_heads"],
        "head_dim": config["d_model"] // config["decoder_attention_heads"],
        "files": {name: {"bytes": (int8_dir / name).stat().st_size, "sha256": sha256(int8_dir / name)} for name in names},
    }
    (int8_dir / "model.json").write_text(json.dumps(manifest, indent=1), encoding="utf-8")

    out_dir.mkdir(parents=True, exist_ok=True)
    archive = out_dir / ZIP_NAME
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as zipped:
        for name in names + ["model.json"]:
            zipped.write(int8_dir / name, name)
    digest = sha256(archive)
    (out_dir / f"{ZIP_NAME}.sha256").write_text(f"{digest}  {ZIP_NAME}\n", encoding="utf-8")
    return archive, digest


def write_report(out_dir: Path, rows: list[dict], fp32_mismatches: list[str], int8_dir: Path, archive: Path, digest: str):
    lines = [
        f"## {ZIP_NAME}",
        "",
        f"- Source: [{MODEL_ID}](https://huggingface.co/{MODEL_ID}) at `{REVISION}` (Apache-2.0)",
        f"- Archive: {megabytes(archive)}, SHA-256 `{digest}`",
        *(f"- {name}: {megabytes(int8_dir / name)}" for name in ONNX_FILES),
        f"- fp32 ONNX vs PyTorch: {'exact on all phrases' if not fp32_mismatches else f'{len(fp32_mismatches)} mismatches'}",
        "",
        "| English | ONNX int8 (greedy) | PyTorch (greedy) | Words differ | ms |",
        "| --- | --- | --- | --- | --- |",
    ]
    for row in rows:
        lines.append(f"| {row['text']} | {row['onnx']} | {row['torch']} | {row['word_difference']} | {row['millis']} |")
    if fp32_mismatches:
        lines += ["", "### fp32 mismatches", *(f"- {m}" for m in fp32_mismatches)]
    (out_dir / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    notes = [
        "Opus-MT English to Russian, exported to ONNX and quantized to int8 for ScreenLate.",
        "",
        f"Original model: [{MODEL_ID}](https://huggingface.co/{MODEL_ID}) by Helsinki-NLP, Apache License 2.0.",
        "",
        f"SHA-256 of `{ZIP_NAME}`: `{digest}`",
    ]
    (out_dir / "release-notes.md").write_text("\n".join(notes) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--work", type=Path, required=True, help="scratch directory for fp32 and int8 models")
    parser.add_argument("--out", type=Path, required=True, help="where the zip, checksum and reports go")
    args = parser.parse_args()
    fp32_dir, int8_dir = args.work / "fp32", args.work / "int8"

    export(fp32_dir)
    tokenizer = load_tokenizer(fp32_dir)
    torch_model = load_torch_model()
    fp32_mismatches = check_fp32(fp32_dir, tokenizer, torch_model)

    quantize(fp32_dir, int8_dir)
    rows = write_references(int8_dir, args.out / "reference", torch_model)
    archive, digest = package(int8_dir, args.out)
    write_report(args.out, rows, fp32_mismatches, int8_dir, archive, digest)
    print((args.out / "report.md").read_text(encoding="utf-8"))

    too_different = [row["text"] for row in rows if row["word_difference"] > MAX_WORD_DIFFERENCE]
    if fp32_mismatches or too_different:
        sys.exit(f"check failed: fp32 mismatches {len(fp32_mismatches)}, int8 phrases off by >2 words: {too_different}")


if __name__ == "__main__":
    main()
