package com.wxy.playerlite.feature.player.runtime.action

import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.runtime.PlaybackCoordinator

internal class PlaybackControlActionHandler(
    private val playbackCoordinator: PlaybackCoordinator,
    private val getUiState: () -> PlayerUiState,
    private val setUiState: (PlayerUiState) -> Unit,
    private val refreshPlaybackState: () -> Unit,
    private val formatDuration: (Long) -> String
) {
    fun pausePlayback() {
        val uiState = getUiState()
        if (uiState.playbackState == AUDIO_TRACK_PLAYSTATE_PAUSED) {
            setUiState(uiState.copy(statusText = "Already paused"))
            return
        }
        if (uiState.playbackState != AUDIO_TRACK_PLAYSTATE_PLAYING) {
            setUiState(uiState.copy(statusText = "Nothing is playing"))
            return
        }

        val code = playbackCoordinator.pause()
        if (code == 0) {
            setUiState(getUiState().copy(statusText = "Paused"))
            refreshPlaybackState()
        } else {
            setUiState(getUiState().copy(statusText = "Pause failed($code): ${playbackCoordinator.lastError()}"))
        }
    }

    fun resumePlayback() {
        val uiState = getUiState()
        if (uiState.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING) {
            setUiState(uiState.copy(statusText = "Already playing"))
            return
        }
        if (uiState.playbackState != AUDIO_TRACK_PLAYSTATE_PAUSED) {
            setUiState(uiState.copy(statusText = "Nothing is playing"))
            return
        }

        val code = playbackCoordinator.resume()
        if (code == 0) {
            setUiState(getUiState().copy(statusText = "Playing via native AudioTrack..."))
            refreshPlaybackState()
        } else {
            setUiState(getUiState().copy(statusText = "Resume failed($code): ${playbackCoordinator.lastError()}"))
        }
    }

    fun applySeek(targetMs: Long) {
        val uiState = getUiState()
        if (!uiState.isSeekSupported) {
            setUiState(uiState.copy(statusText = "Current source does not support seek"))
            return
        }
        if (uiState.playbackState != AUDIO_TRACK_PLAYSTATE_PLAYING) {
            setUiState(uiState.copy(statusText = "Seek is available while playing"))
            return
        }

        val clampedTargetMs = if (uiState.durationMs > 0L) {
            targetMs.coerceIn(0L, uiState.durationMs)
        } else {
            targetMs.coerceAtLeast(0L)
        }
        val code = playbackCoordinator.seek(clampedTargetMs)
        if (code == 0) {
            setUiState(
                getUiState().copy(
                    seekPositionMs = clampedTargetMs,
                    seekDragPositionMs = clampedTargetMs,
                    statusText = "Seeked to ${formatDuration(clampedTargetMs)}"
                )
            )
        } else {
            setUiState(getUiState().copy(statusText = "Seek failed($code): ${playbackCoordinator.lastError()}"))
        }
    }
}
