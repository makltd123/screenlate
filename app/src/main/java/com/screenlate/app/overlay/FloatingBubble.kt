package com.screenlate.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.screenlate.app.R
import com.screenlate.app.diagnostics.Diagnostics
import com.screenlate.app.settings.BubblePosition
import kotlin.math.hypot
import kotlin.math.roundToInt

/** One entry of the floating button's menu. */
class BubbleAction(val label: String, val enabled: Boolean = true, val run: () -> Unit)

/**
 * The floating button: dragged anywhere, it sticks to the nearest side edge; tap and long press
 * call back into the service. It lives in its own small window, so the plaques' window stays
 * touch-free; touches outside the button and its menu go to the app underneath. Main thread only.
 */
class FloatingBubble(
    private val context: Context,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onMoved: (BubblePosition) -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val metrics = context.resources.displayMetrics
    private val size = dp(48)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private val longPress = Runnable {
        longPressed = true
        onLongPress()
    }

    private var button: ImageView? = null
    private var popup: View? = null
    private var position = BubblePosition()
    private var active = true
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var longPressed = false

    private val params = WindowManager.LayoutParams(
        size,
        size,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "ScreenLate button"
    }

    fun show(newPosition: BubblePosition) {
        position = newPosition
        val view = button
        if (view != null) {
            applyPosition(view)
            return
        }
        val created = createButton()
        positionParams()
        try {
            windowManager.addView(created, params)
            button = created
        } catch (e: RuntimeException) {
            Diagnostics.onError(e)
        }
    }

    fun hide() {
        dismissPopup()
        handler.removeCallbacks(longPress)
        button?.let { runCatching { windowManager.removeViewImmediate(it) } }
        button = null
    }

    /** Full colour while translation is on, grey while it is off. */
    fun setActive(value: Boolean) {
        active = value
        button?.let(::paint)
    }

    fun showMenu(actions: List<BubbleAction>) {
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        for (action in actions) {
            list.addView(
                TextView(context).apply {
                    text = action.label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(panelTextColor())
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    alpha = if (action.enabled) 1f else 0.4f
                    if (action.enabled) {
                        setOnClickListener {
                            dismissPopup()
                            action.run()
                        }
                    }
                },
            )
        }
        showPopup(list, maxHeight = metrics.heightPixels * 7 / 10)
    }

    /** A scrollable panel with every translation on screen, for plaques cut short by an ellipsis. */
    fun showText(title: String, paragraphs: List<String>) {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        column.addView(
            TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(panelTextColor())
                setPadding(0, 0, 0, dp(8))
            },
        )
        for (paragraph in paragraphs) {
            column.addView(
                TextView(context).apply {
                    text = paragraph
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(panelTextColor())
                    setPadding(0, dp(4), 0, dp(4))
                    setOnClickListener { dismissPopup() }
                },
            )
        }
        showPopup(ScrollView(context).apply { addView(column) }, maxHeight = metrics.heightPixels * 6 / 10)
    }

    fun dismissPopup() {
        popup?.let { runCatching { windowManager.removeViewImmediate(it) } }
        popup = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showPopup(content: View, maxHeight: Int) {
        dismissPopup()
        val width = minOf(metrics.widthPixels - dp(32), dp(340))
        val frame = PanelFrame(content, maxHeight).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(panelBackground())
            }
            // Touching anywhere else closes the panel; the touch still reaches the app below.
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    dismissPopup()
                    true
                } else {
                    false
                }
            }
        }
        val anchorY = (button?.let { params.y } ?: (metrics.heightPixels / 2))
        val popupParams = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or (if (position.onRight) Gravity.END else Gravity.START)
            x = size + dp(8)
            y = (anchorY - dp(24)).coerceIn(dp(24), (metrics.heightPixels - maxHeight - dp(24)).coerceAtLeast(dp(24)))
            title = "ScreenLate menu"
        }
        try {
            windowManager.addView(frame, popupParams)
            popup = frame
        } catch (e: RuntimeException) {
            Diagnostics.onError(e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createButton() = ImageView(context).apply {
        setImageResource(R.drawable.ic_tile)
        val padding = dp(12)
        setPadding(padding, padding, padding, padding)
        contentDescription = context.getString(R.string.app_name)
        paint(this)
        setOnTouchListener { view, event -> onTouch(view, event) }
    }

    private fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startX = params.x
                startY = params.y
                dragging = false
                longPressed = false
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && hypot(dx, dy) > touchSlop && !longPressed) {
                    dragging = true
                    handler.removeCallbacks(longPress)
                    dismissPopup()
                }
                if (dragging) {
                    params.x = startX + dx.roundToInt()
                    params.y = startY + dy.roundToInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                when {
                    dragging -> snap(view)
                    !longPressed -> onTap()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                if (dragging) snap(view)
            }
        }
        return true
    }

    /** Sticks to the nearer side edge and remembers the spot. */
    private fun snap(view: View) {
        val onRight = params.x + size / 2 > metrics.widthPixels / 2
        val travel = (metrics.heightPixels - size).coerceAtLeast(1)
        position = BubblePosition(onRight, (params.y.toFloat() / travel).coerceIn(0f, 1f))
        applyPosition(view)
        onMoved(position)
    }

    private fun applyPosition(view: View) {
        positionParams()
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun positionParams() {
        params.x = if (position.onRight) metrics.widthPixels - size else 0
        params.y = (position.yFraction * (metrics.heightPixels - size)).roundToInt()
    }

    private fun paint(view: ImageView) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) ACTIVE_COLOR else INACTIVE_COLOR)
        }
    }

    private fun night() =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun panelBackground() = if (night()) 0xF22B2B30.toInt() else 0xF7FFFFFF.toInt()

    private fun panelTextColor() = if (night()) 0xFFE6E6EA.toInt() else 0xFF1B1B1F.toInt()

    private fun dp(value: Int) = (value * metrics.density).roundToInt()

    /** Wraps panel content and caps its height so long menus and texts scroll instead of overflowing. */
    private class PanelFrame(content: View, private val maxHeight: Int) : LinearLayout(content.context) {
        init {
            orientation = VERTICAL
            addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
        }
    }

    private companion object {
        const val ACTIVE_COLOR = 0xE62B5BD7.toInt()
        const val INACTIVE_COLOR = 0xB39E9E9E.toInt()
    }
}
