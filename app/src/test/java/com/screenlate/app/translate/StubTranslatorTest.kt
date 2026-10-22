package com.screenlate.app.translate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StubTranslatorTest {
    @Test
    fun marksTheSentence() = runTest {
        assertEquals("RU: [Sign in]", StubTranslator().translate("Sign in"))
    }
}
