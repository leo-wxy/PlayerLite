package com.wxy.playerlite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = DeepLagoon,
    onPrimary = Paper,
    secondary = SunsetOrange,
    onSecondary = Paper,
    tertiary = Seafoam,
    onTertiary = Charcoal,
    background = Paper,
    onBackground = Charcoal,
    surface = Color(0xFFFEF9F0),
    onSurface = Charcoal,
    surfaceVariant = Porcelain,
    onSurfaceVariant = Dust,
    outline = MistBlue,
    error = CanyonRed,
    onError = Paper
)

private val DarkColorScheme = darkColorScheme(
    primary = NightAccent,
    onPrimary = Night,
    secondary = NightWarm,
    onSecondary = Night,
    tertiary = Seafoam,
    onTertiary = Night,
    background = Night,
    onBackground = Color(0xFFEAF2F1),
    surface = NightSurface,
    onSurface = Color(0xFFEAF2F1),
    surfaceVariant = Coal,
    onSurfaceVariant = Color(0xFFB7C4CB),
    outline = Color(0xFF58656E),
    error = Color(0xFFE88973),
    onError = Night
)

@Composable
fun PlayerLiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
