package com.glimpse.app.ui.drawing

// A small fixed set of one-tap presets (not the only way to pick a color —
// see hueToHex below for the full-spectrum slider) — colors echo the app's
// own theme (see ui/theme/Color.kt) plus a couple of plain primaries, and
// deliberately include near-black/white which a pure hue slider can't
// reach at a fixed saturation/value.
object DrawingColors {
    val PALETTE = listOf(
        "#2B1B33", // near-black, matches GlimpseTextPrimary
        "#FFFFFF", // white — useful on darker backgrounds/undo mistakes
        "#FF6FA5", // GlimpsePink
        "#4FC3E8", // GlimpseBlue
        "#FFD23F", // GlimpseYellow
        "#4CAF50" // green, for contrast against the palette above
    )
    val DEFAULT = PALETTE.first()

    // Fraction of the canvas's min dimension — see LiveStroke.width. Range
    // picked by feel: thin enough for detail work, thick enough to read as
    // a marker rather than a hairline.
    const val MIN_WIDTH_FRACTION = 0.004f
    const val MAX_WIDTH_FRACTION = 0.05f
    const val DEFAULT_WIDTH_FRACTION = 0.012f

    // Fixed saturation/value, varying only hue — a full HSV square (plus a
    // separate lightness control) would cover more ground, but a single
    // slider already gets you the full color wheel at one consistently
    // vivid, legible brightness, which is enough for a quick doodle without
    // the picker becoming its own multi-control UI.
    private const val PICKER_SATURATION = 0.85f
    private const val PICKER_VALUE = 0.85f

    fun hueToHex(hueDegrees: Float): String {
        val hsv = floatArrayOf(hueDegrees.coerceIn(0f, 360f), PICKER_SATURATION, PICKER_VALUE)
        val colorInt = android.graphics.Color.HSVToColor(hsv)
        return String.format("#%06X", 0xFFFFFF and colorInt)
    }
}
