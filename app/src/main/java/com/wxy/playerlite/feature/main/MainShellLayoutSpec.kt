package com.wxy.playerlite.feature.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object MainShellLayoutSpec {
    private val HomeOverviewBottomInset = 92.dp

    fun homeContentPadding(
        mode: HomeSurfaceMode,
        topInset: Dp
    ): PaddingValues {
        return when (mode) {
            HomeSurfaceMode.OVERVIEW -> PaddingValues(
                top = topInset,
                bottom = HomeOverviewBottomInset
            )

            HomeSurfaceMode.PLAYER_EXPANDED -> PaddingValues(
                top = topInset,
                bottom = 0.dp
            )
        }
    }
}
