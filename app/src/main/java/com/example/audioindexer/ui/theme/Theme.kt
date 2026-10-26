package com.example.audioindexer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = DarkBackground,
    primaryContainer = SecondaryViolet,
    onPrimaryContainer = TextPrimary,
    secondary = SecondaryViolet,
    onSecondary = TextPrimary,
    tertiary = TertiaryAmber,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkSurfaceBorder
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryCyanDark,
    onPrimary = LightSurface,
    primaryContainer = SecondaryViolet,
    onPrimaryContainer = LightSurface,
    secondary = SecondaryViolet,
    onSecondary = LightSurface,
    tertiary = TertiaryAmber,
    onTertiary = LightSurface,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightSurfaceBorder
)

@Composable
fun AudioIndexerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
