package com.screenlate.app.accessibility

/** Axis-aligned rectangle in screen pixels; plain Kotlin so the logic around it stays JVM-testable. */
data class ScreenRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    operator fun contains(other: ScreenRect): Boolean =
        left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom

    fun union(other: ScreenRect) = ScreenRect(
        minOf(left, other.left),
        minOf(top, other.top),
        maxOf(right, other.right),
        maxOf(bottom, other.bottom),
    )
}

/**
 * A text node as found on screen, before de-duplication. [inline] marks web text runs, which
 * flow into each other and may be glued into one paragraph.
 */
data class RawText(
    val windowId: Int,
    val viewId: String?,
    val text: String,
    val bounds: ScreenRect,
    val packageName: String,
    val inline: Boolean = false,
)

/**
 * A text block ready for translation; [key] stays the same while the block stays on screen.
 * [lineHeight] is the height of one source line when known (glued paragraphs), otherwise 0.
 */
data class TextItem(
    val key: String,
    val text: String,
    val bounds: ScreenRect,
    val packageName: String,
    val lineHeight: Int = 0,
)

object TextItems {
    /** Share of a container's text its children must cover for the container to be dropped in their favour. */
    private const val AGGREGATE_COVERAGE = 0.8

    /** Pixels of slack when comparing edges of neighbouring text runs. */
    private const val EDGE_SLACK = 2

    private val whitespace = Regex("""\s+""")
    private val noSpaceBefore = Regex("""^[\s.,;:!?)\]}»”’…%]""")
    private val noSpaceAfter = Regex("""[\s(\[{«“‘/-]$""")

    /**
     * Removes duplicates and nested repeats of the same text, glues inline runs into paragraphs,
     * drops what is not English, then assigns stable keys.
     *
     * Apps often expose a text twice: on a container and again on the child that draws it.
     * When the children cover most of the container's text, the children win because their
     * bounds are tighter; otherwise the container wins so no text is lost.
     */
    fun prepare(raw: List<RawText>, isTranslatable: (String) -> Boolean = { true }): List<TextItem> {
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

        val blocks = glueInlineRuns(unique.filterIndexed { i, _ -> !dropped[i] })
        val seen = HashMap<String, Int>()
        return blocks.filter { isTranslatable(it.text) }.map { block ->
            val base = "${block.windowId}|${block.viewId.orEmpty()}|${normalize(block.text)}"
            val occurrence = (seen[base] ?: 0) + 1
            seen[base] = occurrence
            val key = if (occurrence == 1) base else "$base#$occurrence"
            TextItem(key, block.text.trim(), block.bounds, block.packageName, block.lineHeight)
        }
    }

    fun normalize(text: String): String = text.trim().replace(whitespace, " ")

    private class Block(
        val windowId: Int,
        val viewId: String?,
        val packageName: String,
        var text: String,
        var bounds: ScreenRect,
        var last: RawText,
        var lineHeight: Int,
        var pieces: Int,
    )

    /**
     * Web pages split a paragraph into runs at every link or styled word, each with its own
     * rectangle. Runs that continue on the line where the previous one ends are glued back into
     * one paragraph, so it is translated whole and drawn as one plaque instead of overlapping ones.
     */
    private fun glueInlineRuns(items: List<RawText>): List<Block> {
        val blocks = ArrayList<Block>()
        for (item in items) {
            val current = blocks.lastOrNull()
            // The shortest run seen so far is the best guess for one line's height.
            val lineHeight = minOf(current?.lineHeight ?: Int.MAX_VALUE, item.bounds.height)
            if (current != null && current.last.inline && item.inline && continues(current.last, item, lineHeight)) {
                current.text = join(current.text, item.text)
                current.bounds = current.bounds.union(item.bounds)
                current.lineHeight = minOf(current.lineHeight, item.bounds.height)
                current.last = item
                current.pieces++
            } else {
                blocks += Block(item.windowId, item.viewId, item.packageName, item.text, item.bounds, item, item.bounds.height, 1)
            }
        }
        // A single run's height may span several lines, so it says nothing about the line height.
        blocks.filter { it.pieces == 1 }.forEach { it.lineHeight = 0 }
        return blocks
    }

    private fun continues(previous: RawText, next: RawText, lineHeight: Int): Boolean {
        if (previous.windowId != next.windowId) return false
        val a = previous.bounds
        val b = next.bounds
        // The next run must start on a line the previous one occupies.
        if (b.top >= a.bottom - EDGE_SLACK || b.bottom <= a.top) return false
        val previousIsOneLine = a.height <= lineHeight * 1.5
        val nextWraps = b.height > lineHeight * 1.5
        return if (previousIsOneLine) {
            // Starts right after it on the same line, or wraps back to the paragraph's left edge.
            (b.left >= a.right - EDGE_SLACK && b.left <= a.right + lineHeight) || (nextWraps && b.left <= a.left + EDGE_SLACK)
        } else {
            // A multi-line run spans the paragraph width; the next one starts inside it.
            b.left >= a.left - EDGE_SLACK && b.left <= a.right + EDGE_SLACK
        }
    }

    private fun join(left: String, right: String): String =
        if (noSpaceAfter.containsMatchIn(left) || noSpaceBefore.containsMatchIn(right)) left + right else "$left $right"
}
