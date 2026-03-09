package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.player.AudioMetaDisplay
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayerRuntimeProjectionTest {
    @Test
    fun updateRemotePlaybackState_updatesAudioMetaFromRemoteSnapshot() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())
        val audioMeta = AudioMetaDisplay(
            codec = "FLAC",
            sampleRate = "96000 Hz",
            channels = "2",
            bitRate = "Lossless",
            durationMs = 321_000L
        )

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 0L,
            durationMs = 321_000L,
            isSeekSupported = true,
            playbackSpeed = 2.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            playbackOutputInfo = null,
            audioMeta = audioMeta
        )

        val state = runtime.uiStateFlow.value
        assertEquals(2.0f, state.playbackSpeed, 0f)
        assertEquals(PlaybackMode.LIST_LOOP, state.playbackMode)
        assertEquals(audioMeta, state.audioMeta)
    }

    @Test
    fun updateRemotePlaybackState_clearsStaleAudioMetaWhenRemoteSnapshotHasNoMetadata() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 0L,
            durationMs = 321_000L,
            isSeekSupported = true,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            playbackOutputInfo = null,
            audioMeta = AudioMetaDisplay(
                codec = "AAC",
                sampleRate = "44100 Hz",
                channels = "2",
                bitRate = "256 kbps",
                durationMs = 321_000L
            )
        )

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 0L,
            durationMs = 0L,
            isSeekSupported = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = null,
            isProgressAdvancing = false,
            playbackOutputInfo = null,
            audioMeta = null
        )

        assertEquals("-", runtime.uiStateFlow.value.audioMeta.codec)
    }

    @Test
    fun updateRemotePlaybackState_keepsLocalPlaybackModeWhenRemoteHasNoCurrentMedia() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateLocalPlaybackMode(PlaybackMode.SHUFFLE)
        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 0L,
            durationMs = 0L,
            isSeekSupported = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = null,
            isProgressAdvancing = false,
            playbackOutputInfo = null,
            audioMeta = null
        )

        assertEquals(PlaybackMode.SHUFFLE, runtime.uiStateFlow.value.playbackMode)
    }
}
