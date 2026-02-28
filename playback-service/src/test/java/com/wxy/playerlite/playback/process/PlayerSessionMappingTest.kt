package com.wxy.playerlite.playback.process

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSessionMappingTest {
    @Test
    fun media3PlaybackState_withoutMediaItem_isIdle() {
        assertEquals(
            Player.STATE_IDLE,
            PlayerSessionMapping.media3PlaybackState(
                nativePlaybackState = PLAYBACK_STATE_PLAYING,
                hasMediaItem = false,
                playWhenReady = false,
                isPreparing = false
            )
        )
    }

    @Test
    fun media3PlaybackState_mapsPlayingAndPausedToReady() {
        assertEquals(
            Player.STATE_READY,
            PlayerSessionMapping.media3PlaybackState(
                nativePlaybackState = PLAYBACK_STATE_PLAYING,
                hasMediaItem = true,
                playWhenReady = true,
                isPreparing = false
            )
        )
        assertEquals(
            Player.STATE_READY,
            PlayerSessionMapping.media3PlaybackState(
                nativePlaybackState = PLAYBACK_STATE_PAUSED,
                hasMediaItem = true,
                playWhenReady = false,
                isPreparing = false
            )
        )
        assertEquals(
            Player.STATE_IDLE,
            PlayerSessionMapping.media3PlaybackState(
                nativePlaybackState = PLAYBACK_STATE_STOPPED,
                hasMediaItem = true,
                playWhenReady = false,
                isPreparing = false
            )
        )
        assertEquals(
            Player.STATE_BUFFERING,
            PlayerSessionMapping.media3PlaybackState(
                nativePlaybackState = PLAYBACK_STATE_STOPPED,
                hasMediaItem = true,
                playWhenReady = true,
                isPreparing = false
            )
        )
        assertEquals(
            Player.STATE_BUFFERING,
            PlayerSessionMapping.media3PlaybackState(
                nativePlaybackState = PLAYBACK_STATE_STOPPED,
                hasMediaItem = true,
                playWhenReady = false,
                isPreparing = true
            )
        )
    }

    @Test
    fun isPlaying_onlyTrueForPlayingState() {
        assertTrue(PlayerSessionMapping.isPlaying(PLAYBACK_STATE_PLAYING))
        assertFalse(PlayerSessionMapping.isPlaying(PLAYBACK_STATE_PAUSED))
        assertFalse(PlayerSessionMapping.isPlaying(PLAYBACK_STATE_STOPPED))
    }
}
