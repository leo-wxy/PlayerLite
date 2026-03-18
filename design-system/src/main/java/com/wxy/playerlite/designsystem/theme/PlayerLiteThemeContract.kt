package com.wxy.playerlite.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Immutable
data class PlayerLiteBrandPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral: Color,
    val neutralVariant: Color,
    val neutralStrong: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val error: Color
)

@Immutable
data class PlayerLiteBrandPalettes(
    val light: PlayerLiteBrandPalette,
    val dark: PlayerLiteBrandPalette
)

@Immutable
data class PlayerLiteVisualTokens(
    val canvas: Color,
    val surfacePrimary: Color,
    val surfaceMuted: Color,
    val surfaceRaised: Color,
    val surfaceHighlight: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val dividerSubtle: Color,
    val handleMuted: Color,
    val accentStrong: Color,
    val accentSupport: Color,
    val miniPlayerProgressTrack: Color,
    val miniPlayerProgressFill: Color
)

object PlayerLiteThemeContract {
    val DefaultBrandPalettes = PlayerLiteBrandPalettes(
        light = PlayerLiteBrandPalette(
            primary = Color(0xFFE53935),
            secondary = Color(0xFF616161),
            tertiary = Color(0xFF0087A0),
            neutral = Color(0xFFF9F9FB),
            neutralVariant = Color(0xFFF1F1F4),
            neutralStrong = Color(0xFFE6E6EA),
            onSurface = Color(0xFF131316),
            onSurfaceVariant = Color(0xFF74747A),
            outline = Color(0xFFDADAE0),
            error = Color(0xFFB3261E)
        ),
        dark = PlayerLiteBrandPalette(
            primary = Color(0xFFFF6E67),
            secondary = Color(0xFFC7C7CC),
            tertiary = Color(0xFF4DC3D9),
            neutral = Color(0xFF111315),
            neutralVariant = Color(0xFF191C1F),
            neutralStrong = Color(0xFF24282C),
            onSurface = Color(0xFFF2F2F5),
            onSurfaceVariant = Color(0xFFB8B8BE),
            outline = Color(0xFF3C4044),
            error = Color(0xFFFFB4AB)
        )
    )

    fun colorScheme(
        darkTheme: Boolean,
        brandPalettes: PlayerLiteBrandPalettes = DefaultBrandPalettes
    ): ColorScheme {
        return if (darkTheme) {
            brandPalettes.dark.toDarkColorScheme()
        } else {
            brandPalettes.light.toLightColorScheme()
        }
    }

    fun visualTokens(
        darkTheme: Boolean,
        colorScheme: ColorScheme
    ): PlayerLiteVisualTokens {
        return PlayerLiteVisualTokens(
            canvas = colorScheme.background,
            surfacePrimary = colorScheme.surface,
            surfaceMuted = colorScheme.surfaceVariant,
            surfaceRaised = if (darkTheme) Color(0xFF1D2024) else Color.White,
            surfaceHighlight = colorScheme.primary.copy(alpha = if (darkTheme) 0.14f else 0.08f),
            textSecondary = colorScheme.onSurfaceVariant,
            textMuted = colorScheme.secondary,
            dividerSubtle = colorScheme.outline.copy(alpha = if (darkTheme) 0.52f else 0.32f),
            handleMuted = colorScheme.onSurfaceVariant.copy(alpha = if (darkTheme) 0.48f else 0.24f),
            accentStrong = colorScheme.primary,
            accentSupport = colorScheme.tertiary,
            miniPlayerProgressTrack = colorScheme.secondary,
            miniPlayerProgressFill = colorScheme.primary
        )
    }
}

private val BasePlayerLiteTypography = Typography()

val PlayerLiteTypography = Typography(
    displayLarge = BasePlayerLiteTypography.displayLarge.withPlayerLiteFont(),
    displayMedium = BasePlayerLiteTypography.displayMedium.withPlayerLiteFont(),
    displaySmall = BasePlayerLiteTypography.displaySmall.withPlayerLiteFont(),
    headlineLarge = BasePlayerLiteTypography.headlineLarge.withPlayerLiteFont(),
    headlineMedium = BasePlayerLiteTypography.headlineMedium.withPlayerLiteFont(fontWeight = FontWeight.Bold),
    headlineSmall = BasePlayerLiteTypography.headlineSmall.withPlayerLiteFont(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = BasePlayerLiteTypography.titleLarge.withPlayerLiteFont(fontWeight = FontWeight.SemiBold),
    titleMedium = BasePlayerLiteTypography.titleMedium.withPlayerLiteFont(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    titleSmall = BasePlayerLiteTypography.titleSmall.withPlayerLiteFont(fontWeight = FontWeight.SemiBold),
    bodyLarge = BasePlayerLiteTypography.bodyLarge.withPlayerLiteFont(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = BasePlayerLiteTypography.bodyMedium.withPlayerLiteFont(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = BasePlayerLiteTypography.bodySmall.withPlayerLiteFont(),
    labelLarge = BasePlayerLiteTypography.labelLarge.withPlayerLiteFont(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelMedium = BasePlayerLiteTypography.labelMedium.withPlayerLiteFont(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = BasePlayerLiteTypography.labelSmall.withPlayerLiteFont()
)

val LocalPlayerLiteVisualTokens = staticCompositionLocalOf {
    PlayerLiteThemeContract.visualTokens(
        darkTheme = false,
        colorScheme = PlayerLiteThemeContract.colorScheme(darkTheme = false)
    )
}

object PlayerLiteVisualTheme {
    val colors: PlayerLiteVisualTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalPlayerLiteVisualTokens.current
}

@Composable
fun PlayerLiteDesignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    brandPalettes: PlayerLiteBrandPalettes = PlayerLiteThemeContract.DefaultBrandPalettes,
    typography: Typography = PlayerLiteTypography,
    content: @Composable () -> Unit
) {
    val colorScheme = PlayerLiteThemeContract.colorScheme(
        darkTheme = darkTheme,
        brandPalettes = brandPalettes
    )
    val visualTokens = PlayerLiteThemeContract.visualTokens(
        darkTheme = darkTheme,
        colorScheme = colorScheme
    )

    CompositionLocalProvider(LocalPlayerLiteVisualTokens provides visualTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

private fun PlayerLiteBrandPalette.toLightColorScheme(): ColorScheme {
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        secondary = secondary,
        onSecondary = Color.White,
        tertiary = tertiary,
        onTertiary = Color.White,
        background = neutral,
        onBackground = onSurface,
        surface = Color.White,
        onSurface = onSurface,
        surfaceVariant = neutralVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        error = error,
        onError = Color.White
    )
}

private fun TextStyle.withPlayerLiteFont(
    fontWeight: FontWeight? = this.fontWeight,
    fontSize: TextUnit = this.fontSize,
    lineHeight: TextUnit = this.lineHeight,
    letterSpacing: TextUnit = this.letterSpacing
): TextStyle {
    return copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = fontWeight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing
    )
}

private fun PlayerLiteBrandPalette.toDarkColorScheme(): ColorScheme {
    return darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF310909),
        secondary = secondary,
        onSecondary = Color(0xFF1A1A1A),
        tertiary = tertiary,
        onTertiary = Color(0xFF032027),
        background = neutral,
        onBackground = onSurface,
        surface = neutralVariant,
        onSurface = onSurface,
        surfaceVariant = neutralStrong,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        error = error,
        onError = Color(0xFF690005)
    )
}
