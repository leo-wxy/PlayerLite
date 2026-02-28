package com.wxy.playerlite.feature.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.lifecycle.AndroidViewModel
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.runtime.PlayerRuntimeRegistry
import com.wxy.playerlite.playback.client.PlayerServiceBridge
import com.wxy.playerlite.playback.client.RemotePlaybackSnapshot
import com.wxy.playerlite.playback.model.MusicInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val runtime = PlayerRuntimeRegistry.get(appContext)
    private val serviceBridge = PlayerServiceBridge(appContext) { errorMessage ->
        runtime.setStatusText(errorMessage)
        Log.w(TAG, errorMessage)
    }
    private val remoteSyncJob: Job

    val uiStateFlow: StateFlow<PlayerUiState> = runtime.uiStateFlow

    init {
        serviceBridge.ensureServiceStarted()
        serviceBridge.connectIfNeeded()
        remoteSyncJob = viewModelScope.launch {
            while (isActive) {
                syncRemotePlaybackState()
                delay(250L)
            }
        }
    }

    fun onAudioPicked(uri: Uri?) {
        runtime.onAudioPicked(uri)
    }

    fun onTogglePlaylistSheet() {
        runtime.onTogglePlaylistSheet()
    }

    fun onDismissPlaylistSheet() {
        runtime.onDismissPlaylistSheet()
    }

    fun onSeekValueChange(value: Long) {
        runtime.onSeekValueChange(value)
    }

    fun onSeekFinished() {
        val target = uiStateFlow.value.seekDragPositionMs
        runtime.finishSeekDrag()
        if (!serviceBridge.seekTo(target)) {
            runtime.setStatusText("后台播放进程未连接")
        }
    }

    fun onHostStop() {
        runtime.onHostStop()
    }

    fun selectPlaylistItem(index: Int) {
        val shouldContinue = uiStateFlow.value.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING
        runtime.selectPlaylistItem(index)
        syncQueueToPlaybackProcess(playWhenReady = shouldContinue)
    }

    fun removePlaylistItem(index: Int) {
        val previous = uiStateFlow.value
        runtime.removePlaylistItem(index)
        val latest = uiStateFlow.value
        if (!latest.hasSelection) {
            serviceBridge.stop()
            return
        }
        val shouldContinue = previous.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING && index == previous.activePlaylistIndex
        syncQueueToPlaybackProcess(playWhenReady = shouldContinue)
    }

    fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        val shouldContinue = uiStateFlow.value.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING
        runtime.movePlaylistItem(fromIndex, toIndex)
        syncQueueToPlaybackProcess(playWhenReady = shouldContinue)
    }

    fun skipToPreviousTrack() {
        val shouldContinue = uiStateFlow.value.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING
        runtime.skipToPreviousTrack()
        syncQueueToPlaybackProcess(playWhenReady = shouldContinue)
    }

    fun skipToNextTrack() {
        val shouldContinue = uiStateFlow.value.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING
        runtime.skipToNextTrack()
        syncQueueToPlaybackProcess(playWhenReady = shouldContinue)
    }

    fun playSelectedAudio() {
        syncQueueToPlaybackProcess(playWhenReady = true)
    }

    fun pausePlayback() {
        serviceBridge.connectIfNeeded()
        if (!serviceBridge.pause()) {
            runtime.setStatusText("暂停失败：后台播放进程未连接")
        }
    }

    fun resumePlayback() {
        serviceBridge.connectIfNeeded()
        if (!serviceBridge.play()) {
            runtime.setStatusText("恢复失败：后台播放进程未连接")
        }
    }

    fun stopAll(updateStatus: Boolean) {
        serviceBridge.stop()
        runtime.stopAll(updateStatus = updateStatus)
    }

    fun formatDuration(durationMs: Long): String {
        return runtime.formatDuration(durationMs)
    }

    override fun onCleared() {
        remoteSyncJob.cancel()
        runtime.onHostStop()
        serviceBridge.release()
        super.onCleared()
    }

    private fun syncRemotePlaybackState() {
        val snapshot = serviceBridge.currentSnapshot() ?: run {
            serviceBridge.connectIfNeeded()
            return
        }
        runtime.updateRemotePlaybackState(
            playbackState = mapRemotePlaybackState(snapshot),
            positionMs = snapshot.currentPositionMs,
            durationMs = snapshot.durationMs
        )
        runtime.updateRemotePlaybackOutputInfo(snapshot.playbackOutputInfo)
        runtime.syncActiveItemById(snapshot.currentMediaId)
        snapshot.statusText
            ?.takeIf { it.isNotBlank() }
            ?.let { runtime.setStatusText(it) }
    }

    private fun mapRemotePlaybackState(snapshot: RemotePlaybackSnapshot): Int {
        return when {
            snapshot.isPlaying -> AUDIO_TRACK_PLAYSTATE_PLAYING
            snapshot.playbackState == Player.STATE_BUFFERING && snapshot.playWhenReady -> AUDIO_TRACK_PLAYSTATE_PLAYING
            snapshot.playbackState == Player.STATE_READY && snapshot.playWhenReady -> AUDIO_TRACK_PLAYSTATE_PLAYING
            snapshot.playbackState == Player.STATE_READY -> AUDIO_TRACK_PLAYSTATE_PAUSED
            else -> AUDIO_TRACK_PLAYSTATE_STOPPED
        }
    }

    private fun syncQueueToPlaybackProcess(playWhenReady: Boolean): Boolean {
        val state = uiStateFlow.value
        if (state.playlistItems.isEmpty()) {
            runtime.setStatusText("Pick audio first")
            return false
        }

        val activeIndex = state.activePlaylistIndex.takeIf { it in state.playlistItems.indices } ?: 0
        val queue = state.playlistItems.map { item ->
            MusicInfo(
                id = item.id,
                title = item.displayName,
                playbackUri = item.uri
            )
        }

        serviceBridge.ensureServiceStarted()
        serviceBridge.connectIfNeeded()
        val synced = serviceBridge.syncQueue(
            queue = queue,
            activeIndex = activeIndex,
            playWhenReady = playWhenReady
        )

        if (!synced) {
            runtime.setStatusText("播放失败：后台播放进程未连接")
        } else {
            runtime.setStatusText(
                if (playWhenReady) {
                    "已同步队列并开始后台播放"
                } else {
                    "已同步播放队列"
                }
            )
        }
        return synced
    }

    private companion object {
        private const val TAG = "PlayerViewModel"
    }
}
