package com.glimpse.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val GlimpseColorScheme = lightColorScheme(
    primary = GlimpsePink,
    onPrimary = GlimpseSurface,
    primaryContainer = GlimpseChipBg,
    onPrimaryContainer = GlimpsePinkDark,
    secondary = GlimpseLavender,
    onSecondary = GlimpseSurface,
    background = GlimpseBackground,
    surface = GlimpseSurface,
    surfaceVariant = GlimpseChipBg,
    onBackground = GlimpseTextPrimary,
    onSurface = GlimpseTextPrimary,
    onSurfaceVariant = GlimpseTextSecondary
)

@Composable
fun GlimpseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GlimpseColorScheme,
        shapes = GlimpseShapes,
        typography = GlimpseTypography,
        content = content
    )
}
