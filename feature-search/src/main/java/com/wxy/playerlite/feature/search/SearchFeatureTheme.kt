package com.wxy.playerlite.feature.search

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val SearchLightColorScheme = lightColorScheme(
    primary = Color(0xFF176B76),
    onPrimary = Color(0xFFFDF6EA),
    secondary = Color(0xFFE07A2F),
    onSecondary = Color(0xFFFDF6EA),
    tertiary = Color(0xFF4AA89D),
    onTertiary = Color(0xFF1F2529),
    background = Color(0xFFFDF6EA),
    onBackground = Color(0xFF1F2529),
    surface = Color(0xFFFEF9F0),
    onSurface = Color(0xFF1F2529),
    surfaceVariant = Color(0xFFF6E7D6),
    onSurfaceVariant = Color(0xFF6A7784),
    outline = Color(0xFFD7E7E4),
    error = Color(0xFFC7512D),
    onError = Color(0xFFFDF6EA)
)

private val SearchDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7CCFC1),
    onPrimary = Color(0xFF0F161B),
    secondary = Color(0xFFF7BC7A),
    onSecondary = Color(0xFF0F161B),
    tertiary = Color(0xFF4AA89D),
    onTertiary = Color(0xFF0F161B),
    background = Color(0xFF0F161B),
    onBackground = Color(0xFFEAF2F1),
    surface = Color(0xFF1A242B),
    onSurface = Color(0xFFEAF2F1),
    surfaceVariant = Color(0xFF2A3238),
    onSurfaceVariant = Color(0xFFB7C4CB),
    outline = Color(0xFF58656E),
    error = Color(0xFFE88973),
    onError = Color(0xFF0F161B)
)

private val SearchTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.6.sp
    )
)

@Composable
internal fun SearchFeatureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) SearchDarkColorScheme else SearchLightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = SearchTypography,
        content = content
    )
}
