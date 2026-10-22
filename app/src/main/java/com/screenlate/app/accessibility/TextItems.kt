package com.screenlate.app.accessibility

/** Axis-aligned rectangle in screen pixels; plain Kotlin so the logic around it stays JVM-testable. */
data class ScreenRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    operator fun contains(other: ScreenRect): Boolean =
        left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
}

/** A text node as found on screen, before de-duplication. */
data class RawText(
    val windowId: Int,
    val viewId: String?,
    val text: String,
    val bounds: ScreenRect,
    val packageName: String,
)

/** A text node ready for translation; [key] stays the same while the node stays on screen. */
data class TextItem(val key: String, val text: String, val bounds: ScreenRect, val packageName: String)

object TextItems {
    /** Share of a container's text its children must cover for the container to be dropped in their favour. */
    private const val AGGREGATE_COVERAGE = 0.8

    private val whitespace = Regex("""\s+""")

    /**
     * Removes duplicates and nested repeats of the same text, then assigns stable keys.
     *
     * Apps often expose a text twice: on a container and again on the child that draws it.
     * When the children cover most of the container's text, the children win because their
     * bounds are tighter; otherwise the container wins so no text is lost.
     */
    fun prepare(raw: List<RawText>): List<TextItem> {
        val unique = raw.distinctBy { Triple(it.windowId, normalize(it.text), it.bounds) }
        val normalized = unique.map { normalize(it.text) }
        val dropped = BooleanArray(unique.size)
        for (i in unique.indices) {
            val outer = unique[i]
            val inner = unique.indices.filter { j ->
                j != i &&
                    unique[j].windowId == outer.windowId &&
                    unique[j].bounds in outer.bounds &&
                    normalized[i].contains(normalized[j])
            }
            if (inner.isEmpty()) continue
            val covered = inner.sumOf { normalized[it].length }
            if (covered >= normalized[i].length * AGGREGATE_COVERAGE) {
                dropped[i] = true
            } else {
                inner.forEach { dropped[it] = true }
            }
        }

        val seen = HashMap<String, Int>()
        return unique.indices.filterNot { dropped[it] }.map { i ->
            val item = unique[i]
            val base = "${item.windowId}|${item.viewId.orEmpty()}|${normalized[i]}"
            val occurrence = (seen[base] ?: 0) + 1
            seen[base] = occurrence
            val key = if (occurrence == 1) base else "$base#$occurrence"
            TextItem(key, item.text.trim(), item.bounds, item.packageName)
        }
    }

    fun normalize(text: String): String = text.trim().replace(whitespace, " ")
}
