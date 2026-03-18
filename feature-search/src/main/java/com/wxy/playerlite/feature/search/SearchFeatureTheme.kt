package com.wxy.playerlite.feature.search

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.wxy.playerlite.designsystem.theme.PlayerLiteDesignTheme

@Composable
internal fun SearchFeatureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    PlayerLiteDesignTheme(
        darkTheme = darkTheme,
        content = content
    )
}
