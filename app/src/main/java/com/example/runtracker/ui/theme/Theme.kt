package com.example.runtracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = RunGreen,
    secondary = RunGreenLight,
    tertiary = RunAmber,
    background = Background,
    surface = Surface,
    onSurface = OnSurface
)

private val DarkColors = darkColorScheme(
    primary = RunGreenLight,
    secondary = RunGreen,
    tertiary = RunAmber
)

@Composable
fun RunTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
