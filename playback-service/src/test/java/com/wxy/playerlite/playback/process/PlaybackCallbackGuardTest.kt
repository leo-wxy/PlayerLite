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
                currentTrackId = "track-new"
            )
        )
    }

    @Test
    fun shouldHandlePlaybackCallbackWhenTrackIsStillCurrent() {
        assertFalse(
            shouldIgnorePlaybackCallback(
                callbackTrackId = "track-1",
                currentTrackId = "track-1"
            )
        )
    }
}
