package com.glimpse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val GlimpseLightColorScheme = lightColorScheme(
    primary = GlimpsePink,
    onPrimary = GlimpseSurface,
    primaryContainer = GlimpseChipBg,
    onPrimaryContainer = GlimpsePinkDark,
    secondary = GlimpseBlue,
    onSecondary = GlimpseSurface,
    tertiary = GlimpseYellow,
    onTertiary = GlimpseTextPrimary,
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

private val GlimpseDarkColorScheme = darkColorScheme(
    primary = GlimpsePinkOnDark,
    // Dark text on the lighter dark-mode pink reads better than white does —
    // the light-theme primary is dark enough for white text, the dark-theme
    // one isn't.
    onPrimary = GlimpseBackgroundOnDark,
    primaryContainer = GlimpseChipBgOnDark,
    onPrimaryContainer = GlimpsePinkDarkOnDark,
    secondary = GlimpseBlueOnDark,
    onSecondary = GlimpseBackgroundOnDark,
    tertiary = GlimpseYellowOnDark,
    onTertiary = GlimpseBackgroundOnDark,
    background = GlimpseBackgroundOnDark,
    surface = GlimpseSurfaceOnDark,
    surfaceVariant = GlimpseChipBgOnDark,
    onBackground = GlimpseTextPrimaryOnDark,
    onSurface = GlimpseTextPrimaryOnDark,
    onSurfaceVariant = GlimpseTextSecondaryOnDark,
    surfaceTint = GlimpsePinkOnDark
)

@Composable
fun GlimpseTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) GlimpseDarkColorScheme else GlimpseLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = GlimpseShapes,
        typography = GlimpseTypography,
        content = content
    )
}
