package com.screenlate.app.ocr

import com.screenlate.app.accessibility.ScreenRect

/** One line of text found by OCR, with its place on screen and its line height for font sizing. */
data class OcrLine(val key: String, val text: String, val bounds: ScreenRect, val lineHeight: Int)

object OcrLines {
    /** Keeps English lines only, drops repeats, sorts into reading order and gives stable keys. */
    fun prepare(raw: List<Pair<String, ScreenRect>>, isTranslatable: (String) -> Boolean): List<OcrLine> {
        val seen = HashSet<Int>()
        return raw.asSequence()
            .filter { (text, bounds) -> text.isNotBlank() && bounds.width > 0 && bounds.height > 0 && isTranslatable(text) }
            .filter { (text, bounds) -> seen.add(text.trim().hashCode() * 31 + bounds.hashCode()) }
            .sortedWith(compareBy({ it.second.top }, { it.second.left }, { it.second.right }))
            .map { (text, bounds) ->
                OcrLine(
                    key = "ocr|${bounds.left}|${bounds.top}|${bounds.right}|${bounds.bottom}",
                    text = text.trim(),
                    bounds = bounds,
                    lineHeight = bounds.height,
                )
            }
            .toList()
    }
}
