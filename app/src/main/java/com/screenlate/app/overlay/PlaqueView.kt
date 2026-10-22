package com.screenlate.app.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.screenlate.app.settings.DisplayMode
import com.screenlate.app.settings.PlaqueColor
import kotlin.math.roundToInt

/** Plaque colors for the current theme and display mode. */
internal data class PlaqueStyle(val background: Int, val text: Int, val border: Int) {
    companion object {
        fun from(configuration: Configuration, style: OverlayStyle): PlaqueStyle {
            val night = when (style.color) {
                PlaqueColor.System -> configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                PlaqueColor.Light -> false
                PlaqueColor.Dark -> true
            }
            val base = if (night) DARK else LIGHT
            if (style.mode != DisplayMode.Overlay) return base
            val alpha = (style.opacity.coerceIn(0f, 1f) * 255).roundToInt() shl 24
            return base.copy(background = (base.background and 0x00FFFFFF) or alpha)
        }

        private val LIGHT = PlaqueStyle(0xFFFFFFFF.toInt(), 0xFF1B1B1F.toInt(), 0x662B5BD7)
        private val DARK = PlaqueStyle(0xFF1F1F23.toInt(), 0xFFE6E6EA.toInt(), 0x667A9CFF)
    }
}

/** One translated label drawn over (or, as a hint, under) its source text. */
internal class PlaqueView(context: Context) : TextView(context) {
    private val density = resources.displayMetrics.density
    private val spPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)
    private val shape = GradientDrawable().apply { cornerRadius = 2 * density }
    private var fitted: List<Any>? = null

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

    /**
     * Puts the plaque at [x], [y] inside the overlay. Covering modes take exactly [width] x [height]
     * and shrink the font, no larger than the original, down to the minimum before cutting the text
     * with an ellipsis. A hint takes the width and only as much height as it needs.
     */
    fun place(plaque: Plaque, x: Int, y: Int, width: Int, height: Int, style: OverlayStyle) {
        translationX = x.toFloat()
        translationY = y.toFloat()
        val hint = style.mode == DisplayMode.Hint
        val targetHeight = if (hint) ViewGroup.LayoutParams.WRAP_CONTENT else height
        val params = layoutParams
        if (params.width != width || params.height != targetHeight) {
            params.width = width
            params.height = targetHeight
            layoutParams = params
        }
        val key = listOf(style.mode, style.minTextSp, width, height, plaque.sourceLength, plaque.lineHeight)
        if (key != fitted) {
            fitted = key
            fit(plaque, width, height, style)
        }
        if (text.toString() != plaque.text) text = plaque.text
        visibility = View.VISIBLE
    }

    private fun fit(plaque: Plaque, width: Int, height: Int, style: OverlayStyle) {
        val minPx = (style.minTextSp * spPx).roundToInt().coerceAtLeast(1)
        val sourcePx = AutoFitText.sourceTextPx(width, height, plaque.sourceLength, plaque.lineHeight)
        if (style.mode == DisplayMode.Hint) {
            setAutoSizeTextTypeWithDefaults(AUTO_SIZE_TEXT_TYPE_NONE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, (sourcePx * HINT_SCALE).coerceIn(minPx.toFloat(), MAX_HINT_SP * spPx))
            maxLines = MAX_HINT_LINES
            return
        }
        val maxPx = sourcePx.roundToInt().coerceIn(minPx + 1, (MAX_TEXT_SP * spPx).roundToInt().coerceAtLeast(minPx + 1))
        maxLines = AutoFitText.linesThatFit(height, minPx.toFloat())
        setAutoSizeTextTypeUniformWithConfiguration(minPx, maxPx, 1, TypedValue.COMPLEX_UNIT_PX)
    }

    private companion object {
        const val MAX_TEXT_SP = 24f
        const val MAX_HINT_SP = 14f
        const val HINT_SCALE = 0.8f
        const val MAX_HINT_LINES = 3
    }
}
