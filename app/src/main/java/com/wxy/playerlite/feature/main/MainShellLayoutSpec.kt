package com.wxy.playerlite.feature.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object MainShellLayoutSpec {
    fun homeContentPadding(
        mode: HomeSurfaceMode,
        topInset: Dp
    ): PaddingValues {
        return when (mode) {
            HomeSurfaceMode.OVERVIEW -> PaddingValues(
                top = topInset
            )

            HomeSurfaceMode.PLAYER_EXPANDED -> PaddingValues(
                top = topInset,
                bottom = 0.dp
            )
        }
    }
}
