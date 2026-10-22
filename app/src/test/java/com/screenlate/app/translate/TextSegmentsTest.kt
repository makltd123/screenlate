package com.screenlate.app.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextSegmentsTest {
    private fun sentences(text: String) = SentenceSplitter.split(text).map { it.first }

    @Test
    fun splitsSentencesLikeTheReferenceScript() {
        assertEquals(listOf("Something went wrong.", "Please try again."), sentences("Something went wrong. Please try again."))
        assertEquals(listOf("Really?", "\"Yes!\"", "Done…"), sentences("Really? \"Yes!\" Done…"))
        assertEquals(listOf("Version 2.0 is out."), sentences("Version 2.0 is out."))
    }

    @Test
    fun doesNotSplitAfterAbbreviationsOrInitials() {
        assertEquals(
            listOf("Mr. Smith went to Washington.", "U.S.A. and U.K. are here."),
            sentences("Mr. Smith went to Washington. U.S.A. and U.K. are here."),
        )
        assertEquals(listOf("J. K. Rowling wrote it."), sentences("J. K. Rowling wrote it."))
    }

    @Test
    fun keepsLineBreaksListMarkersAndNonEnglish() {
        val text = "Settings:\n• Turn on Wi-Fi. Then restart.\n2) Привет\n"
        assertEquals(listOf("Settings:", "Turn on Wi-Fi.", "Then restart."), TextSegments.sentences(text))
        val translated = TextSegments.assemble(text) { "<$it>" }
        assertEquals("<Settings:>\n• <Turn on Wi-Fi.> <Then restart.>\n2) Привет\n", translated)
    }

    @Test
    fun waitsForEverySentence() {
        val text = "First one. Second one."
        assertNull(TextSegments.assemble(text) { if (it == "First one.") "Первый." else null })
    }

    @Test
    fun allCapsSourceGivesAllCapsTranslation() {
        assertEquals("ОТМЕНА", TextSegments.assemble("CANCEL") { "Отмена" })
        assertEquals("Отмена", TextSegments.assemble("Cancel") { "Отмена" })
    }
}
