package com.screenlate.app.accessibility

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs [action] once events have been quiet for [quietMs].
 * A stream that never goes quiet (video, live counters) still fires every [maxWaitMs].
 * Must be called from a single thread.
 */
class EventDebouncer(
    private val scope: CoroutineScope,
    private val quietMs: Long,
    private val maxWaitMs: Long,
    private val clock: () -> Long,
    private val action: () -> Unit,
) {
    private var pending: Job? = null
    private var burstStart = 0L

    fun onEvent() {
        val now = clock()
        if (pending?.isActive != true) burstStart = now
        pending?.cancel()
        val wait = minOf(quietMs, (burstStart + maxWaitMs - now).coerceAtLeast(0))
        pending = scope.launch {
            delay(wait)
            action()
        }
    }

    fun cancel() {
        pending?.cancel()
        pending = null
    }
}
