package com.screenlate.app.cache

import com.screenlate.app.accessibility.TextItems
import com.screenlate.app.translate.TextSegments
import com.screenlate.app.translate.Translator
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sentence translations: the memory LRU first, then the on-disk cache, then the model.
 * Keys are the normalized source text, so differences in whitespace share one entry.
 */
class TranslationRepository(private val dao: TranslationDao, private val translator: Translator) {
    private val memory = LruMemoryCache<String, String>(MEMORY_ENTRIES)
    private val writesSinceTrim = AtomicInteger()

    val memoryEntries: Int get() = memory.size

    fun cached(sentence: String): String? = memory[key(sentence)]

    /** The translated [text] if all its sentences are in memory, otherwise null. */
    fun assembled(text: String): String? = TextSegments.assemble(text, ::cached)

    /** Copies disk hits for [sentences] into memory and returns how many there were. */
    suspend fun preload(sentences: Collection<String>): Int {
        val missing = sentences.map(::key).distinct().filter { memory[it] == null }
        var found = 0
        for (chunk in missing.chunked(SQL_CHUNK)) {
            val rows = dao.find(chunk)
            if (rows.isEmpty()) continue
            rows.forEach { memory.put(it.source, it.translation) }
            dao.touch(rows.map { it.source }, System.currentTimeMillis())
            found += rows.size
        }
        return found
    }

    suspend fun translate(sentence: String): String {
        val key = key(sentence)
        memory[key]?.let { return it }
        dao.find(listOf(key)).firstOrNull()?.let {
            memory.put(key, it.translation)
            return it.translation
        }
        val translation = translator.translate(key)
        memory.put(key, translation)
        dao.upsert(CachedTranslation(key, translation, sizeOf(key, translation), System.currentTimeMillis()))
        if (writesSinceTrim.incrementAndGet() >= TRIM_EVERY_WRITES) {
            writesSinceTrim.set(0)
            trimDisk()
        }
        return translation
    }

    /** Translates a whole on-screen text the same way the overlay does. */
    suspend fun translateText(text: String): String {
        val sentences = TextSegments.sentences(text).associateWith { translate(it) }
        return TextSegments.assemble(text) { sentences[it] ?: cached(it) } ?: text
    }

    suspend fun diskEntries(): Int = dao.count()

    suspend fun diskBytes(): Long = dao.totalBytes()

    suspend fun clear() {
        memory.clear()
        dao.clear()
    }

    /** Drops the least recently used rows until the cache fits [MAX_DISK_BYTES]. */
    private suspend fun trimDisk() {
        while (dao.totalBytes() > MAX_DISK_BYTES && dao.count() > 0) {
            dao.deleteOldest(TRIM_BATCH)
        }
    }

    private fun key(sentence: String) = TextItems.normalize(sentence)

    private fun sizeOf(source: String, translation: String) =
        source.toByteArray().size + translation.toByteArray().size + ROW_OVERHEAD_BYTES

    private companion object {
        const val MEMORY_ENTRIES = 2000
        const val MAX_DISK_BYTES = 20L * 1024 * 1024
        const val TRIM_EVERY_WRITES = 50
        const val TRIM_BATCH = 200
        const val SQL_CHUNK = 500
        const val ROW_OVERHEAD_BYTES = 48
    }
}
