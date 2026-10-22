package com.screenlate.app.translate

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** model.json written by scripts/convert_model.py next to the ONNX files. */
class ModelManifest(
    val encoder: String,
    val decoder: String,
    val padId: Int,
    val eosId: Int,
    val decoderStartId: Int,
    val heads: Int,
    val headDim: Int,
) {
    companion object {
        fun read(modelDir: File): ModelManifest {
            val json = JSONObject(File(modelDir, "model.json").readText())
            return ModelManifest(
                encoder = json.getString("encoder"),
                decoder = json.getString("decoder"),
                padId = json.getInt("pad_id"),
                eosId = json.getInt("eos_id"),
                decoderStartId = json.getInt("decoder_start_id"),
                heads = json.getInt("heads"),
                headDim = json.getInt("head_dim"),
            )
        }
    }
}

/**
 * Opus-MT on ONNX Runtime with greedy decoding: the encoder runs once, then the merged decoder
 * yields one token per step using its KV cache. Mirrors OnnxGreedyTranslator in
 * scripts/reference_translate.py step for step. Not thread-safe.
 */
class OnnxMarianTranslator private constructor(
    private val env: OrtEnvironment,
    private val encoder: OrtSession,
    private val decoder: OrtSession,
    val tokenizer: MarianTokenizer,
    private val manifest: ModelManifest,
) : Closeable {
    private val pastNames = decoder.inputNames.filter { it.startsWith(PAST_PREFIX) }

    suspend fun translate(text: String): String = tokenizer.decode(translateIds(tokenizer.encode(text)))

    /** Generated token ids without the end-of-sentence token. Stops early if the coroutine is cancelled. */
    suspend fun translateIds(inputIds: LongArray): IntArray {
        val shape = longArrayOf(1, inputIds.size.toLong())
        val generated = ArrayList<Int>()
        OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape).use { ids ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(inputIds.size) { 1L }), shape).use { mask ->
                encoder.run(mapOf(INPUT_IDS to ids, ATTENTION_MASK to mask)).use { encoded ->
                    decode(encoded.get(0) as OnnxTensor, mask, maxNewTokens(inputIds.size), generated)
                }
            }
        }
        return generated.toIntArray()
    }

    private suspend fun decode(hidden: OnnxTensor, mask: OnnxTensor, limit: Int, generated: MutableList<Int>) {
        // The first step takes the no-cache branch; its past inputs are ignored but must have length 1.
        val placeholder = OnnxTensor.createTensor(
            env,
            FloatBuffer.allocate(manifest.heads * manifest.headDim),
            longArrayOf(1, manifest.heads.toLong(), 1, manifest.headDim.toLong()),
        )
        val past = HashMap<String, OnnxTensor>()
        pastNames.forEach { past[it] = placeholder }
        // The first result owns the cross-attention cache, used until the end; later results only
        // replace the self-attention cache, so each can be closed once the next one exists.
        var first: OrtSession.Result? = null
        var previous: OrtSession.Result? = null
        try {
            var token = manifest.decoderStartId.toLong()
            for (step in 0 until limit) {
                currentCoroutineContext().ensureActive()
                val useCache = step > 0
                val output = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(token)), longArrayOf(1, 1)).use { tokenTensor ->
                    OnnxTensor.createTensor(env, booleanArrayOf(useCache)).use { flag ->
                        val inputs = HashMap<String, OnnxTensor>(past)
                        inputs[INPUT_IDS] = tokenTensor
                        inputs[ENCODER_MASK] = mask
                        inputs[ENCODER_HIDDEN] = hidden
                        inputs[USE_CACHE] = flag
                        decoder.run(inputs)
                    }
                }
                val next = argmax((output.get(LOGITS).get() as OnnxTensor).floatBuffer)
                for (name in pastNames) {
                    if (!useCache || DECODER_CACHE in name) {
                        past[name] = output.get(name.replace(PAST_PREFIX, PRESENT_PREFIX)).get() as OnnxTensor
                    }
                }
                if (first == null) {
                    first = output
                } else {
                    previous?.close()
                    previous = output
                }
                if (next == manifest.eosId) break
                generated += next
                token = next.toLong()
            }
        } finally {
            previous?.close()
            first?.close()
            placeholder.close()
        }
    }

    /** Highest-scoring token; the padding token is never generated (bad_words_ids in the model config). */
    private fun argmax(logits: FloatBuffer): Int {
        var best = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in 0 until logits.remaining()) {
            if (i == manifest.padId) continue
            val score = logits.get(i)
            if (score > bestScore) {
                best = i
                bestScore = score
            }
        }
        return best
    }

    override fun close() {
        decoder.close()
        encoder.close()
        tokenizer.close()
    }

    companion object {
        private const val INPUT_IDS = "input_ids"
        private const val ATTENTION_MASK = "attention_mask"
        private const val ENCODER_MASK = "encoder_attention_mask"
        private const val ENCODER_HIDDEN = "encoder_hidden_states"
        private const val USE_CACHE = "use_cache_branch"
        private const val LOGITS = "logits"
        private const val PAST_PREFIX = "past_key_values."
        private const val PRESENT_PREFIX = "present."
        private const val DECODER_CACHE = ".decoder."

        /** Generation limit from the spec: about 1.5x the input plus 10 tokens. */
        fun maxNewTokens(inputLength: Int) = (inputLength * 1.5).toInt() + 10

        fun open(modelDir: File, threads: Int): OnnxMarianTranslator {
            val manifest = ModelManifest.read(modelDir)
            val env = OrtEnvironment.getEnvironment()
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(threads)
                options.setInterOpNumThreads(1)
                val encoder = env.createSession(File(modelDir, manifest.encoder).path, options)
                val decoder = env.createSession(File(modelDir, manifest.decoder).path, options)
                return OnnxMarianTranslator(env, encoder, decoder, MarianTokenizer.load(modelDir), manifest)
            }
        }
    }
}
