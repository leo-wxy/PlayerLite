package com.wxy.playerlite.playback.orchestrator

import android.util.Log
import androidx.media3.common.C

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

    fun playSelectedAudio(startPositionMs: Long = C.TIME_UNSET): Boolean {
        return playbackSynchronizer.syncQueueToPlaybackProcess(
            playWhenReady = true,
            startPositionMs = startPositionMs
        )
    }

    fun pausePlayback(): Boolean {
        serviceController.connectIfNeeded()
        val accepted = serviceController.pause()
        if (!accepted) {
            runtime.setStatusText("暂停失败：后台播放进程未连接")
        }
        return accepted
    }

    fun resumePlayback(startPositionMs: Long = C.TIME_UNSET): Boolean {
        val queueSize = runtime.playbackQueueItems().size
        if (startPositionMs != C.TIME_UNSET && queueSize > 0) {
            safeLogI(
                "resumePlayback: resync local queue startPositionMs=$startPositionMs, queueSize=$queueSize"
            )
            return playbackSynchronizer.syncQueueToPlaybackProcess(
                playWhenReady = true,
                startPositionMs = startPositionMs,
                requirePlaybackServiceStart = true
            )
        }
        val snapshot = serviceController.currentSnapshot()
        val hasRemoteCurrentItem = !snapshot?.currentMediaId.isNullOrBlank() ||
            !snapshot?.currentPlayable?.id.isNullOrBlank()
        safeLogI(
            "resumePlayback: fallback startPositionMs=$startPositionMs, queueSize=$queueSize, hasRemoteCurrentItem=$hasRemoteCurrentItem, remoteCurrentMediaId=${snapshot?.currentMediaId}, remotePositionMs=${snapshot?.currentPositionMs}"
        )
        if (!hasRemoteCurrentItem && queueSize > 0) {
            safeLogI(
                "resumePlayback: remote queue missing, syncing local queue startPositionMs=$startPositionMs"
            )
            return playbackSynchronizer.syncQueueToPlaybackProcess(
                playWhenReady = true,
                startPositionMs = startPositionMs,
                requirePlaybackServiceStart = true
            )
        }
        safeLogI(
            "resumePlayback: using remote play() currentMediaId=${snapshot?.currentMediaId}, startPositionMs=$startPositionMs"
        )
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

    private companion object {
        private const val TAG = "PlaybackTransport"
    }

    private fun safeLogI(message: String) {
        runCatching { Log.i(TAG, message) }
    }
}
