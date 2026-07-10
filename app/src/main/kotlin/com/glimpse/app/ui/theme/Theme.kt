package com.glimpse.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val GlimpseColorScheme = lightColorScheme(
    primary = GlimpsePink,
    onPrimary = GlimpseBackground,
    secondary = GlimpseBlue,
    background = GlimpseBackground,
    surface = GlimpseBackground,
    onBackground = GlimpseTextPrimary,
    onSurface = GlimpseTextPrimary
)

@Composable
fun GlimpseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GlimpseColorScheme,
        typography = GlimpseTypography,
        content = content
    )
}
