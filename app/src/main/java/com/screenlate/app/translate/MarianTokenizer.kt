package com.screenlate.app.translate

import org.json.JSONObject
import java.io.Closeable
import java.io.File

/**
 * Same ids as Hugging Face's MarianTokenizer: the source SentencePiece model splits English into
 * pieces, the shared vocabulary maps pieces to ids, and the target model joins Russian pieces back.
 */
class MarianTokenizer(
    private val source: SentencePiece,
    private val target: SentencePiece,
    vocab: Map<String, Int>,
) : Closeable {
    private val pieceToId = vocab
    private val idToPiece = arrayOfNulls<String>((vocab.values.maxOrNull() ?: -1) + 1).also { pieces ->
        vocab.forEach { (piece, id) -> pieces[id] = piece }
    }
    private val unkId = vocab.getValue(UNK)
    val eosId = vocab.getValue(EOS)
    val padId = vocab.getValue(PAD)
    private val specialIds = setOf(unkId, eosId, padId)

    /** Token ids for [text], ending with the end-of-sentence token. */
    fun encode(text: String): LongArray {
        val pieces = source.encode(text)
        val ids = LongArray(pieces.size + 1)
        pieces.forEachIndexed { i, piece -> ids[i] = (pieceToId[piece] ?: unkId).toLong() }
        ids[pieces.size] = eosId.toLong()
        return ids
    }

    /** Text for generated ids, skipping special tokens like `skip_special_tokens=True` does. */
    fun decode(ids: IntArray): String {
        val pieces = ids.filter { it !in specialIds }.mapNotNull { idToPiece.getOrNull(it) }
        return target.decode(pieces).replace(WORD_BOUNDARY, ' ').trim { it.isWhitespace() }
    }

    override fun close() {
        source.close()
        target.close()
    }

    companion object {
        private const val UNK = "<unk>"
        private const val EOS = "</s>"
        private const val PAD = "<pad>"
        private const val WORD_BOUNDARY = '▁'

        fun load(modelDir: File): MarianTokenizer {
            val json = JSONObject(File(modelDir, "vocab.json").readText())
            val vocab = HashMap<String, Int>(json.length() * 2)
            for (key in json.keys()) vocab[key] = json.getInt(key)
            return MarianTokenizer(
                source = SentencePiece.load(File(modelDir, "source.spm").readBytes()),
                target = SentencePiece.load(File(modelDir, "target.spm").readBytes()),
                vocab = vocab,
            )
        }
    }
}
