package com.screenlate.app.translate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs the Kotlin decoding loop on desktop ONNX Runtime and compares token ids with the Python
 * reference produced by the same model, so a decoding bug shows up in CI rather than on the phone.
 * Several rounds, because a multi-threaded runtime could differ from run to run.
 */
class OnnxMarianTranslatorTest {
    @Test
    fun reproducesPythonReference() = runBlocking {
        assumeTrue("this CPU has no VNNI, so its int8 kernels saturate and differ from the reference", exactInt8())
        val reference = ModelTestFiles.reference("translation_reference.json")
        val mismatches = ArrayList<String>()
        OnnxMarianTranslator.open(ModelTestFiles.modelDir(), threads = 2).use { translator ->
            repeat(ROUNDS) { round ->
                val started = System.nanoTime()
                var sentences = 0
                for (i in 0 until reference.length()) {
                    val parts = reference.getJSONObject(i).getJSONArray("sentences")
                    for (j in 0 until parts.length()) {
                        val part = parts.getJSONObject(j)
                        val text = part.getString("text")
                        val expected = part.getJSONArray("output_ids").let { ids -> List(ids.length()) { ids.getInt(it) } }
                        val actual = translator.translateIds(translator.tokenizer.encode(text)).toList()
                        if (actual != expected) mismatches += "round $round: '$text' expected $expected got $actual"
                        val decoded = translator.tokenizer.decode(actual.toIntArray())
                        if (actual == expected && decoded != part.getString("onnx")) {
                            mismatches += "round $round: '$text' decoded '$decoded' instead of '${part.getString("onnx")}'"
                        }
                        sentences++
                    }
                }
                println("Round $round: $sentences sentences in ${(System.nanoTime() - started) / 1_000_000} ms")
            }
        }
        mismatches.forEach(::println)
        assertTrue("mismatches:\n" + mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    /**
     * The reference was made on a CPU that computes int8 products exactly, as ARM phones do.
     * x86 CPUs without VNNI use a saturating instruction and pick different words in a few sentences.
     */
    private fun exactInt8(): Boolean {
        if (System.getProperty("os.arch") == "aarch64") return true
        val cpu = File("/proc/cpuinfo").takeIf { it.isFile }?.readText() ?: return false
        return "avx512_vnni" in cpu || "avx_vnni" in cpu
    }

    private companion object {
        const val ROUNDS = 3
    }
}
