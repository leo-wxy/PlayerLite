package com.wxy.playerlite.feature.player.runtime.action

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.runtime.PlaylistSessionCoordinator
import com.wxy.playerlite.feature.player.runtime.PreparedSourceSession
import com.wxy.playerlite.feature.player.runtime.PreparationResult
import com.wxy.playerlite.feature.player.runtime.TrackPreparationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class PreparationActionHandler(
    private val scope: CoroutineScope,
    private val playlistSession: PlaylistSessionCoordinator,
    private val sourceSession: PreparedSourceSession,
    private val trackPreparationCoordinator: TrackPreparationCoordinator,
    private val getUiState: () -> PlayerUiState,
    private val setUiState: (PlayerUiState) -> Unit,
    private val stopPlaybackOnly: (Boolean) -> Unit,
    private val releaseSelectedSource: () -> Unit,
    private val resetAudioMetaState: () -> Unit,
    private val syncSelectionFromPlaylist: () -> Unit,
    private val removeInvalidActiveItem: (PlaylistItem, String) -> Unit,
    private val playSelectedAudio: () -> Unit
) {
    private var prepareJob: Job? = null

    fun prepareActiveItem(stopPlayback: Boolean = true) {
        val activeItem = playlistSession.activeItem
        if (activeItem == null) {
            releaseSelectedSource()
            resetAudioMetaState()
            setUiState(getUiState().copy(statusText = "Pick a local audio file, then tap Play"))
            return
        }

        prepareJob?.cancel()
        prepareJob = scope.launch {
            if (stopPlayback) {
                stopPlaybackOnly(false)
            }
            releaseSelectedSource()
            setUiState(
                getUiState().copy(
                    isPreparing = true,
                    statusText = "Preparing file...",
                    playbackOutputInfo = null
                )
            )

            try {
                when (val preparation = trackPreparationCoordinator.prepare(activeItem)) {
                    is PreparationResult.Invalid -> {
                        removeInvalidActiveItem(activeItem, preparation.message)
                        return@launch
                    }

                    is PreparationResult.Ready -> {
                        sourceSession.markPrepared(activeItem.id, preparation.source)
                        val mediaMeta = preparation.mediaMeta
                        setUiState(
                            getUiState().copy(
                                seekPositionMs = 0L,
                                seekDragPositionMs = 0L,
                                isSeekDragging = false,
                                audioMeta = mediaMeta,
                                durationMs = if (mediaMeta.durationMs > 0L) mediaMeta.durationMs else 0L,
                                statusText = if (mediaMeta.durationMs > 0L) {
                                    "Ready to play"
                                } else {
                                    "Ready to play (duration unavailable)"
                                }
                            )
                        )
                    }
                }
            } finally {
                setUiState(getUiState().copy(isPreparing = false))
            }

            if (sourceSession.consumeAutoPlayIfPrepared(activeItem.id)) {
                playSelectedAudio()
            }
        }
    }

    fun cancelPreparation() {
        prepareJob?.cancel()
        prepareJob = null
        setUiState(getUiState().copy(isPreparing = false))
    }

    fun addPickedUri(
        displayName: String,
        item: PlaylistItem
    ) {
        playlistSession.addItem(item, makeActive = true)
        syncSelectionFromPlaylist()
        setUiState(getUiState().copy(statusText = "Added to playlist: $displayName"))
        sourceSession.setAutoPlayWhenPrepared(false)
        prepareActiveItem()
    }
}
