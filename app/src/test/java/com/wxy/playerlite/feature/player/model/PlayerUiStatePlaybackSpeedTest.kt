package com.wxy.playerlite.feature.player.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerUiStatePlaybackSpeedTest {
    @Test
    fun withPlaybackSpeed_updatesDisplayedSpeedUsingSupportedStep() {
        val updated = PlayerUiState().withPlaybackSpeed(1.96f)

        assertEquals(2.0f, updated.playbackSpeed, 0f)
    }
}
