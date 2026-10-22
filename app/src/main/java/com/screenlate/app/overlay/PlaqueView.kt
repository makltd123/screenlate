package com.screenlate.app.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import kotlin.math.roundToInt

/** Plaque colors; they follow the system light or dark theme. */
internal data class PlaqueStyle(val background: Int, val text: Int, val border: Int) {
    companion object {
        private val LIGHT = PlaqueStyle(0xFFFFFFFF.toInt(), 0xFF1B1B1F.toInt(), 0x662B5BD7)
        private val DARK = PlaqueStyle(0xFF1F1F23.toInt(), 0xFFE6E6EA.toInt(), 0x667A9CFF)

        fun from(configuration: Configuration): PlaqueStyle {
            val night = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return if (night) DARK else LIGHT
        }
    }
}

/** One translated label drawn over the bounds of its source node. */
internal class PlaqueView(context: Context) : TextView(context) {
    private val density = resources.displayMetrics.density
    private val spPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)
    private val shape = GradientDrawable().apply { cornerRadius = 2 * density }
    private var fittedHeight = -1

    init {
        background = shape
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        ellipsize = TextUtils.TruncateAt.END
        val padding = (2 * density).roundToInt()
        setPadding(padding, 0, padding, 0)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun applyStyle(style: PlaqueStyle) {
        shape.setColor(style.background)
        shape.setStroke(1, style.border)
        setTextColor(style.text)
    }

    /** Moves the plaque to [x], [y] inside the overlay and shrinks the font so [label] fits [width] x [height]. */
    fun place(label: String, x: Int, y: Int, width: Int, height: Int) {
        translationX = x.toFloat()
        translationY = y.toFloat()
        val params = layoutParams
        if (params.width != width || params.height != height) {
            params.width = width
            params.height = height
            layoutParams = params
        }
        if (height != fittedHeight) fitTo(height)
        if (text.toString() != label) text = label
        visibility = View.VISIBLE
    }

    private fun fitTo(height: Int) {
        fittedHeight = height
        val minPx = (MIN_TEXT_SP * spPx).roundToInt()
        val maxPx = (height * SINGLE_LINE_SHARE).roundToInt().coerceIn(minPx + 1, (MAX_TEXT_SP * spPx).roundToInt())
        maxLines = (height / (minPx * LINE_HEIGHT)).toInt().coerceAtLeast(1)
        setAutoSizeTextTypeUniformWithConfiguration(minPx, maxPx, 1, TypedValue.COMPLEX_UNIT_PX)
    }

    private companion object {
        const val MIN_TEXT_SP = 8f
        const val MAX_TEXT_SP = 22f
        const val SINGLE_LINE_SHARE = 0.75f
        const val LINE_HEIGHT = 1.2f
    }
}
