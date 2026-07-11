package com.glimpse.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Substantially more rounded than Material3's defaults (which sit around
// 4-16dp) so cards, fields, and dialogs read as soft/cute rather than
// standard boxy Android components. Buttons use their own default "full"
// (pill) shape already, so this mainly reshapes cards, text fields, and
// sheets.
val GlimpseShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)
