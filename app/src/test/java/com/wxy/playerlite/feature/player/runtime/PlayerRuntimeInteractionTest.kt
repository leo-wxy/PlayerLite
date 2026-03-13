package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.playback.model.PlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayerRuntimeInteractionTest {
    @Test
    fun updateLocalPlaybackMode_shouldPublishNewModeImmediately() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateLocalPlaybackMode(PlaybackMode.SINGLE_LOOP)

        assertEquals(PlaybackMode.SINGLE_LOOP, runtime.uiStateFlow.value.playbackMode)
    }

    @Test
    fun finishSeekDrag_shouldClampDraggedPositionIntoKnownDuration() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            positionMs = 12_000L,
            durationMs = 120_000L,
            isSeekSupported = true,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            playbackOutputInfo = null,
            audioMeta = null
        )

        runtime.onSeekValueChange(180_000L)
        assertTrue(runtime.uiStateFlow.value.isSeekDragging)
        assertEquals(180_000L, runtime.uiStateFlow.value.seekDragPositionMs)

        runtime.finishSeekDrag()

        val state = runtime.uiStateFlow.value
        assertFalse(state.isSeekDragging)
        assertEquals(120_000L, state.seekPositionMs)
        assertEquals(120_000L, state.seekDragPositionMs)
        assertEquals(120_000L, state.displayedSeekMs)
    }

    @Test
    fun onSeekValueChange_shouldIgnoreUnsupportedSeekSources() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            positionMs = 8_000L,
            durationMs = 0L,
            isSeekSupported = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            playbackOutputInfo = null,
            audioMeta = null
        )

        runtime.onSeekValueChange(30_000L)

        val state = runtime.uiStateFlow.value
        assertFalse(state.isSeekDragging)
        assertEquals(8_000L, state.seekDragPositionMs)
        assertEquals(8_000L, state.seekPositionMs)
    }
}
