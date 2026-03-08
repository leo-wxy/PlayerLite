package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProgressEstimatorTest {
    @Test
    fun estimate_advancesPositionUsingPlaybackSpeed() {
        val estimated = PlaybackProgressEstimator.estimatePositionMs(
            anchorPositionMs = 1_000L,
            anchorElapsedRealtimeMs = 10_000L,
            nowElapsedRealtimeMs = 10_250L,
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            playbackSpeed = 2.0f,
            durationMs = 5_000L
        )

        assertEquals(1_500L, estimated)
    }

    @Test
    fun estimate_keepsAnchorWhenNotPlaying() {
        val estimated = PlaybackProgressEstimator.estimatePositionMs(
            anchorPositionMs = 1_000L,
            anchorElapsedRealtimeMs = 10_000L,
            nowElapsedRealtimeMs = 10_500L,
            playbackState = AUDIO_TRACK_PLAYSTATE_PAUSED,
            playbackSpeed = 2.0f,
            durationMs = 5_000L
        )

        assertEquals(1_000L, estimated)
    }
}
