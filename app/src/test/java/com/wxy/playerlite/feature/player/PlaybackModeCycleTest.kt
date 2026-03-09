package com.wxy.playerlite.feature.player

import com.wxy.playerlite.playback.model.PlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackModeCycleTest {
    @Test
    fun nextPlaybackMode_cyclesInConfiguredOrder() {
        assertEquals(PlaybackMode.SINGLE_LOOP, PlaybackMode.LIST_LOOP.nextPlaybackMode())
        assertEquals(PlaybackMode.SHUFFLE, PlaybackMode.SINGLE_LOOP.nextPlaybackMode())
        assertEquals(PlaybackMode.LIST_LOOP, PlaybackMode.SHUFFLE.nextPlaybackMode())
    }
}
