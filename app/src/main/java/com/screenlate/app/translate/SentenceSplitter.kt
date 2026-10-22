package com.screenlate.app.translate

/** Splits English text into sentences. Keep in step with split_sentences in scripts/reference_translate.py. */
object SentenceSplitter {
    private val sentenceEnd = Regex("""([.!?…]+["'”’)\]]*)(\s+)(?=["'“‘(\[]?[A-Z0-9])""")
    private val whitespace = Regex("""\s+""")
    private val abbreviations = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc", "e.g", "i.e", "inc", "ltd", "co", "no", "fig", "u.s", "u.k",
    )

    /** Sentences paired with the whitespace that follows each of them. */
    fun split(text: String): List<Pair<String, String>> {
        val parts = ArrayList<Pair<String, String>>()
        var start = 0
        for (match in sentenceEnd.findAll(text)) {
            val ending = match.groups[1]!!
            val lastWord = text.substring(start, match.range.first).split(whitespace).lastOrNull { it.isNotEmpty() }.orEmpty()
            val abbreviation = lastWord.lowercase() in abbreviations || (lastWord.length == 1 && lastWord[0].isLetter())
            if (ending.value.startsWith(".") && abbreviation) continue
            parts += text.substring(start, ending.range.last + 1) to match.groupValues[2]
            start = match.range.last + 1
        }
        parts += text.substring(start) to ""
        return parts
    }
}
