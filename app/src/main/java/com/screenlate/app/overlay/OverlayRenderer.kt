package com.screenlate.app.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.screenlate.app.diagnostics.Diagnostics

/**
 * Owns the single full-screen overlay window and keeps its plaques in sync with the screen.
 * The window never takes touches or focus. Main thread only.
 */
class OverlayRenderer(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private val shown = HashMap<String, Plaque>()
    private val views = HashMap<String, PlaqueView>()
    private val spare = ArrayList<PlaqueView>()
    private var style = PlaqueStyle.from(context.resources.configuration)
    private val origin = IntArray(2)

    /** Updates only the plaques that appeared, moved, changed text or disappeared, so nothing flickers. */
    fun render(plaques: List<Plaque>) {
        val container = attach() ?: return
        val diff = PlaqueDiff.between(shown, plaques)
        for (key in diff.removed) {
            shown.remove(key)
            views.remove(key)?.let(::recycle)
        }
        container.getLocationOnScreen(origin)
        for (plaque in diff.added) {
            val view = obtain(container)
            views[plaque.key] = view
            shown[plaque.key] = plaque
            place(container, view, plaque)
        }
        for (plaque in diff.changed) {
            shown[plaque.key] = plaque
            views[plaque.key]?.let { place(container, it, plaque) }
        }
    }

    fun hide() {
        root?.visibility = View.INVISIBLE
    }

    fun show() {
        root?.visibility = View.VISIBLE
    }

    /** Removes every plaque together with the overlay window. */
    fun clear() {
        shown.clear()
        views.clear()
        spare.clear()
        root?.let { runCatching { windowManager.removeViewImmediate(it) } }
        root = null
    }

    fun onConfigurationChanged(configuration: Configuration) {
        style = PlaqueStyle.from(configuration)
        val container = root ?: return
        for (i in 0 until container.childCount) {
            (container.getChildAt(i) as PlaqueView).applyStyle(style)
        }
    }

    /** Converts screen bounds to overlay coordinates, clipped to the overlay. */
    private fun place(container: FrameLayout, view: PlaqueView, plaque: Plaque) {
        var left = plaque.bounds.left - origin[0]
        var top = plaque.bounds.top - origin[1]
        var right = plaque.bounds.right - origin[0]
        var bottom = plaque.bounds.bottom - origin[1]
        if (container.width > 0 && container.height > 0) {
            left = left.coerceAtLeast(0)
            top = top.coerceAtLeast(0)
            right = right.coerceAtMost(container.width)
            bottom = bottom.coerceAtMost(container.height)
        }
        if (right - left < 1 || bottom - top < 1) {
            view.visibility = View.GONE
            return
        }
        view.place(plaque.text, left, top, right - left, bottom - top)
    }

    private fun repositionAll(container: FrameLayout) {
        container.getLocationOnScreen(origin)
        for ((key, view) in views) {
            shown[key]?.let { place(container, view, it) }
        }
    }

    private fun obtain(container: FrameLayout): PlaqueView =
        spare.removeLastOrNull() ?: PlaqueView(context).also {
            it.applyStyle(style)
            container.addView(it, FrameLayout.LayoutParams(0, 0))
        }

    private fun recycle(view: PlaqueView) {
        if (spare.size < MAX_SPARE_VIEWS) {
            view.visibility = View.GONE
            spare += view
        } else {
            root?.removeView(view)
        }
    }

    private fun attach(): FrameLayout? {
        root?.let { return it }
        val container = FrameLayout(context).apply {
            visibility = View.INVISIBLE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        // The first render happens before layout; rotation changes the size later. Both need a re-place.
        container.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) repositionAll(container)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "ScreenLate"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }
        }
        return try {
            windowManager.addView(container, params)
            root = container
            container
        } catch (e: RuntimeException) {
            Diagnostics.onError(e)
            null
        }
    }

    private companion object {
        const val MAX_SPARE_VIEWS = 40
    }
}
