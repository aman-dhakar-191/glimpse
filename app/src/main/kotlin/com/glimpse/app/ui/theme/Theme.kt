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
    onSurfaceVariant = GlimpseTextSecondary,
    // Material3 blends surfaceTint into any elevated surface (e.g. Card)
    // whose container color is exactly colorScheme.surface — left
    // unspecified, it defaults to a baseline purple, which is why cards
    // rendered muddy gray-lavender instead of clean white. Pinning it to
    // our own accent keeps that blend on-brand (a faint pink cast) instead.
    surfaceTint = GlimpsePink
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
