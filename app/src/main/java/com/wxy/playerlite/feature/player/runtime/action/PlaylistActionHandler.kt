package com.wxy.playerlite.feature.player.runtime.action

import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.runtime.PlaylistSessionCoordinator
import com.wxy.playerlite.feature.player.runtime.PreparedSourceSession

internal class PlaylistActionHandler(
    private val playlistSession: PlaylistSessionCoordinator,
    private val sourceSession: PreparedSourceSession,
    private val getUiState: () -> PlayerUiState,
    private val setUiState: (PlayerUiState) -> Unit,
    private val syncSelectionFromPlaylist: () -> Unit,
    private val switchToPlaylistIndex: (Int) -> Unit,
    private val prepareActiveItem: () -> Unit,
    private val stopPlaybackOnly: (Boolean) -> Unit,
    private val releaseSelectedSource: () -> Unit,
    private val resetAudioMetaState: () -> Unit
) {
    fun selectPlaylistItem(index: Int) {
        if (!playlistSession.containsIndex(index)) {
            return
        }

        val target = playlistSession.itemAt(index) ?: return
        val isSamePrepared =
            playlistSession.activeIndex == index && sourceSession.isPreparedFor(target.id)
        if (isSamePrepared) {
            setUiState(getUiState().copy(showPlaylistSheet = false))
            return
        }

        switchToPlaylistIndex(index)
        setUiState(getUiState().copy(showPlaylistSheet = false))
    }

    fun removePlaylistItem(index: Int) {
        val target = playlistSession.itemAt(index) ?: return
        val removingActive = index == playlistSession.activeIndex

        playlistSession.removeAt(index)
        syncSelectionFromPlaylist()

        if (playlistSession.items.isEmpty()) {
            stopPlaybackOnly(false)
            releaseSelectedSource()
            resetAudioMetaState()
            setUiState(
                getUiState().copy(
                    statusText = "播放列表已清空",
                    showPlaylistSheet = false
                )
            )
            return
        }

        if (removingActive) {
            sourceSession.setAutoPlayWhenPrepared(false)
            setUiState(
                getUiState().copy(
                    statusText = "已移除当前项"
                )
            )
            prepareActiveItem()
            return
        }

        setUiState(getUiState().copy(statusText = "已移除: ${target.displayName}"))
    }

    fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) {
            return
        }
        if (!playlistSession.containsIndex(fromIndex) || !playlistSession.containsIndex(toIndex)) {
            return
        }

        playlistSession.moveItem(fromIndex, toIndex)
        syncSelectionFromPlaylist()
    }

    fun skipToPreviousTrack() {
        val targetIndex = playlistSession.activeIndex - 1
        if (!playlistSession.containsIndex(targetIndex)) {
            setUiState(getUiState().copy(statusText = "已是第一首"))
            return
        }
        switchToPlaylistIndex(targetIndex)
    }

    fun skipToNextTrack() {
        val targetIndex = playlistSession.activeIndex + 1
        if (!playlistSession.containsIndex(targetIndex)) {
            setUiState(getUiState().copy(statusText = "已是最后一首"))
            return
        }
        switchToPlaylistIndex(targetIndex)
    }
}
