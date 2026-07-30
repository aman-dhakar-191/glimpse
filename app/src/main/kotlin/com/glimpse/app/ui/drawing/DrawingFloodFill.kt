package com.glimpse.app.ui.drawing

import android.graphics.Color
import com.glimpse.app.data.model.LiveStroke

// A real paint-bucket fill for a Fill-mode tap that lands on EMPTY canvas
// rather than on a stroke (that case just fills the tapped stroke itself —
// see DrawingViewModel.fillStrokeAt). Works the way MS Paint's bucket does:
// rasterizes whatever's currently drawn into a coarse occupancy grid, floods
// outward from the tapped cell until it runs into drawn pixels or the canvas
// edge, and hands that region back as merged axis-aligned rectangles.
//
// Rectangles (not a traced outline) because a flood-filled region routinely
// has holes — filling the background around a ring leaves the ring itself
// unfilled — and a single traced contour would swallow them. Rectangles also
// keep the result a flat list of primitives, which is what Firebase
// round-trips reliably (same reasoning as LiveStroke.points).
object DrawingFloodFill {
    // Coarse on purpose: fine enough that the blocky edge is hidden under
    // the strokes bounding the region, small enough that a filled region
    // stays a few hundred rectangles rather than thousands.
    private const val GRID = 256

    // Empty when the tap landed on already-drawn pixels (nothing to flood)
    // or outside the canvas — callers treat that as "no fill happened."
    fun regionRectsAt(strokes: Collection<LiveStroke>, x: Float, y: Float): List<Double> {
        if (x < 0f || x > 1f || y < 0f || y > 1f) return emptyList()
        val blocked = occupancyGrid(strokes)
        val startX = (x * GRID).toInt().coerceIn(0, GRID - 1)
        val startY = (y * GRID).toInt().coerceIn(0, GRID - 1)
        if (blocked[startY * GRID + startX]) return emptyList()
        return mergedRects(flood(blocked, startX, startY))
    }

    // Reuses the SAME renderer the sent PNG goes through, so what counts as
    // a wall here is exactly what's visibly drawn — including earlier fills.
    // Rendered square while the canvas itself may not be; that stretches the
    // grid but preserves which regions are connected to which, which is all
    // a flood fill depends on.
    private fun occupancyGrid(strokes: Collection<LiveStroke>): BooleanArray {
        val bitmap = DrawingRasterizer.render(strokes, GRID)
        val pixels = IntArray(GRID * GRID)
        bitmap.getPixels(pixels, 0, GRID, 0, 0, GRID, GRID)
        bitmap.recycle()
        return BooleanArray(GRID * GRID) { pixels[it] != Color.WHITE }
    }

    // Plain 4-connected flood, iterative (not recursive — GRID² cells deep
    // would overflow the stack on a wide-open canvas).
    private fun flood(blocked: BooleanArray, startX: Int, startY: Int): BooleanArray {
        val filled = BooleanArray(GRID * GRID)
        val stack = ArrayDeque<Int>()
        val startIndex = startY * GRID + startX
        filled[startIndex] = true
        stack.addLast(startIndex)
        while (stack.isNotEmpty()) {
            val index = stack.removeLast()
            val cx = index % GRID
            val cy = index / GRID
            if (cx > 0) push(filled, blocked, stack, index - 1)
            if (cx < GRID - 1) push(filled, blocked, stack, index + 1)
            if (cy > 0) push(filled, blocked, stack, index - GRID)
            if (cy < GRID - 1) push(filled, blocked, stack, index + GRID)
        }
        return filled
    }

    private fun push(filled: BooleanArray, blocked: BooleanArray, stack: ArrayDeque<Int>, index: Int) {
        if (filled[index] || blocked[index]) return
        filled[index] = true
        stack.addLast(index)
    }

    // Row runs, greedily merged downward wherever the next row has a run
    // with identical bounds — turns a big open area into a handful of tall
    // rectangles instead of one per grid row.
    private fun mergedRects(filled: BooleanArray): List<Double> {
        val out = mutableListOf<Double>()
        // Keyed by run start x; value is (run end x, the row it opened on).
        var open = mutableMapOf<Int, Pair<Int, Int>>()
        for (y in 0 until GRID) {
            val runs = mutableMapOf<Int, Int>()
            var x = 0
            while (x < GRID) {
                if (filled[y * GRID + x]) {
                    val start = x
                    while (x < GRID && filled[y * GRID + x]) x++
                    runs[start] = x
                } else {
                    x++
                }
            }
            val next = mutableMapOf<Int, Pair<Int, Int>>()
            for ((startX, endX) in runs) {
                val previous = open[startX]
                // Same span as the row above: keep its original top edge
                // and let the rectangle grow. Otherwise start a new one.
                next[startX] = if (previous != null && previous.first == endX) {
                    endX to previous.second
                } else {
                    endX to y
                }
            }
            for ((startX, value) in open) {
                val continued = next[startX]
                if (continued == null || continued.second != value.second) {
                    emitRect(out, startX, value.second, value.first, y)
                }
            }
            open = next
        }
        for ((startX, value) in open) emitRect(out, startX, value.second, value.first, GRID)
        return out
    }

    private fun emitRect(out: MutableList<Double>, left: Int, top: Int, right: Int, bottom: Int) {
        out.add(left.toDouble() / GRID)
        out.add(top.toDouble() / GRID)
        out.add(right.toDouble() / GRID)
        out.add(bottom.toDouble() / GRID)
    }
}
