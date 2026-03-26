package com.wxy.playerlite.playback.orchestrator

import com.wxy.playerlite.playback.model.PlaybackMode

class PlaybackQueueController(
    private val runtime: PlaybackRuntimePort,
    private val serviceController: PlayerServiceController,
    private val playbackSynchronizer: PlaybackServiceSynchronizer
) {
    fun updatePlaybackMode(
        playbackMode: PlaybackMode,
        startPositionMs: Long
    ): Boolean {
        val shouldContinue = playbackSynchronizer.shouldContinuePlayback()
        runtime.updateLocalPlaybackMode(playbackMode)
        return playbackSynchronizer.syncQueueToPlaybackProcess(
            playWhenReady = shouldContinue,
            startPositionMs = startPositionMs
        )
    }

    fun selectPlaylistItem(index: Int): Boolean {
        runtime.selectPlaylistItem(index)
        return playbackSynchronizer.syncQueueToPlaybackProcess(playWhenReady = true)
    }

    fun removePlaylistItem(
        index: Int,
        removedActiveWhilePlayingOrPreparing: Boolean
    ): Boolean {
        runtime.removePlaylistItem(index)
        if (runtime.playbackQueueItems().isEmpty()) {
            return serviceController.stop()
        }
        return playbackSynchronizer.syncQueueToPlaybackProcess(
            playWhenReady = removedActiveWhilePlayingOrPreparing
        )
    }

    fun clearPlaylist(): Boolean {
        runtime.clearPlaylist()
        return if (runtime.playbackQueueItems().isEmpty()) {
            serviceController.stop()
        } else {
            true
        }
    }

    fun movePlaylistItem(fromIndex: Int, toIndex: Int): Boolean {
        val shouldContinue = playbackSynchronizer.shouldContinuePlayback()
        runtime.movePlaylistItem(fromIndex, toIndex)
        return playbackSynchronizer.syncQueueToPlaybackProcess(playWhenReady = shouldContinue)
    }
}
