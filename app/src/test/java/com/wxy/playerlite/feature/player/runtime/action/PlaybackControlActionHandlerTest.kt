package com.wxy.playerlite.feature.player.runtime.action

import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.runtime.PlaybackCoordinator
import com.wxy.playerlite.player.AudioMeta
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.INativePlayer
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.player.source.IPlaysource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControlActionHandlerTest {
    @Test
    fun applySeek_rejectsWhenSourceDoesNotSupportSeek() {
        val fakePlayer = FakeNativePlayer()
        val coordinator = PlaybackCoordinator(
            player = fakePlayer,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
        var state = PlayerUiState(
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            isSeekSupported = false,
            durationMs = 10_000L
        )
        val handler = PlaybackControlActionHandler(
            playbackCoordinator = coordinator,
            getUiState = { state },
            setUiState = { next -> state = next },
            refreshPlaybackState = {},
            formatDuration = { value -> value.toString() }
        )

        handler.applySeek(3_000L)

        assertFalse(fakePlayer.seekInvoked)
        assertEquals("Current source does not support seek", state.statusText)
    }

    @Test
    fun applySeek_dispatchesToPlayerWhenSeekSupported() {
        val fakePlayer = FakeNativePlayer()
        val coordinator = PlaybackCoordinator(
            player = fakePlayer,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
        var state = PlayerUiState(
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            isSeekSupported = true,
            durationMs = 8_000L
        )
        val handler = PlaybackControlActionHandler(
            playbackCoordinator = coordinator,
            getUiState = { state },
            setUiState = { next -> state = next },
            refreshPlaybackState = {},
            formatDuration = { value -> value.toString() }
        )

        handler.applySeek(3_500L)

        assertTrue(fakePlayer.seekInvoked)
        assertEquals(3_500L, fakePlayer.lastSeekPositionMs)
        assertEquals(3_500L, state.seekPositionMs)
    }

    private class FakeNativePlayer : INativePlayer {
        var seekInvoked: Boolean = false
        var lastSeekPositionMs: Long = -1L

        override fun setProgressListener(listener: ((Long) -> Unit)?) = Unit
        override fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?) = Unit
        override fun setPlaybackSpeed(speed: Float): Int = 0
        override fun playFromSource(source: IPlaysource): Int = 0
        override fun pause(): Int = 0
        override fun resume(): Int = 0

        override fun seek(positionMs: Long): Int {
            seekInvoked = true
            lastSeekPositionMs = positionMs
            return 0
        }

        override fun getDurationFromSource(source: IPlaysource): Long = 0L
        override fun loadAudioMetaFromSource(source: IPlaysource): AudioMeta {
            return AudioMeta("-", 0, 0, 0L, 0L)
        }

        override fun loadAudioMetaDisplayFromSource(source: IPlaysource): AudioMetaDisplay {
            return AudioMetaDisplay("-", "-", "-", "-", 0L)
        }

        override fun playbackState(): Int = AUDIO_TRACK_PLAYSTATE_PLAYING
        override fun stop() = Unit
        override fun close() = Unit
        override fun lastError(): String = "fake"
    }
}
