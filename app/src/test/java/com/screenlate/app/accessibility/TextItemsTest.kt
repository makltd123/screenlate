package com.screenlate.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class TextItemsTest {
    private fun raw(text: String, bounds: ScreenRect, viewId: String? = null, windowId: Int = 1, inline: Boolean = false) =
        RawText(windowId, viewId, text, bounds, "com.example", inline)

    private fun web(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        raw(text, ScreenRect(left, top, right, bottom), inline = true)

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

    @Test
    fun gluesWebRunsSplitByALinkIntoOneParagraph() {
        val items = TextItems.prepare(
            listOf(
                web("The first work was done with formaldehyde (see", 0, 0, 1000, 60),
                web("Bair and Haase, 1996", 600, 40, 800, 60),
                web("). It was found that the rate changed.", 0, 40, 1000, 100),
            ),
        )
        val item = items.single()
        assertEquals("The first work was done with formaldehyde (see Bair and Haase, 1996). It was found that the rate changed.", item.text)
        assertEquals(ScreenRect(0, 0, 1000, 100), item.bounds)
        assertEquals(20, item.lineHeight)
    }

    @Test
    fun keepsColumnsAndStackedBlocksApart() {
        val columns = TextItems.prepare(listOf(web("Left column text", 0, 0, 400, 60), web("Right column text", 600, 0, 1000, 60)))
        assertEquals(2, columns.size)
        val stacked = TextItems.prepare(listOf(web("First paragraph.", 0, 0, 1000, 20), web("Second paragraph.", 0, 30, 1000, 50)))
        assertEquals(2, stacked.size)
    }

    @Test
    fun neverGluesNativeViews() {
        val items = TextItems.prepare(listOf(raw("Wi-Fi", ScreenRect(0, 0, 100, 20)), raw("Connected", ScreenRect(105, 0, 300, 20))))
        assertEquals(2, items.size)
    }

    @Test
    fun nonEnglishRunsJoinTheirParagraphButNotAlone() {
        val glued = TextItems.prepare(listOf(web("Published in", 0, 0, 200, 20), web("1996", 205, 0, 245, 20)), TextFilter::shouldTranslate)
        assertEquals(listOf("Published in 1996"), glued.map { it.text })
        val alone = TextItems.prepare(listOf(web("1996", 0, 0, 40, 20)), TextFilter::shouldTranslate)
        assertEquals(emptyList<TextItem>(), alone)
    }
}
