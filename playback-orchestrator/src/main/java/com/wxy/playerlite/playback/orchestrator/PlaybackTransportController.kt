package com.wxy.playerlite.playback.orchestrator

class PlaybackTransportController(
    private val runtime: PlaybackRuntimePort,
    private val serviceController: PlayerServiceController,
    private val playbackSynchronizer: PlaybackServiceSynchronizer
) {
    fun seekTo(positionMs: Long): Boolean {
        serviceController.connectIfNeeded()
        val accepted = serviceController.seekTo(positionMs)
        if (!accepted) {
            runtime.setStatusText("后台播放进程未连接")
        }
        return accepted
    }

    fun skipToPreviousTrack(): Boolean {
        playbackSynchronizer.ensureRemoteQueueReadyForSkip()
        serviceController.ensurePlaybackServiceStartedForPlayback()
        serviceController.connectIfNeeded()
        val accepted = serviceController.seekToPreviousMediaItem()
        if (!accepted) {
            runtime.setStatusText("切换上一首失败：后台播放进程未连接")
        }
        return accepted
    }

    fun skipToNextTrack(): Boolean {
        playbackSynchronizer.ensureRemoteQueueReadyForSkip()
        serviceController.ensurePlaybackServiceStartedForPlayback()
        serviceController.connectIfNeeded()
        val accepted = serviceController.seekToNextMediaItem()
        if (!accepted) {
            runtime.setStatusText("切换下一首失败：后台播放进程未连接")
        }
        return accepted
    }

    fun playSelectedAudio(): Boolean {
        return playbackSynchronizer.syncQueueToPlaybackProcess(playWhenReady = true)
    }

    fun pausePlayback(): Boolean {
        serviceController.connectIfNeeded()
        val accepted = serviceController.pause()
        if (!accepted) {
            runtime.setStatusText("暂停失败：后台播放进程未连接")
        }
        return accepted
    }

    fun resumePlayback(): Boolean {
        serviceController.ensurePlaybackServiceStartedForPlayback()
        serviceController.connectIfNeeded()
        val accepted = serviceController.play()
        if (!accepted) {
            runtime.setStatusText("恢复失败：后台播放进程未连接")
        }
        return accepted
    }

    fun stopPlayback(): Boolean {
        return serviceController.stop()
    }

    fun clearCache(): Boolean {
        serviceController.connectIfNeeded()
        val accepted = serviceController.clearCache()
        runtime.setStatusText(
            if (accepted) {
                "已请求清理缓存"
            } else {
                "清理缓存请求失败：后台播放进程未连接"
            }
        )
        return accepted
    }
}
