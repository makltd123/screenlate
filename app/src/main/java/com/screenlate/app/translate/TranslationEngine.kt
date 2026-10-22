package com.screenlate.app.translate

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ModelNotReadyException : IllegalStateException("translation model is not installed")

/**
 * Owns the single ONNX translator: opens it on first use, runs all work on one thread,
 * and closes it after five idle minutes to give the memory back.
 */
class TranslationEngine(
    private val modelDir: () -> File?,
    private var threads: Int = DEFAULT_THREADS,
) : Translator {
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var translator: OnnxMarianTranslator? = null
    private var idleUnload: Job? = null

    /** How long the last cold start of the model took, or -1 before the first one. */
    @Volatile
    var lastLoadMillis: Long = -1
        private set

    override suspend fun translate(sentence: String): String = use { it.translate(sentence) }

    /** Runs [block] on the engine thread with the model loaded. */
    suspend fun <T> use(block: suspend (OnnxMarianTranslator) -> T): T = withContext(dispatcher) {
        idleUnload?.cancel()
        val active = translator ?: load()
        try {
            block(active)
        } finally {
            idleUnload = scope.launch {
                delay(IDLE_UNLOAD_MS)
                close()
            }
        }
    }

    /** Takes effect from the next translation; the loaded model is closed so it reopens with [count] threads. */
    suspend fun setThreads(count: Int) = withContext(dispatcher) {
        if (count == threads) return@withContext
        threads = count
        idleUnload?.cancel()
        close()
    }

    suspend fun unload() = withContext(dispatcher) {
        idleUnload?.cancel()
        close()
    }

    private fun load(): OnnxMarianTranslator {
        val dir = modelDir() ?: throw ModelNotReadyException()
        val started = SystemClock.elapsedRealtime()
        return OnnxMarianTranslator.open(dir, threads).also {
            translator = it
            lastLoadMillis = SystemClock.elapsedRealtime() - started
        }
    }

    private fun close() {
        translator?.close()
        translator = null
    }

    private companion object {
        const val DEFAULT_THREADS = 2
        const val IDLE_UNLOAD_MS = 5 * 60 * 1000L
    }
}
