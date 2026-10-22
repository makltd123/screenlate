package com.screenlate.app.translate

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationQueueTest {
    /** Each sentence takes 100 ms of virtual time; finished ones are recorded in order. */
    private fun TestScope.queue(done: MutableList<String>) = TranslationQueue(
        scope = backgroundScope,
        translate = { sentence ->
            delay(100)
            done += sentence
        },
        onFailure = { throw it },
    )

    /** advanceUntilIdle ignores backgroundScope work, so move virtual time on explicitly. */
    private fun TestScope.finish() {
        advanceTimeBy(10_000)
        runCurrent()
    }

    @Test
    fun translatesInScreenOrder() = runTest {
        val done = mutableListOf<String>()
        queue(done).replace(listOf("a", "b", "c"))
        finish()
        assertEquals(listOf("a", "b", "c"), done)
    }

    @Test
    fun abandonsSentenceThatLeftTheScreen() = runTest {
        val done = mutableListOf<String>()
        val queue = queue(done)
        queue.replace(listOf("a", "b"))
        advanceTimeBy(50)
        runCurrent()
        queue.replace(listOf("c"))
        finish()
        assertEquals(listOf("c"), done)
    }

    @Test
    fun keepsWorkingOnSentenceStillOnScreen() = runTest {
        val done = mutableListOf<String>()
        val queue = queue(done)
        queue.replace(listOf("a", "b"))
        advanceTimeBy(50)
        runCurrent()
        queue.replace(listOf("a", "c"))
        finish()
        assertEquals(listOf("a", "c"), done)
    }
}
