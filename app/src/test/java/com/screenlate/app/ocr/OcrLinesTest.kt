package com.screenlate.app.ocr

import com.screenlate.app.accessibility.ScreenRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrLinesTest {
    private val translatable: (String) -> Boolean = { it in setOf("Hello world", "Second line", "Wrap text") }

    @Test
    fun `keeps english lines with bounds, sorts into reading order, drops the rest`() {
        val lines = OcrLines.prepare(
            listOf(
                "Привет" to ScreenRect(0, 0, 100, 20),
                "Second line" to ScreenRect(10, 100, 200, 130),
                "Hello world" to ScreenRect(10, 50, 200, 80),
                "   " to ScreenRect(0, 0, 50, 20),
                "Wrap text" to ScreenRect(300, 50, 400, 80),
                "123" to ScreenRect(0, 200, 100, 220),
            ),
            translatable,
        )
        assertEquals(listOf("Hello world", "Wrap text", "Second line"), lines.map { it.text })
        assertEquals("ocr|10|50|200|80", lines[0].key)
        assertEquals(30, lines[0].lineHeight)
    }

    @Test
    fun `the same text at the same place is taken once`() {
        val lines = OcrLines.prepare(
            listOf(
                "Hello world" to ScreenRect(10, 50, 200, 80),
                "Hello world" to ScreenRect(10, 50, 200, 80),
            ),
            translatable,
        )
        assertEquals(1, lines.size)
    }

    @Test
    fun `empty or zero-sized lines are dropped even when translatable`() {
        val lines = OcrLines.prepare(
            listOf(
                "Hello world" to ScreenRect(10, 50, 10, 80),
                "Hello world" to ScreenRect(10, 50, 200, 50),
            ),
            { true },
        )
        assertTrue(lines.isEmpty())
    }
}
