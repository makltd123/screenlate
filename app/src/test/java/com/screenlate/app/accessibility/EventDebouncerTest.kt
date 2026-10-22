package com.screenlate.app.accessibility

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventDebouncerTest {
    private fun TestScope.debouncer(onFire: () -> Unit) = EventDebouncer(
        scope = backgroundScope,
        quietMs = 250,
        maxWaitMs = 1_000,
        clock = { testScheduler.currentTime },
        action = onFire,
    )

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun firesOnceAfterQuietPeriod() = runTest {
        var fired = 0
        val debouncer = debouncer { fired++ }
        debouncer.onEvent()
        advance(249)
        assertEquals(0, fired)
        advance(1)
        assertEquals(1, fired)
        advance(1_000)
        assertEquals(1, fired)
    }

    @Test
    fun newEventRestartsTheWait() = runTest {
        var fired = 0
        val debouncer = debouncer { fired++ }
        debouncer.onEvent()
        advance(200)
        debouncer.onEvent()
        advance(200)
        assertEquals(0, fired)
        advance(50)
        assertEquals(1, fired)
    }

    @Test
    fun endlessStreamStillFiresAtMaxWait() = runTest {
        var fired = 0
        val debouncer = debouncer { fired++ }
        repeat(10) {
            debouncer.onEvent()
            advance(100)
        }
        assertEquals(1, fired)
    }

    @Test
    fun cancelDropsPendingRun() = runTest {
        var fired = 0
        val debouncer = debouncer { fired++ }
        debouncer.onEvent()
        debouncer.cancel()
        advance(2_000)
        assertEquals(0, fired)
    }
}
