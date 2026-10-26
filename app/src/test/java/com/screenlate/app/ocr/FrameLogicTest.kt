package com.screenlate.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class FrameLogicTest {

    /** Builds an RGBA_8888 buffer of `width x height` pixels, one color per row of pixels. */
    private fun buffer(width: Int, height: Int, rows: List<Int>): ByteBuffer {
        val b = ByteBuffer.allocate(width * height * 4)
        for (y in 0 until height) {
            val (r, g, bl) = Triple((rows[y] shr 16) and 0xFF, (rows[y] shr 8) and 0xFF, rows[y] and 0xFF)
            repeat(width) {
                b.put(r.toByte()).put(g.toByte()).put(bl.toByte()).put(0xFF.toByte())
            }
        }
        b.flip()
        return b
    }

    @Test
    fun `grid samples the right luma values`() {
        // 4x2: top row white (luma 255), bottom row black.
        val grid = FrameLogic.gridOf(buffer(4, 2, listOf(0xFFFFFF, 0x000000)), width = 4, height = 2, rowStride = 16)
        assertEquals(8, grid.size)
        for (i in 0 until 4) assertEquals(255, grid[i])
        for (i in 4 until 8) assertEquals(0, grid[i])
    }

    @Test
    fun `difference of the same grid is zero and of different sizes is max`() {
        val a = intArrayOf(10, 20, 30)
        assertEquals(0, FrameLogic.difference(a, intArrayOf(10, 20, 30)))
        assertEquals(5, FrameLogic.difference(a, intArrayOf(15, 25, 35)))
        assertEquals(Int.MAX_VALUE, FrameLogic.difference(a, intArrayOf(10, 20)))
    }

    @Test
    fun `a mostly black frame counts as blank, a lit one does not`() {
        assertTrue(FrameLogic.isBlank(IntArray(24 * 53) { 0 }))
        assertTrue(FrameLogic.isBlank(IntArray(24 * 53) { 3 }))
        assertFalse(FrameLogic.isBlank(intArrayOf(0, 0, 10) + IntArray(100) { 0 }))
    }
}
