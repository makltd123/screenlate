package com.screenlate.app.translate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Translates the sentences the current screen still needs, top to bottom. Each new screen
 * replaces the list: sentences that left the screen are dropped, and the one being translated
 * is abandoned only if the new screen no longer contains it.
 */
class TranslationQueue(
    scope: CoroutineScope,
    private val translate: suspend (String) -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private val wanted = MutableStateFlow<List<String>>(emptyList())

    val pending: Int get() = wanted.value.size

    init {
        scope.launch { work() }
    }

    fun replace(sentences: List<String>) {
        wanted.value = sentences
    }

    private suspend fun work() {
        while (true) {
            val next = wanted.first { it.isNotEmpty() }.first()
            try {
                coroutineScope {
                    val job = launch { translate(next) }
                    val watcher = launch {
                        wanted.first { next !in it }
                        job.cancel()
                    }
                    job.join()
                    watcher.cancel()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure(e)
            }
            wanted.update { list -> list.filterNot { it == next } }
        }
    }
}
