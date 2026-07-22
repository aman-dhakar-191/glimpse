package com.glimpse.app.ui.drawing

// A small fixed palette (not a full picker) — enough variety for a quick
// doodle without turning the drawing screen into its own project. Colors
// echo the app's own theme (see ui/theme/Color.kt) plus a couple of plain
// primaries so drawings aren't limited to the app's palette alone.
object DrawingColors {
    val PALETTE = listOf(
        "#2B1B33", // near-black, matches GlimpseTextPrimary
        "#FF6FA5", // GlimpsePink
        "#4FC3E8", // GlimpseBlue
        "#FFD23F", // GlimpseYellow
        "#4CAF50" // green, for contrast against the palette above
    )
    val DEFAULT = PALETTE.first()
}
