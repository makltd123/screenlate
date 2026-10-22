package com.screenlate.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager

/**
 * Packages whose events and windows are never read: this app, the system UI and keyboards always,
 * plus the user's exclusion list and apps paused from the floating button.
 */
class PackagePolicy(private val ignored: Set<String>) {
    @Volatile
    var excluded: Set<String> = emptySet()

    @Volatile
    var paused: Set<String> = emptySet()

    fun isIgnored(packageName: String?): Boolean =
        packageName == null || packageName in ignored || packageName in excluded || packageName in paused

    /** Never read regardless of user settings. */
    fun isSystem(packageName: String?): Boolean = packageName == null || packageName in ignored

    companion object {
        private const val SYSTEM_UI = "com.android.systemui"

        fun forDevice(context: Context): PackagePolicy {
            val keyboards = runCatching {
                context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
            }.getOrDefault(emptyList())
            return PackagePolicy(setOf(context.packageName, SYSTEM_UI) + keyboards)
        }
    }
}

/** Translatable text found on screen, plus counters for the diagnostics screen. */
class ScreenText(val items: List<TextItem>, val windowCount: Int, val occluded: Int)

/**
 * Walks the visible app windows and returns English text nodes with their screen bounds.
 * Node queries are blocking binder calls, so call [collect] off the main thread.
 */
class ScreenTextCollector(
    private val service: AccessibilityService,
    private val packages: PackagePolicy,
) {
    @Volatile
    var translateEditable = false

    fun collect(): ScreenText {
        val found = ArrayList<RawText>()
        // Area already taken by windows above the one being walked; text under it is not visible.
        val covered = Region()
        var windowCount = 0
        var occluded = 0

        val windows = service.windows
        if (windows.isEmpty()) {
            val root = service.rootInActiveWindow
            if (root != null && !packages.isIgnored(root.packageName?.toString())) {
                windowCount = 1
                occluded += walk(root, root.windowId, covered, found)
            }
        } else {
            val windowBounds = Rect()
            for (window in windows.sortedByDescending { it.layer }) {
                if (isOverlay(window)) continue
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val root = window.root
                    if (root != null && !packages.isIgnored(root.packageName?.toString())) {
                        windowCount++
                        occluded += walk(root, window.id, covered, found)
                    }
                }
                window.getBoundsInScreen(windowBounds)
                covered.union(windowBounds)
            }
        }
        return ScreenText(TextItems.prepare(found, TextFilter::shouldTranslate), windowCount, occluded)
    }

    /** Depth-first walk in on-screen order. Returns how many text nodes were hidden under higher windows. */
    private fun walk(root: AccessibilityNodeInfo, windowId: Int, covered: Region, out: MutableList<RawText>): Int {
        var occluded = 0
        var visited = 0
        val bounds = Rect()
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty() && visited < MAX_NODES_PER_WINDOW) {
            val (node, depth) = stack.removeLast()
            visited++
            // Passwords are never read, and neither is anything nested inside them; input fields only on request.
            if (!node.isVisibleToUser || node.isPassword || (node.isEditable && !translateEditable)) continue

            // Web text runs are kept even when not English on their own ("1996", "), "), so a
            // paragraph glued from them stays whole; the glued text is checked afterwards.
            val inline = node.className == WEB_VIEW_CLASS
            val text = readText(node, inline)
            if (text != null) {
                node.getBoundsInScreen(bounds)
                when {
                    bounds.width() < MIN_SIDE_PX || bounds.height() < MIN_SIDE_PX -> Unit
                    intersects(covered, bounds) -> occluded++
                    else -> out += RawText(
                        windowId = windowId,
                        viewId = node.viewIdResourceName,
                        text = text,
                        bounds = ScreenRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
                        packageName = node.packageName?.toString().orEmpty(),
                        inline = inline,
                    )
                }
            }

            if (depth < MAX_DEPTH) {
                for (i in node.childCount - 1 downTo 0) {
                    node.getChild(i)?.let { stack.addLast(it to depth + 1) }
                }
            }
        }
        return occluded
    }

    private fun readText(node: AccessibilityNodeInfo, inline: Boolean): String? {
        val accept: (String) -> Boolean = { inline || TextFilter.shouldTranslate(it) }
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) return text.takeIf(accept)
        // Containers' descriptions usually repeat their children, so only leaves contribute one.
        if (node.childCount > 0) return null
        return node.contentDescription?.toString()?.takeIf { it.isNotBlank() && accept(it) }
    }

    private fun intersects(covered: Region, rect: Rect): Boolean =
        !covered.quickReject(rect) && Region(covered).op(rect, Region.Op.INTERSECT)

    private fun isOverlay(window: AccessibilityWindowInfo): Boolean =
        window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                window.type == AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY)

    private companion object {
        /** Chrome and WebView report page elements, text runs and links alike, with this class. */
        const val WEB_VIEW_CLASS = "android.view.View"
        const val MAX_NODES_PER_WINDOW = 1500
        const val MAX_DEPTH = 64
        const val MIN_SIDE_PX = 4
    }
}
