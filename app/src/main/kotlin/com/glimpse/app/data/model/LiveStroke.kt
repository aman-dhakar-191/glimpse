package com.glimpse.app.data.model

// One in-progress or finished stroke on the shared live drawing canvas
// (shared/live_drawing/strokes/{strokeId}) — see FirebaseSync/DrawingViewModel.
// Flattened x0,y0,x1,y1,... (normalized 0.0..1.0, independent of either
// device's actual screen size) rather than a list of nested point objects —
// flat lists round-trip through Firebase's reflection-based getValue(Class)
// far more reliably than nested POJOs. Double (not Float) since that's the
// actual numeric type Firebase's JSON-backed store deserializes to; convert
// to Float only at the Compose UI boundary.
data class LiveStroke @JvmOverloads constructor(
    val authorUid: String = "",
    val color: String = "",
    val points: List<Double> = emptyList(),
    // Fraction of the canvas's min dimension — matches how PHOTO_MASK_SIZE-
    // style widget code already scales fixed pixel values by a size-
    // independent fraction. Defaults to the original fixed pen size so
    // strokes drawn before this field existed still deserialize and render
    // at the same thickness they were drawn at.
    val width: Double = 0.012
)
