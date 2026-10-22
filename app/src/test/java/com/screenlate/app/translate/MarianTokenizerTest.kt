package com.screenlate.app.translate

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

/** Compares the app tokenizer with Hugging Face's MarianTokenizer (references from scripts/reference_translate.py). */
class MarianTokenizerTest {
    companion object {
        private lateinit var tokenizer: MarianTokenizer

        @BeforeClass
        @JvmStatic
        fun load() {
            tokenizer = MarianTokenizer.load(ModelTestFiles.modelDir())
        }

        @AfterClass
        @JvmStatic
        fun close() {
            if (::tokenizer.isInitialized) tokenizer.close()
        }
    }

    @Test
    fun encodesExactlyLikePython() {
        val reference = ModelTestFiles.reference("tokenizer_reference.json")
        assertEquals(50, reference.length())
        for (i in 0 until reference.length()) {
            val row = reference.getJSONObject(i)
            val expected = row.getJSONArray("ids").let { ids -> List(ids.length()) { ids.getLong(it) } }
            assertEquals("ids for ${row.getString("text")}", expected, tokenizer.encode(row.getString("text")).toList())
        }
    }

    @Test
    fun decodesExactlyLikePython() {
        val reference = ModelTestFiles.reference("translation_reference.json")
        for (i in 0 until reference.length()) {
            val sentences = reference.getJSONObject(i).getJSONArray("sentences")
            for (j in 0 until sentences.length()) {
                val sentence = sentences.getJSONObject(j)
                val ids = sentence.getJSONArray("output_ids").let { out -> IntArray(out.length()) { out.getInt(it) } }
                assertEquals(sentence.getString("onnx"), tokenizer.decode(ids))
            }
        }
    }
}
