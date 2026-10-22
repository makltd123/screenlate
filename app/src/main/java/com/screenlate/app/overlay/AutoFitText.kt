package com.screenlate.app.overlay

import kotlin.math.min
import kotlin.math.sqrt

/**
 * Font size guesses for plaques. The accessibility tree gives only a node's rectangle, so the
 * size of the original text is estimated from it; the translation is then fitted no larger than that.
 */
object AutoFitText {
    /** Average character width as a share of the font size, for Latin text. */
    private const val CHAR_WIDTH = 0.5f

    /** Line height as a multiple of the font size. */
    const val LINE_SPACING = 1.2f

    /**
     * Rough font size of the original text in pixels. With a known [lineHeight] it follows from
     * that; otherwise from how many characters fill the box, but never taller than the box itself.
     */
    fun sourceTextPx(width: Int, height: Int, chars: Int, lineHeight: Int): Float {
        if (lineHeight > 0) return lineHeight / LINE_SPACING
        val byArea = sqrt(width.toFloat() * height / (CHAR_WIDTH * LINE_SPACING * chars.coerceAtLeast(1)))
        return min(byArea, height / LINE_SPACING)
    }

    /** How many lines of [textPx] text fit into [height]; at least one. */
    fun linesThatFit(height: Int, textPx: Float): Int = (height / (textPx * LINE_SPACING)).toInt().coerceAtLeast(1)
}
