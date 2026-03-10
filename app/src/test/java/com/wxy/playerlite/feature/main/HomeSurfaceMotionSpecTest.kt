package com.wxy.playerlite.feature.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSurfaceMotionSpecTest {
    @Test
    fun overviewTargets_shouldKeepOverviewFullyVisibleAndPlayerPreparedOffscreen() {
        val targets = HomeSurfaceMotionSpec.targetsFor(HomeSurfaceMode.OVERVIEW)

        assertEquals(1f, targets.overviewAlpha)
        assertEquals(1f, targets.overviewScale)
        assertEquals(0f, targets.overviewOffsetFraction)
        assertEquals(0f, targets.playerAlpha)
        assertTrue(targets.playerScale < 1f)
        assertTrue(targets.playerOffsetFraction > 0f)
    }

    @Test
    fun playerExpandedTargets_shouldPromotePlayerAndRetireOverview() {
        val targets = HomeSurfaceMotionSpec.targetsFor(HomeSurfaceMode.PLAYER_EXPANDED)

        assertEquals(0f, targets.overviewAlpha)
        assertTrue(targets.overviewScale < 1f)
        assertTrue(targets.overviewOffsetFraction < 0f)
        assertEquals(1f, targets.playerAlpha)
        assertEquals(1f, targets.playerScale)
        assertEquals(0f, targets.playerOffsetFraction)
    }
}
