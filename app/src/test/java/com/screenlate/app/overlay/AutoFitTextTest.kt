package com.screenlate.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFitTextTest {
    @Test
    fun knownLineHeightGivesTheFontSize() {
        assertEquals(20f, AutoFitText.sourceTextPx(width = 1000, height = 100, chars = 50, lineHeight = 24), 0.01f)
    }

    @Test
    fun shortLabelInAWideBoxIsLimitedByItsHeight() {
        assertEquals(40f, AutoFitText.sourceTextPx(width = 300, height = 48, chars = 6, lineHeight = 0), 0.01f)
    }

    @Test
    fun paragraphSizeFollowsFromItsArea() {
        val size = AutoFitText.sourceTextPx(width = 1000, height = 120, chars = 300, lineHeight = 0)
        assertTrue("got $size", size in 25f..27f)
    }

    @Test
    fun countsLinesThatFit() {
        assertEquals(5, AutoFitText.linesThatFit(height = 120, textPx = 20f))
        assertEquals(1, AutoFitText.linesThatFit(height = 10, textPx = 20f))
    }
}
