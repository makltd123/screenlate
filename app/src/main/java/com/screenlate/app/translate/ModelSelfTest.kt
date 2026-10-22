package com.screenlate.app.translate

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import com.screenlate.app.AppServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * The stage 2 acceptance check, run on the phone: the reference phrases must give exactly the
 * tokens the Python reference gave, and the timings show whether the spec's speed targets hold.
 */
class ModelSelfTest(private val context: Context, private val app: AppServices) {

    class Report(
        val phrasesMatched: Int,
        val phrases: Int,
        val sentencesMatched: Int,
        val sentences: Int,
        val loadMillis: Long,
        val medianPhraseMillis: Long,
        val maxPhraseMillis: Long,
        val totalMillis: Long,
        val cachedPhraseMillis: Double,
        val memoryMb: Int,
        val mismatches: List<String>,
    )

    suspend fun run(): Report {
        val reference = withContext(Dispatchers.IO) {
            JSONArray(context.assets.open(REFERENCE).bufferedReader().use { it.readText() })
        }
        val phrases = List(reference.length()) { reference.getJSONObject(it) }
        app.engine.unload()

        val phraseMillis = ArrayList<Long>()
        val mismatches = ArrayList<String>()
        var sentences = 0
        var sentencesMatched = 0
        var phrasesMatched = 0
        app.engine.use { translator ->
            for (phrase in phrases) {
                val parts = phrase.getJSONArray("sentences")
                val translations = HashMap<String, String>()
                var allMatch = true
                val started = SystemClock.elapsedRealtime()
                for (j in 0 until parts.length()) {
                    val part = parts.getJSONObject(j)
                    val text = part.getString("text")
                    val ids = translator.translateIds(translator.tokenizer.encode(text))
                    val expected = part.getJSONArray("output_ids").let { out -> IntArray(out.length()) { out.getInt(it) } }
                    sentences++
                    if (ids.contentEquals(expected)) sentencesMatched++ else allMatch = false
                    translations[text] = translator.tokenizer.decode(ids)
                }
                phraseMillis += SystemClock.elapsedRealtime() - started
                // Also checks that the app splits sentences exactly like the reference script.
                val assembled = TextSegments.assemble(phrase.getString("text")) { translations[it] }
                if (allMatch && assembled == phrase.getString("onnx")) phrasesMatched++ else mismatches += phrase.getString("text")
            }
        }
        val loadMillis = app.engine.lastLoadMillis

        // Cache path: fill the cache, then time how long showing a cached phrase takes.
        val texts = phrases.map { it.getString("text") }
        texts.forEach { app.repository.translateText(it) }
        val started = System.nanoTime()
        repeat(CACHE_ROUNDS) { texts.forEach { app.repository.assembled(it) } }
        val cachedPhraseMillis = (System.nanoTime() - started) / 1e6 / (CACHE_ROUNDS * texts.size)

        val sorted = phraseMillis.sorted()
        return Report(
            phrasesMatched = phrasesMatched,
            phrases = phrases.size,
            sentencesMatched = sentencesMatched,
            sentences = sentences,
            loadMillis = loadMillis,
            medianPhraseMillis = sorted[sorted.size / 2],
            maxPhraseMillis = sorted.last(),
            totalMillis = phraseMillis.sum(),
            cachedPhraseMillis = cachedPhraseMillis,
            memoryMb = withContext(Dispatchers.Default) { (Debug.getPss() / 1024).toInt() },
            mismatches = mismatches,
        )
    }

    private companion object {
        const val REFERENCE = "selftest.json"
        const val CACHE_ROUNDS = 10
    }
}
