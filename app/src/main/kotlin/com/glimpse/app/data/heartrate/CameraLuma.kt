package com.glimpse.app.data.heartrate

import androidx.camera.core.ImageProxy
import kotlin.math.sqrt

// Reduces a camera frame to two numbers: how bright the middle of it is, and
// how uniform that brightness is. The first, sampled ~30 times a second with
// a fingertip sealed over the lens, is what HeartRateAnalyzer turns into a
// pulse. The second is how we know there's a fingertip there at all.
object CameraLuma {

    data class Frame(
        val mean: Double,
        // Standard deviation across the sampled pixels. A fingertip lit from
        // behind by the torch is a flat wash of one colour, so this collapses
        // to near zero. A room has edges, corners and shadows, so it doesn't.
        // That difference is the whole finger-detection test.
        val spread: Double
    ) {
        // Thresholds are deliberately loose, and both numbers are shown on
        // screen so they can be tuned against real hardware rather than
        // guessed at twice. The mean floor only rules out a fully black
        // frame — a covered lens with the torch on is never that dark.
        val fingerDetected: Boolean get() = spread < MAX_COVERED_SPREAD && mean > MIN_COVERED_MEAN
    }

    const val MAX_COVERED_SPREAD = 22.0
    const val MIN_COVERED_MEAN = 15.0

    // Only the centre is measured. The edges of the frame are where light
    // leaks in around a fingertip, and that leaked light carries no pulse —
    // it only dilutes the signal that does.
    private const val ROI_FRACTION = 4

    // Reading every pixel of a frame 30 times a second is a lot of work for
    // no benefit: neighbouring pixels see the same blood. Sampling a grid is
    // just as accurate and far cheaper, which matters because dropped frames
    // show up directly as a jittery sample rate.
    private const val PIXEL_STEP = 4

    // The Y (luma) plane of YUV_420_888. Using luma rather than converting to
    // RGB and taking the red channel: under a torch-lit fingertip the image
    // is essentially monochrome red anyway, so luma tracks the same
    // blood-volume changes without paying for a colour conversion per frame.
    fun analyse(image: ImageProxy): Frame {
        val plane = image.planes.firstOrNull() ?: return Frame(0.0, 0.0)
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val startX = image.width / ROI_FRACTION
        val endX = image.width - startX
        val startY = image.height / ROI_FRACTION
        val endY = image.height - startY

        // Sum and sum-of-squares together, so mean and spread both come out
        // of a single pass over the frame.
        var total = 0.0
        var totalSquares = 0.0
        var count = 0
        var y = startY
        while (y < endY) {
            val rowStart = y * rowStride
            var x = startX
            while (x < endX) {
                val index = rowStart + x * pixelStride
                if (index in 0 until buffer.limit()) {
                    // Bytes are signed in Kotlin; luma is 0..255.
                    val value = (buffer.get(index).toInt() and 0xFF).toDouble()
                    total += value
                    totalSquares += value * value
                    count++
                }
                x += PIXEL_STEP
            }
            y += PIXEL_STEP
        }
        if (count == 0) return Frame(0.0, 0.0)

        val mean = total / count
        // Rounding can push this a hair below zero for a perfectly flat
        // frame, and sqrt of a negative is NaN.
        val variance = (totalSquares / count - mean * mean).coerceAtLeast(0.0)
        return Frame(mean, sqrt(variance))
    }
}
