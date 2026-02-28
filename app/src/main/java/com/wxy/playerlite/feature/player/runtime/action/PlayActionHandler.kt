package com.wxy.playerlite.feature.player.runtime.action

import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.runtime.PlaybackCoordinator
import com.wxy.playerlite.feature.player.runtime.PlayerUiFormatter
import com.wxy.playerlite.feature.player.runtime.PlaylistSessionCoordinator
import com.wxy.playerlite.feature.player.runtime.PreparedSourceSession
import com.wxy.playerlite.player.source.IPlaysource

internal class PlayActionHandler(
    private val playlistSession: PlaylistSessionCoordinator,
    private val sourceSession: PreparedSourceSession,
    private val playbackCoordinator: PlaybackCoordinator,
    private val getUiState: () -> PlayerUiState,
    private val setUiState: (PlayerUiState) -> Unit,
    private val prepareActiveItem: () -> Unit,
    private val advanceToNextAfterCompletion: () -> Boolean,
    private val refreshPlaybackState: () -> Unit
) {
    fun playSelectedAudio() {
        val uiState = getUiState()
        val activeItem = playlistSession.activeItem
        if (!uiState.hasSelection || activeItem == null) {
            setUiState(uiState.copy(statusText = "Pick audio first"))
            return
        }

        if (uiState.isPreparing) {
            sourceSession.setAutoPlayWhenPrepared(true)
            setUiState(uiState.copy(statusText = "Wait for file preparation"))
            return
        }

        val source = sourceSession.currentSource()
        if (source == null || !sourceSession.isPreparedFor(activeItem.id)) {
            sourceSession.setAutoPlayWhenPrepared(true)
            prepareActiveItem()
            setUiState(getUiState().copy(statusText = "Preparing selected track..."))
            return
        }

        if (uiState.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING) {
            setUiState(uiState.copy(statusText = "Already playing"))
            return
        }

        if (uiState.playbackState == AUDIO_TRACK_PLAYSTATE_PAUSED) {
            setUiState(uiState.copy(statusText = "Paused. Tap Resume"))
            return
        }

        val sourceOpenCode = source.open()
        if (sourceOpenCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
            setUiState(getUiState().copy(statusText = "Source open failed(${sourceOpenCode.code})"))
            return
        }

        if (source.seek(0L, IPlaysource.SEEK_SET) < 0L) {
            setUiState(getUiState().copy(statusText = "Source rewind failed"))
            return
        }

        playbackCoordinator.launchPlay(
            source = source,
            onStarted = {
                setUiState(
                    getUiState().copy(
                        seekPositionMs = 0L,
                        seekDragPositionMs = 0L,
                        isSeekDragging = false,
                        statusText = "Playing via native AudioTrack..."
                    )
                )
            },
            onCompleted = { playCode ->
                if (playCode == 0) {
                    val latestState = getUiState()
                    setUiState(
                        latestState.copy(
                            seekPositionMs = latestState.durationMs,
                            seekDragPositionMs = latestState.durationMs
                        )
                    )
                    if (advanceToNextAfterCompletion()) {
                        return@launchPlay
                    }
                }

                setUiState(
                    getUiState().copy(
                        statusText = PlayerUiFormatter.formatPlaybackResult(
                            playCode = playCode,
                            lastError = playbackCoordinator.lastError()
                        )
                    )
                )
            },
            onFinally = {
                refreshPlaybackState()
            }
        )
    }
}
