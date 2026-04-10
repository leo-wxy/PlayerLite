package com.wxy.playerlite.feature.search

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.wxy.playerlite.designsystem.theme.PlayerLiteDesignTheme
import com.wxy.playerlite.designsystem.theme.PlayerLiteThemeContract
import com.wxy.playerlite.designsystem.theme.PlayerLiteVisualTokens

@Immutable
internal data class SearchFeatureVisualTokens(
    val pageBackgroundStart: Color,
    val pageBackgroundEnd: Color,
    val panel: Color,
    val panelMuted: Color,
    val divider: Color,
    val accent: Color,
    val accentMuted: Color,
    val accentSoft: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val rankSecondary: Color,
    val rankTertiary: Color
)

private val LocalSearchFeatureVisualTokens = staticCompositionLocalOf {
    val colorScheme = PlayerLiteThemeContract.colorScheme(darkTheme = false)
    val sharedTokens = PlayerLiteThemeContract.visualTokens(
        darkTheme = false,
        colorScheme = colorScheme
    )
    sharedTokens.toSearchFeatureVisualTokens(colorScheme = colorScheme, darkTheme = false)
}

internal object SearchFeatureVisualTheme {
    val colors: SearchFeatureVisualTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalSearchFeatureVisualTokens.current
}

@Composable
internal fun SearchFeatureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = PlayerLiteThemeContract.colorScheme(darkTheme = darkTheme)
    val sharedTokens = PlayerLiteThemeContract.visualTokens(
        darkTheme = darkTheme,
        colorScheme = colorScheme
    )
    val searchTokens = sharedTokens.toSearchFeatureVisualTokens(
        colorScheme = colorScheme,
        darkTheme = darkTheme
    )

    CompositionLocalProvider(LocalSearchFeatureVisualTokens provides searchTokens) {
        PlayerLiteDesignTheme(
            darkTheme = darkTheme,
            content = content
        )
    }
}

private fun PlayerLiteVisualTokens.toSearchFeatureVisualTokens(
    colorScheme: ColorScheme,
    darkTheme: Boolean
): SearchFeatureVisualTokens {
    return SearchFeatureVisualTokens(
        pageBackgroundStart = lerp(canvas, surfaceMuted, if (darkTheme) 0.34f else 0.72f),
        pageBackgroundEnd = canvas,
        panel = surfaceRaised,
        panelMuted = lerp(surfacePrimary, surfaceMuted, if (darkTheme) 0.24f else 0.38f),
        divider = dividerSubtle,
        accent = accentStrong,
        accentMuted = accentStrong.copy(alpha = if (darkTheme) 0.88f else 0.84f),
        accentSoft = colorScheme.primary.copy(alpha = if (darkTheme) 0.18f else 0.1f),
        textSecondary = textSecondary,
        textMuted = textMuted,
        rankSecondary = colorScheme.tertiary.copy(alpha = if (darkTheme) 0.96f else 0.92f),
        rankTertiary = colorScheme.primary.copy(alpha = if (darkTheme) 0.8f else 0.78f)
    )
}
