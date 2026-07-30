package com.glimpse.app.ui.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.glimpse.app.data.model.LiveStroke

// Turns the shared live canvas (a map of strokes, each a flat list of
// normalized 0.0..1.0 points) into a flat PNG for sending — see
// DrawingViewModel.send(). Rendered at a fixed square size regardless of
// either device's actual screen size, matching how the points themselves
// are stored normalized.
object DrawingRasterizer {
    // A tapped dot's radius, relative to that stroke's own width — keeps a
    // lone tap visually consistent with whatever pen size was selected
    // rather than always the same fixed dot regardless of pen thickness.
    private const val DOT_RADIUS_TO_WIDTH_RATIO = 0.6f

    fun render(strokes: Collection<LiveStroke>, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        strokes.forEach { stroke ->
            val points = stroke.points
            if (points.size < 2) return@forEach
            val strokeWidthPx = sizePx * stroke.width.toFloat()
            paint.color = parseColor(stroke.color)
            paint.alpha = 255
            paint.pathEffect = null

            if (points.size == 2 && !stroke.isFilled) {
                // A tap with no drag — draw a dot rather than silently
                // dropping a stroke that has no line segment to render.
                canvas.drawCircle(
                    (points[0] * sizePx).toFloat(),
                    (points[1] * sizePx).toFloat(),
                    strokeWidthPx * DOT_RADIUS_TO_WIDTH_RATIO,
                    paint.apply { style = Paint.Style.FILL }
                )
                paint.style = Paint.Style.STROKE
                return@forEach
            }

            val path = Path().apply {
                moveTo((points[0] * sizePx).toFloat(), (points[1] * sizePx).toFloat())
            }
            var i = 2
            while (i + 1 < points.size) {
                path.lineTo((points[i] * sizePx).toFloat(), (points[i + 1] * sizePx).toFloat())
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

        return bitmap
    }

    private fun parseColor(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (e: IllegalArgumentException) {
        Color.BLACK
    }
}
