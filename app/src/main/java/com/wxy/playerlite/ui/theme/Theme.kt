package com.wxy.playerlite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.wxy.playerlite.designsystem.theme.PlayerLiteDesignTheme

@Composable
fun PlayerLiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    PlayerLiteDesignTheme(
        darkTheme = darkTheme,
        content = content
    )
}
