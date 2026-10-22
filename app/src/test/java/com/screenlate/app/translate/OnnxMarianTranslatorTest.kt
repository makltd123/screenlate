package com.screenlate.app.translate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Runs the Kotlin decoding loop on desktop ONNX Runtime and compares token ids with the Python
 * reference produced by the same model, so a decoding bug shows up in CI rather than on the phone.
 */
class OnnxMarianTranslatorTest {
    @Test
    fun reproducesPythonReference() = runBlocking {
        OnnxMarianTranslator.open(ModelTestFiles.modelDir(), threads = 2).use { translator ->
            val reference = ModelTestFiles.reference("translation_reference.json")
            var sentences = 0
            val started = System.nanoTime()
            for (i in 0 until reference.length()) {
                val parts = reference.getJSONObject(i).getJSONArray("sentences")
                for (j in 0 until parts.length()) {
                    val part = parts.getJSONObject(j)
                    val input = translator.tokenizer.encode(part.getString("text"))
                    val expected = part.getJSONArray("output_ids").let { ids -> List(ids.length()) { ids.getInt(it) } }
                    assertEquals(part.getString("text"), expected, translator.translateIds(input).toList())
                    assertEquals(part.getString("onnx"), translator.translate(part.getString("text")))
                    sentences++
                }
            }
            println("Translated $sentences sentences twice in ${(System.nanoTime() - started) / 1_000_000} ms")
        }
    }
}
