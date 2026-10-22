package com.screenlate.app.accessibility

/** Decides whether a piece of on-screen text is English worth translating. */
object TextFilter {
    private const val MIN_LATIN_LETTERS = 2
    private const val MIN_LATIN_SHARE = 0.6

    private val url = Regex("""^(https?://|www\.)\S+$|^[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}(/\S*)?$""")
    private val email = Regex("""^[\w.+-]+@[\w-]+(\.[\w-]+)+$""")
    private val time = Regex("""^\d{1,2}[:.]\d{2}([:.]\d{2})?\s*([ap]\.?m\.?)?$""", RegexOption.IGNORE_CASE)

    fun shouldTranslate(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 2) return false
        if (url.matches(trimmed) || email.matches(trimmed) || time.matches(trimmed)) return false
        return isEnglish(trimmed)
    }

    /** More than 60% of the letters are Latin, and there are at least two of them. */
    fun isEnglish(text: String): Boolean {
        var letters = 0
        var latin = 0
        for (ch in text) {
            if (!ch.isLetter()) continue
            letters++
            if (Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.LATIN) latin++
        }
        return latin >= MIN_LATIN_LETTERS && latin > letters * MIN_LATIN_SHARE
    }
}
