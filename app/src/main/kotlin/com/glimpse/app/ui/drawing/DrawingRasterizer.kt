package com.glimpse.app.ui.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.glimpse.app.data.model.LiveStroke

// Turns the shared live canvas (a map of strokes, each a flat list of
// normalized 0.0..1.0 points) into a flat PNG for sending — see
// DrawingViewModel.send(). Rendered at a fixed square size regardless of
// either device's actual screen size, matching how the points themselves
// are stored normalized.
object DrawingRasterizer {
    private const val STROKE_WIDTH_FRACTION = 0.012f
    private const val DOT_RADIUS_FRACTION = 0.008f

    fun render(strokes: Collection<LiveStroke>, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = sizePx * STROKE_WIDTH_FRACTION
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        strokes.forEach { stroke ->
            paint.color = parseColor(stroke.color)
            val points = stroke.points
            if (points.size < 2) return@forEach

            if (points.size == 2) {
                // A tap with no drag — draw a dot rather than silently
                // dropping a stroke that has no line segment to render.
                canvas.drawCircle(
                    (points[0] * sizePx).toFloat(),
                    (points[1] * sizePx).toFloat(),
                    sizePx * DOT_RADIUS_FRACTION,
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
