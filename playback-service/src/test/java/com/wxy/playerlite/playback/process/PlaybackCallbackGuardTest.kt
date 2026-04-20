package com.wxy.playerlite.playback.process

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCallbackGuardTest {
    @Test
    fun shouldIgnorePlaybackCallbackWhenCurrentTrackHasChanged() {
        assertTrue(
            shouldIgnorePlaybackCallback(
                callbackTrackId = "track-old",
                currentTrackId = "track-new",
                callbackGeneration = 1L,
                currentGeneration = 1L
            )
        )
    }

    @Test
    fun shouldHandlePlaybackCallbackWhenTrackIsStillCurrent() {
        assertFalse(
            shouldIgnorePlaybackCallback(
                callbackTrackId = "track-1",
                currentTrackId = "track-1",
                callbackGeneration = 3L,
                currentGeneration = 3L
            )
        )
    }

    @Test
    fun shouldIgnorePlaybackCallbackWhenGenerationHasAdvanced() {
        assertTrue(
            shouldIgnorePlaybackCallback(
                callbackTrackId = "track-1",
                currentTrackId = "track-1",
                callbackGeneration = 2L,
                currentGeneration = 3L
            )
        )
    }
}
