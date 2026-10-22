package com.screenlate.app.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFilterTest {
    @Test
    fun acceptsEnglish() {
        assertTrue(TextFilter.shouldTranslate("Hello world"))
        assertTrue(TextFilter.shouldTranslate("OK"))
        assertTrue(TextFilter.shouldTranslate("iPhone 15 Pro"))
        assertTrue(TextFilter.shouldTranslate("Café au lait"))
        assertTrue(TextFilter.shouldTranslate("  Settings  "))
    }

    @Test
    fun rejectsNonEnglish() {
        assertFalse(TextFilter.shouldTranslate("Привет, мир"))
        assertFalse(TextFilter.shouldTranslate("Hello Привет мир"))
        assertFalse(TextFilter.shouldTranslate("こんにちは"))
    }

    @Test
    fun rejectsShortAndLetterless() {
        assertFalse(TextFilter.shouldTranslate("a"))
        assertFalse(TextFilter.shouldTranslate("+"))
        assertFalse(TextFilter.shouldTranslate("42"))
        assertFalse(TextFilter.shouldTranslate("3,141.59 %"))
        assertFalse(TextFilter.shouldTranslate("   "))
    }

    @Test
    fun rejectsUrlsEmailsAndTimes() {
        assertFalse(TextFilter.shouldTranslate("https://example.com/path?q=1"))
        assertFalse(TextFilter.shouldTranslate("www.example.com"))
        assertFalse(TextFilter.shouldTranslate("example.com"))
        assertFalse(TextFilter.shouldTranslate("user.name+tag@mail.example.org"))
        assertFalse(TextFilter.shouldTranslate("10:30"))
        assertFalse(TextFilter.shouldTranslate("9:05 PM"))
        assertFalse(TextFilter.shouldTranslate("23:59:59"))
    }

    @Test
    fun keepsSentencesThatMentionDomains() {
        assertTrue(TextFilter.shouldTranslate("Visit example.com for details"))
        assertTrue(TextFilter.shouldTranslate("Mr.Smith"))
    }
}
