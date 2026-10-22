package com.screenlate.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class TextItemsTest {
    private fun raw(text: String, bounds: ScreenRect, viewId: String? = null, windowId: Int = 1) =
        RawText(windowId, viewId, text, bounds, "com.example")

    private val row = ScreenRect(0, 0, 1000, 100)
    private val left = ScreenRect(0, 0, 400, 100)
    private val right = ScreenRect(500, 0, 1000, 100)

    @Test
    fun dropsExactDuplicates() {
        val items = TextItems.prepare(listOf(raw("Hello", left), raw("Hello", left)))
        assertEquals(listOf("Hello"), items.map { it.text })
    }

    @Test
    fun containerLosesToChildrenThatCoverIt() {
        val items = TextItems.prepare(
            listOf(raw("Wi-Fi Connected", row), raw("Wi-Fi", left), raw("Connected", right)),
        )
        assertEquals(listOf("Wi-Fi", "Connected"), items.map { it.text })
    }

    @Test
    fun containerWinsWhenChildCoversLittleOfIt() {
        val items = TextItems.prepare(listOf(raw("Read the full article here", row), raw("here", right)))
        assertEquals(listOf("Read the full article here"), items.map { it.text })
    }

    @Test
    fun sameTextNestedKeepsTheTighterNode() {
        val items = TextItems.prepare(listOf(raw("Sign in", row), raw("Sign in", left)))
        assertEquals(listOf(left), items.map { it.bounds })
    }

    @Test
    fun differentWindowsDoNotSwallowEachOther() {
        val items = TextItems.prepare(listOf(raw("Cancel", row, windowId = 1), raw("Cancel", left, windowId = 2)))
        assertEquals(2, items.size)
    }

    @Test
    fun repeatedTextGetsDistinctStableKeys() {
        val raws = listOf(raw("Reply", left, viewId = "btn"), raw("Reply", right, viewId = "btn"))
        val first = TextItems.prepare(raws).map { it.key }
        val second = TextItems.prepare(raws).map { it.key }
        assertEquals(2, first.toSet().size)
        assertEquals(first, second)
    }

    @Test
    fun keyIgnoresWhitespaceButTextKeepsLineBreaks() {
        val item = TextItems.prepare(listOf(raw("  Line one\nLine two ", row))).single()
        assertEquals("1||Line one Line two", item.key)
        assertEquals("Line one\nLine two", item.text)
    }
}
