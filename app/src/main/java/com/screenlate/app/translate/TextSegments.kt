package com.screenlate.app.translate

import com.screenlate.app.accessibility.TextFilter
import java.util.Locale

/**
 * Splits on-screen text into what the model translates (English sentences) and what is kept
 * as is (line breaks, list markers, spaces, non-English pieces), and puts translations back.
 */
object TextSegments {
    sealed interface Part {
        val text: String

        data class Kept(override val text: String) : Part

        data class Sentence(override val text: String) : Part
    }

    private val listMarker = Regex("""^\s*(?:[•·▪◦‣*\-–—]|\d{1,3}[.)])\s+""")
    private val russian = Locale.forLanguageTag("ru")

    fun split(text: String): List<Part> {
        val parts = ArrayList<Part>()
        text.split('\n').forEachIndexed { index, line ->
            if (index > 0) parts += Part.Kept("\n")
            val marker = listMarker.find(line)?.value.orEmpty()
            if (marker.isNotEmpty()) parts += Part.Kept(marker)
            for ((sentence, gap) in SentenceSplitter.split(line.substring(marker.length))) {
                addSentence(sentence, parts)
                if (gap.isNotEmpty()) parts += Part.Kept(gap)
            }
        }
        return parts
    }

    fun sentences(text: String): List<String> = split(text).filterIsInstance<Part.Sentence>().map { it.text }

    /** The translated text, or null while any of its sentences has no translation yet. */
    fun assemble(text: String, translationOf: (String) -> String?): String? {
        val out = StringBuilder()
        for (part in split(text)) {
            when (part) {
                is Part.Kept -> out.append(part.text)
                is Part.Sentence -> out.append(matchCase(part.text, translationOf(part.text) ?: return null))
            }
        }
        return out.toString()
    }

    /** All-caps English (buttons, headings) stays all-caps in Russian. */
    fun matchCase(source: String, translation: String): String {
        val letters = source.filter { it.isLetter() }
        return if (letters.length >= 2 && letters.all { it.isUpperCase() }) translation.uppercase(russian) else translation
    }

    private fun addSentence(raw: String, parts: MutableList<Part>) {
        val core = raw.trim { it.isWhitespace() }
        if (core.isEmpty()) {
            if (raw.isNotEmpty()) parts += Part.Kept(raw)
            return
        }
        val leading = raw.takeWhile { it.isWhitespace() }
        val trailing = raw.takeLastWhile { it.isWhitespace() }
        if (leading.isNotEmpty()) parts += Part.Kept(leading)
        parts += if (TextFilter.isEnglish(core)) Part.Sentence(core) else Part.Kept(core)
        if (trailing.isNotEmpty()) parts += Part.Kept(trailing)
    }
}
