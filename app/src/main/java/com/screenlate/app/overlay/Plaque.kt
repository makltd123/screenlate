package com.screenlate.app.overlay

import com.screenlate.app.accessibility.ScreenRect
import com.screenlate.app.settings.DisplayMode
import com.screenlate.app.settings.PlaqueColor

/**
 * A translated label to draw over [bounds]; [key] identifies the source node between refreshes.
 * [sourceLength] and [lineHeight] describe the original text, to size the font like it.
 */
data class Plaque(
    val key: String,
    val text: String,
    val bounds: ScreenRect,
    val sourceLength: Int = text.length,
    val lineHeight: Int = 0,
)

/** User-chosen look of the plaques. */
data class OverlayStyle(
    val mode: DisplayMode = DisplayMode.Replace,
    val color: PlaqueColor = PlaqueColor.System,
    val opacity: Float = 0.85f,
    val minTextSp: Int = 8,
)

/** What the overlay has to change to go from the plaques on screen to the next set. */
data class PlaqueDiff(val added: List<Plaque>, val changed: List<Plaque>, val removed: List<String>) {
    companion object {
        fun between(current: Map<String, Plaque>, next: List<Plaque>): PlaqueDiff {
            val nextKeys = next.mapTo(HashSet()) { it.key }
            return PlaqueDiff(
                added = next.filter { it.key !in current },
                changed = next.filter { plaque -> current[plaque.key].let { it != null && it != plaque } },
                removed = current.keys.filter { it !in nextKeys },
            )
        }
    }
}
