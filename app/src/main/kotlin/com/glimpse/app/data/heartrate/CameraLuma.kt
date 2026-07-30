package com.glimpse.app.data.heartrate

import androidx.camera.core.ImageProxy

// Reduces a camera frame to a single number: how bright the middle of it is.
// That number, sampled ~30 times a second with a fingertip sealed over the
// lens, is the raw material HeartRateAnalyzer turns into a pulse.
object CameraLuma {

    // Only the centre is measured. The edges of the frame are where light
    // leaks in around a fingertip, and that leaked light carries no pulse —
    // it only dilutes the signal that does.
    private const val ROI_FRACTION = 4

    // Reading every pixel of a 640x480 frame 30 times a second is a lot of
    // work for no benefit: neighbouring pixels see the same blood. Sampling
    // a grid is just as accurate and far cheaper, which matters because
    // dropped frames show up directly as a jittery sample rate.
    private const val PIXEL_STEP = 4

    // The Y (luma) plane of YUV_420_888. Using luma rather than converting
    // to RGB and taking the red channel: under a torch-lit fingertip the
    // image is essentially monochrome red anyway, so luma tracks the same
    // blood-volume changes without paying for a colour conversion on every
    // frame.
    fun averageCentreLuma(image: ImageProxy): Double {
        val plane = image.planes.firstOrNull() ?: return 0.0
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val startX = image.width / ROI_FRACTION
        val endX = image.width - startX
        val startY = image.height / ROI_FRACTION
        val endY = image.height - startY

        var total = 0L
        var count = 0
        var y = startY
        while (y < endY) {
            val rowStart = y * rowStride
            var x = startX
            while (x < endX) {
                val index = rowStart + x * pixelStride
                if (index in 0 until buffer.limit()) {
                    // Bytes are signed in Kotlin; luma is 0..255.
                    total += buffer.get(index).toInt() and 0xFF
                    count++
                }
                x += PIXEL_STEP
            }
            y += PIXEL_STEP
        }
        return if (count == 0) 0.0 else total.toDouble() / count
    }
}
