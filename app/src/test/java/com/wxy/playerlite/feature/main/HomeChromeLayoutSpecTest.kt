package com.wxy.playerlite.feature.main

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeChromeLayoutSpecTest {
    @Test
    fun miniPlayer_shouldUseSofterButSmallerCornerThanBottomBar() {
        assertEquals(24.dp, HomeChromeLayoutSpec.bottomBarCornerRadius)
        assertEquals(14.dp, HomeChromeLayoutSpec.miniPlayerCornerRadius)
        assertTrue(
            "Expected minibar radius to be smaller than bottom tab radius",
            HomeChromeLayoutSpec.miniPlayerCornerRadius <
                HomeChromeLayoutSpec.bottomBarCornerRadius
        )
    }

    @Test
    fun bottomBar_shouldUseSlimmerHeight() {
        assertEquals(72.dp, HomeChromeLayoutSpec.bottomBarMinHeight)
    }

    @Test
    fun bottomBar_shouldReserveIndependentBottomClearanceForGestureHandle() {
        assertEquals(18.dp, HomeChromeLayoutSpec.bottomBarBottomClearance)
    }

    @Test
    fun miniPlayerProgressTrack_shouldStaySubtleAndTranslucent() {
        assertTrue(
            "Expected progress track background alpha to stay readable without becoming heavy, but was ${HomeChromeLayoutSpec.miniPlayerProgressTrackAlpha}",
            HomeChromeLayoutSpec.miniPlayerProgressTrackAlpha in 0.09f..0.12f
        )
    }

    @Test
    fun miniPlayerProgressTrack_shouldStaySlimButReadable() {
        assertEquals(4.dp, HomeChromeLayoutSpec.miniPlayerProgressTrackHeight)
        assertEquals(0.dp, HomeChromeLayoutSpec.miniPlayerProgressTrackOverlap)
    }

    @Test
    fun overviewScrollContent_shouldReserveExtraBottomSpaceForMiniPlayerAndTabBar() {
        assertEquals(
            HomeChromeLayoutSpec.bottomBarOverlayHeight + 4.dp,
            HomeChromeLayoutSpec.homeMiniPlayerBottomSpacing
        )
        assertTrue(
            HomeChromeLayoutSpec.homeOverviewScrollBottomPadding >
                HomeChromeLayoutSpec.homeMiniPlayerBottomSpacing
        )
    }

    @Test
    fun userCenterScrollContent_shouldReserveBottomSpaceForFloatingTabBar() {
        assertTrue(
            HomeChromeLayoutSpec.userCenterScrollBottomPadding >
                HomeChromeLayoutSpec.bottomBarMinHeight
        )
    }
}
