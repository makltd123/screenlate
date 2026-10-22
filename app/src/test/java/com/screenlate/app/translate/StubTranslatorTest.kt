package com.screenlate.app.translate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StubTranslatorTest {
    @Test
    fun wrapsEachTextInOrder() = runTest {
        assertEquals(
            listOf("RU: [Hello]", "RU: [Sign in]"),
            StubTranslator().translate(listOf("Hello", "Sign in")),
        )
    }
}
