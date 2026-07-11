package com.glimpse.app.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Shape

// The "Blob Pop" shape language: organic asymmetric silhouettes built from
// real bezier curves (GenericShape gives a Path with the size already
// scaled in, same idea as an SVG clipPath with clipPathUnits="objectBoundingBox").
// Every shape below is ported 1:1 from the design-preview artifact's SVG
// path data, so what was approved in the preview is exactly what renders
// here. Material3's Surface/Button/Card all accept a Shape directly via
// their `shape` param, so these plug in without needing manual clip()
// modifiers.
//
// Deliberately not reused 1:1 everywhere a card/button/chip is needed —
// see each screen for which of BlobShapeA/B/C, the chip pair, etc. it uses.
// Reusing the exact same silhouette for every surface was the thing the
// first pass got wrong.

val BlobShapeA: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0.5f * w, 0.05f * h)
    cubicTo(0.68f * w, 0.02f * h, 0.88f * w, 0.08f * h, 0.95f * w, 0.25f * h)
    cubicTo(1.02f * w, 0.42f * h, 0.99f * w, 0.6f * h, 0.92f * w, 0.75f * h)
    cubicTo(0.85f * w, 0.9f * h, 0.68f * w, 0.99f * h, 0.5f * w, 0.98f * h)
    cubicTo(0.32f * w, 0.97f * h, 0.14f * w, 0.88f * h, 0.07f * w, 0.72f * h)
    cubicTo(0f * w, 0.56f * h, 0.02f * w, 0.36f * h, 0.12f * w, 0.2f * h)
    cubicTo(0.2f * w, 0.08f * h, 0.34f * w, 0.07f * h, 0.5f * w, 0.05f * h)
    close()
}

val BlobShapeB: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0.1f * w, 0.22f * h)
    cubicTo(0.02f * w, 0.08f * h, 0.22f * w, -0.02f * h, 0.4f * w, 0.04f * h)
    cubicTo(0.55f * w, 0.09f * h, 0.6f * w, 0.22f * h, 0.75f * w, 0.18f * h)
    cubicTo(0.92f * w, 0.13f * h, 1.04f * w, 0.3f * h, 0.98f * w, 0.48f * h)
    cubicTo(0.93f * w, 0.63f * h, 0.8f * w, 0.6f * h, 0.82f * w, 0.76f * h)
    cubicTo(0.85f * w, 0.96f * h, 0.62f * w, 1.06f * h, 0.44f * w, 0.98f * h)
    cubicTo(0.3f * w, 0.92f * h, 0.32f * w, 0.8f * h, 0.16f * w, 0.82f * h)
    cubicTo(-0.02f * w, 0.84f * h, -0.08f * w, 0.64f * h, 0f * w, 0.48f * h)
    cubicTo(0.06f * w, 0.36f * h, 0.16f * w, 0.34f * h, 0.1f * w, 0.22f * h)
    close()
}

val BlobShapeC: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0.06f * w, 0.34f * h)
    cubicTo(0f * w, 0.16f * h, 0.18f * w, 0.02f * h, 0.36f * w, 0.02f * h)
    cubicTo(0.5f * w, 0.02f * h, 0.5f * w, 0.12f * h, 0.64f * w, 0.08f * h)
    cubicTo(0.82f * w, 0.03f * h, 1.02f * w, 0.14f * h, 0.98f * w, 0.34f * h)
    cubicTo(0.95f * w, 0.48f * h, 0.84f * w, 0.44f * h, 0.88f * w, 0.6f * h)
    cubicTo(0.93f * w, 0.8f * h, 0.78f * w, 0.98f * h, 0.58f * w, 0.98f * h)
    cubicTo(0.44f * w, 0.98f * h, 0.44f * w, 0.88f * h, 0.28f * w, 0.9f * h)
    cubicTo(0.1f * w, 0.92f * h, -0.04f * w, 0.78f * h, 0.02f * w, 0.6f * h)
    cubicTo(0.05f * w, 0.5f * h, 0.1f * w, 0.46f * h, 0.06f * w, 0.34f * h)
    close()
}

// The original path above reads as a near-perfect flat ellipse once
// stretched to a fillMaxWidth() button's wide/short aspect ratio — its
// wobble is spread evenly around the silhouette, and evenly-spread wobble
// on a squashed shape just looks like a smooth curve. This version instead
// puts two sharp asymmetric kinks (small width-fraction jumps, so they
// survive being squashed vertically) at different points on the top and
// bottom edges, so the organic irregularity still reads at a wide/short
// aspect ratio instead of averaging out.
val BlobButtonShape: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0.07f * w, 0.52f * h)
    cubicTo(0.02f * w, 0.22f * h, 0.18f * w, -0.04f * h, 0.42f * w, 0.02f * h)
    cubicTo(0.58f * w, 0.06f * h, 0.55f * w, 0.2f * h, 0.72f * w, 0.14f * h)
    cubicTo(0.86f * w, 0.09f * h, 1.02f * w, 0.22f * h, 0.98f * w, 0.44f * h)
    cubicTo(0.96f * w, 0.56f * h, 0.88f * w, 0.5f * h, 0.9f * w, 0.62f * h)
    cubicTo(0.93f * w, 0.8f * h, 0.78f * w, 1.02f * h, 0.56f * w, 0.98f * h)
    cubicTo(0.46f * w, 0.96f * h, 0.48f * w, 0.86f * h, 0.34f * w, 0.9f * h)
    cubicTo(0.16f * w, 0.95f * h, 0.02f * w, 0.8f * h, 0.07f * w, 0.52f * h)
    close()
}

// Content-safe counterparts to BlobShapeA/B/C: the same organic asymmetric
// character, but with the maximum edge inset capped around 10-12% instead
// of the ~40-50% deep pinches those shapes have at their sharpest corners.
// Fixed-dp padding (20-28dp) can't reliably clear a pinch that deep on a
// real device at real card widths — real-device screenshots showed text
// getting clipped at exactly those corners. Use these on any card carrying
// paragraph text or multiple text rows; the more dramatic A/B/C are still
// fine for small, short, centered content like chips and stat numbers.
val BlobShapeSoftA: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0.5f * w, 0.02f * h)
    cubicTo(0.68f * w, 0f * h, 0.87f * w, 0.06f * h, 0.94f * w, 0.18f * h)
    cubicTo(1f * w, 0.28f * h, 0.98f * w, 0.4f * h, 0.99f * w, 0.5f * h)
    cubicTo(1f * w, 0.62f * h, 1f * w, 0.74f * h, 0.93f * w, 0.84f * h)
    cubicTo(0.85f * w, 0.96f * h, 0.68f * w, 1f * h, 0.5f * w, 0.99f * h)
    cubicTo(0.33f * w, 0.99f * h, 0.15f * w, 0.95f * h, 0.07f * w, 0.84f * h)
    cubicTo(0f * w, 0.74f * h, 0.01f * w, 0.62f * h, 0.01f * w, 0.5f * h)
    cubicTo(0f * w, 0.38f * h, 0.01f * w, 0.24f * h, 0.09f * w, 0.15f * h)
    cubicTo(0.17f * w, 0.06f * h, 0.33f * w, 0.03f * h, 0.5f * w, 0.02f * h)
    close()
}

val BlobShapeSoftB: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0.16f * w, 0.06f * h)
    cubicTo(0.28f * w, -0.01f * h, 0.4f * w, 0.02f * h, 0.5f * w, 0.03f * h)
    cubicTo(0.63f * w, 0.04f * h, 0.72f * w, -0.01f * h, 0.84f * w, 0.06f * h)
    cubicTo(0.96f * w, 0.13f * h, 1.01f * w, 0.26f * h, 0.98f * w, 0.4f * h)
    cubicTo(0.96f * w, 0.5f * h, 1f * w, 0.58f * h, 0.98f * w, 0.68f * h)
    cubicTo(0.95f * w, 0.84f * h, 0.84f * w, 0.97f * h, 0.68f * w, 0.99f * h)
    cubicTo(0.58f * w, 1.01f * h, 0.5f * w, 0.96f * h, 0.4f * w, 0.97f * h)
    cubicTo(0.26f * w, 0.99f * h, 0.12f * w, 0.94f * h, 0.05f * w, 0.82f * h)
    cubicTo(-0.01f * w, 0.7f * h, 0.02f * w, 0.58f * h, 0.02f * w, 0.46f * h)
    cubicTo(0.02f * w, 0.3f * h, 0.03f * w, 0.14f * h, 0.16f * w, 0.06f * h)
    close()
}

val BlobShapeSoftC: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0.12f * w, 0.16f * h)
    cubicTo(0.18f * w, 0.04f * h, 0.32f * w, -0.01f * h, 0.46f * w, 0.02f * h)
    cubicTo(0.58f * w, 0.04f * h, 0.68f * w, 0.02f * h, 0.8f * w, 0.08f * h)
    cubicTo(0.93f * w, 0.14f * h, 1.01f * w, 0.26f * h, 0.98f * w, 0.4f * h)
    cubicTo(0.96f * w, 0.5f * h, 0.9f * w, 0.46f * h, 0.92f * w, 0.58f * h)
    cubicTo(0.95f * w, 0.74f * h, 0.9f * w, 0.9f * h, 0.76f * w, 0.97f * h)
    cubicTo(0.64f * w, 1.02f * h, 0.5f * w, 0.98f * h, 0.38f * w, 0.98f * h)
    cubicTo(0.24f * w, 0.98f * h, 0.1f * w, 0.94f * h, 0.04f * w, 0.82f * h)
    cubicTo(-0.01f * w, 0.71f * h, 0.04f * w, 0.6f * h, 0.03f * w, 0.48f * h)
    cubicTo(0.02f * w, 0.34f * h, 0.05f * w, 0.26f * h, 0.12f * w, 0.16f * h)
    close()
}

val BlobMarkShape: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0.5f * w, 0.04f * h)
    cubicTo(0.7f * w, 0.02f * h, 0.92f * w, 0.14f * h, 0.96f * w, 0.36f * h)
    cubicTo(1f * w, 0.56f * h, 0.88f * w, 0.72f * h, 0.7f * w, 0.86f * h)
    cubicTo(0.56f * w, 0.97f * h, 0.4f * w, 0.98f * h, 0.26f * w, 0.88f * h)
    cubicTo(0.08f * w, 0.76f * h, -0.02f * w, 0.54f * h, 0.04f * w, 0.34f * h)
    cubicTo(0.1f * w, 0.14f * h, 0.3f * w, 0.06f * h, 0.5f * w, 0.04f * h)
    close()
}

val BlobChipShapeA: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0.5f * w, 0.06f * h)
    cubicTo(0.74f * w, 0.02f * h, 0.96f * w, 0.2f * h, 0.94f * w, 0.46f * h)
    cubicTo(0.92f * w, 0.7f * h, 0.72f * w, 0.9f * h, 0.48f * w, 0.94f * h)
    cubicTo(0.26f * w, 0.98f * h, 0.06f * w, 0.82f * h, 0.04f * w, 0.58f * h)
    cubicTo(0.02f * w, 0.34f * h, 0.16f * w, 0.14f * h, 0.38f * w, 0.08f * h)
    cubicTo(0.42f * w, 0.07f * h, 0.46f * w, 0.07f * h, 0.5f * w, 0.06f * h)
    close()
}

val BlobChipShapeB: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0.46f * w, 0.04f * h)
    cubicTo(0.68f * w, 0.08f * h, 0.9f * w, 0.06f * h, 0.96f * w, 0.28f * h)
    cubicTo(1.02f * w, 0.5f * h, 0.86f * w, 0.64f * h, 0.74f * w, 0.8f * h)
    cubicTo(0.6f * w, 0.98f * h, 0.36f * w, 1.02f * h, 0.2f * w, 0.88f * h)
    cubicTo(0.04f * w, 0.74f * h, 0f * w, 0.5f * h, 0.08f * w, 0.3f * h)
    cubicTo(0.16f * w, 0.12f * h, 0.28f * w, 0f * h, 0.46f * w, 0.04f * h)
    close()
}

// Chat-bubble shape with an organic curved tail — mirrored for outgoing
// (your own messages, tail on the right) vs incoming (partner's, tail on
// the left), same left/right convention as any chat UI.
fun bubbleShape(tailOnRight: Boolean): Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    fun mx(fx: Float): Float = if (tailOnRight) (1f - fx) * w else fx * w

    moveTo(mx(0.06f), 0.08f * h)
    quadraticTo(mx(0.06f), 0f * h, mx(0.14f), 0f * h)
    lineTo(mx(0.86f), 0f * h)
    quadraticTo(mx(0.96f), 0f * h, mx(0.96f), 0.1f * h)
    lineTo(mx(0.96f), 0.62f * h)
    quadraticTo(mx(0.96f), 0.74f * h, mx(0.84f), 0.74f * h)
    lineTo(mx(0.32f), 0.74f * h)
    quadraticTo(mx(0.24f), 0.74f * h, mx(0.18f), 0.8f * h)
    lineTo(mx(0.08f), 0.92f * h)
    quadraticTo(mx(0.03f), 0.97f * h, mx(0.04f), 0.89f * h)
    lineTo(mx(0.06f), 0.76f * h)
    quadraticTo(mx(0f), 0.74f * h, mx(0f), 0.62f * h)
    lineTo(mx(0f), 0.08f * h)
    close()
}
