package com.screenlate.app.ocr

import java.nio.ByteBuffer

/**
 * Pure helpers behind the OCR capture: coarse brightness grids of frames, used to see when
 * the screen has stopped changing and to recognise all-black captures of protected screens.
 * Works on plain buffers, so it stays JVM-testable.
 */
object FrameLogic {
    /** Columns of the comparison grid; the row count follows the frame's aspect ratio. */
    const val GRID_COLS = 24

    /** A mean cell difference below this counts as the same picture. */
    const val STABLE_DIFFERENCE = 4

    /** A grid cell must stay under this luma for the frame to count as blank (FLAG_SECURE). */
    private const val BLANK_LUMA = 3

    /** Samples an RGBA_8888 frame into a grid of luma values 0..255, one byte per pixel read. */
    fun gridOf(rgba: ByteBuffer, width: Int, height: Int, rowStride: Int): IntArray {
        val cols = GRID_COLS.coerceAtMost(width)
        val rows = (cols.toLong() * height / width).toInt().coerceAtLeast(1)
        val grid = IntArray(cols * rows)
        var index = 0
        for (row in 0 until rows) {
            // Cell centres, in whole-number pixel arithmetic.
            val y = (row * 2 + 1) * height / (rows * 2)
            val rowStart = y * rowStride
            for (col in 0 until cols) {
                val x = (col * 2 + 1) * width / (cols * 2)
                val pixel = rowStart + x * 4
                val luma = (ubyte(rgba, pixel) * 299 + ubyte(rgba, pixel + 1) * 587 + ubyte(rgba, pixel + 2) * 114) / 1000
                grid[index++] = luma
            }
        }
        return grid
    }

    /** Mean absolute difference of two grids, 0..255; grids of different sizes never match. */
    fun difference(a: IntArray, b: IntArray): Int {
        if (a.size != b.size) return Int.MAX_VALUE
        var total = 0L
        for (i in a.indices) total += kotlin.math.abs(a[i] - b[i])
        return (total / a.size.coerceAtLeast(1)).toInt()
    }

    /** True when every sampled cell is essentially black: the capture is a black protected frame. */
    fun isBlank(grid: IntArray): Boolean = grid.all { it <= BLANK_LUMA }

    private fun ubyte(buffer: ByteBuffer, index: Int): Int = buffer.get(index).toInt() and 0xFF
}
