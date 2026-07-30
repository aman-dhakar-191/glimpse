package com.glimpse.app.ui.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.glimpse.app.data.model.LiveStroke
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Turns the shared live canvas (a map of strokes, each a flat list of
// normalized 0.0..1.0 points) into a flat PNG — see DrawingViewModel.send().
object DrawingRasterizer {
    // A tapped dot's radius, relative to that stroke's own width — keeps a
    // lone tap visually consistent with whatever pen size was selected
    // rather than always the same fixed dot regardless of pen thickness.
    private const val DOT_RADIUS_TO_WIDTH_RATIO = 0.6f

    // How far outside the canvas (in canvas-widths/heights) the sent image
    // will follow strokes drawn past the edge. Generous enough to cover
    // normal off-canvas drawing while zoomed, capped so one stray far-away
    // stroke can't shrink the actual drawing to a speck.
    private const val MAX_OUT_OF_BOUNDS = 1f

    // The whole canvas as a square, exactly the normalized 0..1 space the
    // points are stored in — used by DrawingFloodFill, which maps grid
    // cells straight back to that space and so needs it unchanged.
    fun render(strokes: Collection<LiveStroke>, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        drawStrokes(
            canvas = canvas,
            strokes = strokes,
            originX = 0f,
            originY = 0f,
            scaleX = sizePx.toFloat(),
            scaleY = sizePx.toFloat(),
            widthBasisPx = sizePx.toFloat()
        )
        return bitmap
    }

    // The version that actually gets sent. Two things it does that a plain
    // square render of the 0..1 space can't:
    //
    //  - Keeps the sender's canvas ASPECT RATIO. Points are normalized
    //    against each device's own canvas, so squeezing a tall phone canvas
    //    into a square silently flattened every circle into an ellipse.
    //  - Includes anything drawn OUTSIDE the canvas edges (possible since
    //    strokes stopped being clamped, so you can draw past the edge while
    //    zoomed) instead of cutting it off at 0..1.
    //
    // Falls back to a plain square render if the caller doesn't know its
    // canvas size yet.
    fun renderForSend(
        strokes: Collection<LiveStroke>,
        canvasWidthPx: Int,
        canvasHeightPx: Int,
        maxSizePx: Int
    ): Bitmap {
        if (canvasWidthPx <= 0 || canvasHeightPx <= 0) return render(strokes, maxSizePx)

        val bounds = contentBounds(strokes)
        val widthFraction = bounds[2] - bounds[0]
        val heightFraction = bounds[3] - bounds[1]
        // Canvas pixels the window spans — this is what carries the aspect
        // ratio through, since a fraction of the width and the same fraction
        // of the height are different numbers of pixels on a non-square canvas.
        val spanX = widthFraction * canvasWidthPx
        val spanY = heightFraction * canvasHeightPx
        val fit = maxSizePx / max(spanX, spanY)

        val bitmap = Bitmap.createBitmap(
            (spanX * fit).roundToInt().coerceAtLeast(1),
            (spanY * fit).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        drawStrokes(
            canvas = canvas,
            strokes = strokes,
            originX = bounds[0],
            originY = bounds[1],
            scaleX = canvasWidthPx * fit,
            scaleY = canvasHeightPx * fit,
            // Matches DrawingScreen.drawLiveStroke, which scales pen width by
            // the canvas's MIN dimension — so a stroke keeps the thickness
            // (relative to the drawing) that it had while being drawn.
            widthBasisPx = min(canvasWidthPx, canvasHeightPx) * fit
        )
        return bitmap
    }

    // The whole canvas plus however far past its edges anything was actually
    // drawn, as normalized left, top, right, bottom. Always contains the full
    // 0..1 canvas even for a drawing crammed into one corner — the empty space
    // around it is part of how it was composed.
    private fun contentBounds(strokes: Collection<LiveStroke>): FloatArray {
        var left = 0f
        var top = 0f
        var right = 1f
        var bottom = 1f
        for (stroke in strokes) {
            // Even indices are x, odd are y, for `points` (x,y pairs) and
            // `fillRects` (left,top,right,bottom quads) alike.
            for (coordinates in listOf(stroke.points, stroke.fillRects)) {
                var i = 0
                while (i + 1 < coordinates.size) {
                    val x = coordinates[i].toFloat()
                    val y = coordinates[i + 1].toFloat()
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                    i += 2
                }
            }
        }
        return floatArrayOf(
            left.coerceAtLeast(-MAX_OUT_OF_BOUNDS),
            top.coerceAtLeast(-MAX_OUT_OF_BOUNDS),
            right.coerceAtMost(1f + MAX_OUT_OF_BOUNDS),
            bottom.coerceAtMost(1f + MAX_OUT_OF_BOUNDS)
        )
    }

    private fun drawStrokes(
        canvas: Canvas,
        strokes: Collection<LiveStroke>,
        originX: Float,
        originY: Float,
        scaleX: Float,
        scaleY: Float,
        widthBasisPx: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        fun mapX(normalized: Double) = ((normalized.toFloat() - originX) * scaleX)
        fun mapY(normalized: Double) = ((normalized.toFloat() - originY) * scaleY)

        // Flood-fill regions first, so they sit UNDER the strokes that bound
        // them — a fill's blocky grid edge can otherwise overlap a boundary
        // stroke by a cell and nibble into it. See DrawingFloodFill.
        strokes.sortedBy { if (it.fillRects.isEmpty()) 1 else 0 }.forEach { stroke ->
            if (stroke.fillRects.isNotEmpty()) {
                paint.color = parseColor(stroke.color)
                paint.alpha = 255
                paint.pathEffect = null
                paint.style = Paint.Style.FILL
                var i = 0
                while (i + 3 < stroke.fillRects.size) {
                    canvas.drawRect(
                        mapX(stroke.fillRects[i]),
                        mapY(stroke.fillRects[i + 1]),
                        mapX(stroke.fillRects[i + 2]),
                        mapY(stroke.fillRects[i + 3]),
                        paint
                    )
                    i += 4
                }
                paint.style = Paint.Style.STROKE
                return@forEach
            }
            val points = stroke.points
            if (points.size < 2) return@forEach
            val strokeWidthPx = widthBasisPx * stroke.width.toFloat()
            paint.color = parseColor(stroke.color)
            paint.alpha = 255
            paint.pathEffect = null

            if (points.size == 2 && !stroke.isFilled) {
                // A tap with no drag — draw a dot rather than silently
                // dropping a stroke that has no line segment to render.
                canvas.drawCircle(
                    mapX(points[0]),
                    mapY(points[1]),
                    strokeWidthPx * DOT_RADIUS_TO_WIDTH_RATIO,
                    paint.apply { style = Paint.Style.FILL }
                )
                paint.style = Paint.Style.STROKE
                return@forEach
            }

            val path = Path().apply { moveTo(mapX(points[0]), mapY(points[1])) }
            var i = 2
            while (i + 1 < points.size) {
                path.lineTo(mapX(points[i]), mapY(points[i + 1]))
                i += 2
            }

            // Matches DrawingScreen.drawLiveStroke's live rendering, so the
            // final sent PNG looks the same as what you were drawing.
            if (stroke.isFilled) {
                path.close()
                paint.style = Paint.Style.FILL
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.STROKE
                return@forEach
            }

            when (stroke.brushType) {
                DrawingColors.BrushTypes.SQUARE -> {
                    paint.strokeCap = Paint.Cap.SQUARE
                    paint.strokeJoin = Paint.Join.MITER
                    paint.strokeWidth = strokeWidthPx
                }
                DrawingColors.BrushTypes.MARKER -> {
                    paint.strokeCap = Paint.Cap.SQUARE
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.strokeWidth = strokeWidthPx * 1.7f
                    paint.alpha = (255 * 0.55f).toInt()
                }
                DrawingColors.BrushTypes.DASHED -> {
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.strokeWidth = strokeWidthPx
                    paint.pathEffect = DashPathEffect(floatArrayOf(strokeWidthPx * 2.2f, strokeWidthPx * 1.6f), 0f)
                }
                else -> {
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.strokeWidth = strokeWidthPx
                }
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun parseColor(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (e: IllegalArgumentException) {
        Color.BLACK
    }
}
