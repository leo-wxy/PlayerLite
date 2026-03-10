package com.wxy.playerlite.feature.main

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MainShellLayoutSpecTest {
    @Test
    fun overviewPadding_shouldReserveStableBottomSpaceForTabBar() {
        val padding = MainShellLayoutSpec.homeContentPadding(
            mode = HomeSurfaceMode.OVERVIEW,
            topInset = 16.dp
        )

        assertEquals(16.dp, padding.calculateTopPadding())
        assertEquals(92.dp, padding.calculateBottomPadding())
    }

    @Test
    fun playerExpandedPadding_shouldDropBottomInsetSoContentDoesNotRelayoutWithBarExit() {
        val padding = MainShellLayoutSpec.homeContentPadding(
            mode = HomeSurfaceMode.PLAYER_EXPANDED,
            topInset = 16.dp
        )

        assertEquals(16.dp, padding.calculateTopPadding())
        assertEquals(0.dp, padding.calculateBottomPadding())
    }
}
