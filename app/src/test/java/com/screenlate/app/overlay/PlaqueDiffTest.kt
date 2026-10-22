package com.screenlate.app.overlay

import com.screenlate.app.accessibility.ScreenRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaqueDiffTest {
    private val a = Plaque("a", "RU: [A]", ScreenRect(0, 0, 100, 40))
    private val b = Plaque("b", "RU: [B]", ScreenRect(0, 50, 100, 90))

    @Test
    fun sameScreenChangesNothing() {
        val diff = PlaqueDiff.between(mapOf("a" to a, "b" to b), listOf(a, b))
        assertTrue(diff.added.isEmpty() && diff.changed.isEmpty() && diff.removed.isEmpty())
    }

    @Test
    fun detectsAddedMovedAndRemoved() {
        val movedB = b.copy(bounds = ScreenRect(0, 60, 100, 100))
        val c = Plaque("c", "RU: [C]", ScreenRect(0, 120, 100, 160))
        val diff = PlaqueDiff.between(mapOf("a" to a, "b" to b), listOf(movedB, c))
        assertEquals(listOf(c), diff.added)
        assertEquals(listOf(movedB), diff.changed)
        assertEquals(listOf("a"), diff.removed)
    }

    @Test
    fun changedTextIsAnUpdate() {
        val renamed = a.copy(text = "RU: [A2]")
        val diff = PlaqueDiff.between(mapOf("a" to a), listOf(renamed))
        assertEquals(listOf(renamed), diff.changed)
    }
}
