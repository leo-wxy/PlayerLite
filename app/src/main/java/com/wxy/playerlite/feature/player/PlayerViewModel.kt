package com.wxy.playerlite.feature.player

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.runtime.PlayerRuntimeRegistry
import com.wxy.playerlite.feature.user.model.UserSessionUiState
import com.wxy.playerlite.feature.user.model.toUserSessionUiState
import com.wxy.playerlite.playback.client.PlayerServiceBridge
import com.wxy.playerlite.playback.client.RemotePlaybackSnapshot
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.process.PlayerMediaSessionService
import com.wxy.playerlite.player.PlaybackSpeed
import com.wxy.playerlite.user.model.LoginState
import com.wxy.playerlite.user.model.toAuthHeaders
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val runtime = PlayerRuntimeRegistry.get(appContext)
    private val userRepository = AppContainer.userRepository(appContext)
    private val serviceBridge = PlayerServiceBridge(appContext, PlayerMediaSessionService::class.java) { errorMessage ->
        runtime.setStatusText(errorMessage)
        Log.w(TAG, errorMessage)
    }
    private val remoteSyncJob: Job
    private val uiProgressJob: Job
    private val userStateJob: Job
    private var playbackModeToast: Toast? = null
    private var playbackModeToastDismissJob: Job? = null
    private val _userSessionUiState = MutableStateFlow(UserSessionUiState(isBusy = true))

    val uiStateFlow: StateFlow<PlayerUiState> = runtime.uiStateFlow
    val userSessionUiStateFlow: StateFlow<UserSessionUiState> = _userSessionUiState.asStateFlow()

    init {
        serviceBridge.ensureServiceStarted()
        serviceBridge.connectIfNeeded()
        userStateJob = viewModelScope.launch {
            userRepository.loginStateFlow.collect(::publishUserState)
        }
        remoteSyncJob = viewModelScope.launch {
            while (isActive) {
                syncRemotePlaybackState()
                delay(250L)
            }
        }
        uiProgressJob = viewModelScope.launch {
            while (isActive) {
                runtime.tickRemotePlaybackPosition()
                delay(100L)
            }
        }
        viewModelScope.launch {
            setUserBusy(true)
            userRepository.restorePersistedSession()
            setUserBusy(false)
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
        if (!uiStateFlow.value.isSeekSupported) {
            runtime.setStatusText("当前音源不支持拖动 seek")
            return
        }
        val target = uiStateFlow.value.seekDragPositionMs
        runtime.finishSeekDrag()
        if (!serviceBridge.seekTo(target)) {
            runtime.setStatusText("后台播放进程未连接")
        }
    }

    fun updatePlaybackSpeed(speed: Float) {
        val previousSpeed = uiStateFlow.value.playbackSpeed
        val normalizedSpeed = PlaybackSpeed.normalizeValue(speed)
        runtime.updateLocalPlaybackSpeed(normalizedSpeed)
        serviceBridge.connectIfNeeded()
        if (!serviceBridge.setPlaybackSpeed(normalizedSpeed) { success ->
                if (!success) {
                    runtime.revertPendingPlaybackSpeed(previousSpeed)
                }
            }) {
            runtime.revertPendingPlaybackSpeed(previousSpeed)
            runtime.setStatusText("倍速设置失败：后台播放进程未连接")
        }
    }

    fun updatePlaybackMode(playbackMode: PlaybackMode) {
        val shouldContinue = uiStateFlow.value.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING
        val currentPosition = uiStateFlow.value.displayedSeekMs
        runtime.updateLocalPlaybackMode(playbackMode)
        syncQueueToPlaybackProcess(
            playWhenReady = shouldContinue,
            startPositionMs = currentPosition
        )
    }

    fun cyclePlaybackMode() {
        val nextMode = uiStateFlow.value.playbackMode.nextPlaybackMode()
        updatePlaybackMode(nextMode)
        showPlaybackModeToast(nextMode.toastText())
    }

    fun setShowOriginalOrderInShuffle(show: Boolean) {
        runtime.setShowOriginalOrderInShuffle(show)
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
        serviceBridge.connectIfNeeded()
        if (!serviceBridge.seekToPreviousMediaItem()) {
            runtime.setStatusText("切换上一首失败：后台播放进程未连接")
        }
    }

    fun skipToNextTrack() {
        serviceBridge.connectIfNeeded()
        if (!serviceBridge.seekToNextMediaItem()) {
            runtime.setStatusText("切换下一首失败：后台播放进程未连接")
        }
    }

    fun playSelectedAudio() {
        syncQueueToPlaybackProcess(playWhenReady = true)
    }

    fun runUiTestEntry() {
        serviceBridge.ensureServiceStarted()
        serviceBridge.connectIfNeeded()
        val resourceKey = "ui-test-${UUID.randomUUID()}"
        val synced = serviceBridge.syncQueue(
            queue = listOf(
                MusicInfo(
                    id = resourceKey,
                    title = UI_TEST_TITLE,
                    playbackUri = UI_TEST_MP3_URL
                )
            ),
            activeIndex = 0,
            playWhenReady = true
        )

        runtime.setStatusText(
            if (synced) {
                "已启动 UI 测试流: $UI_TEST_TITLE ($resourceKey)"
            } else {
                "UI 测试流启动失败：后台播放进程未连接"
            }
        )
    }

    fun logout() {
        viewModelScope.launch {
            setUserBusy(true)
            userRepository.logout()
            runtime.setStatusText("已退出登录")
            setUserBusy(false)
        }
    }

    fun clearCache() {
        serviceBridge.ensureServiceStarted()
        serviceBridge.connectIfNeeded()
        val accepted = serviceBridge.clearCache()
        runtime.setStatusText(
            if (accepted) {
                "已请求清理缓存"
            } else {
                "清理缓存请求失败：后台播放进程未连接"
            }
        )
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
        playbackModeToastDismissJob?.cancel()
        playbackModeToast?.cancel()
        userStateJob.cancel()
        remoteSyncJob.cancel()
        uiProgressJob.cancel()
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
            durationMs = snapshot.durationMs,
            isSeekSupported = snapshot.isSeekSupported,
            playbackSpeed = snapshot.playbackSpeed,
            playbackMode = snapshot.playbackMode,
            currentMediaId = snapshot.currentMediaId,
            isProgressAdvancing = snapshot.isPlaying,
            playbackOutputInfo = snapshot.playbackOutputInfo,
            audioMeta = snapshot.audioMeta
        )
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

    private fun syncQueueToPlaybackProcess(
        playWhenReady: Boolean,
        startPositionMs: Long = C.TIME_UNSET
    ): Boolean {
        val queueItems = runtime.playbackQueueItems()
        if (queueItems.isEmpty()) {
            runtime.setStatusText("Pick audio first")
            return false
        }

        val activeIndex = runtime.playbackQueueActiveIndex().takeIf { it in queueItems.indices } ?: 0
        val queue = queueItems.map { item ->
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
            playWhenReady = playWhenReady,
            startPositionMs = startPositionMs
        )

        if (!synced) {
            runtime.setStatusText("播放失败：后台播放进程未连接")
        } else {
            serviceBridge.setPlaybackMode(uiStateFlow.value.playbackMode)
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

    private fun showPlaybackModeToast(message: String) {
        playbackModeToastDismissJob?.cancel()
        playbackModeToast?.cancel()
        val toast = Toast.makeText(appContext, message, Toast.LENGTH_SHORT)
        playbackModeToast = toast
        toast.show()
        playbackModeToastDismissJob = viewModelScope.launch {
            delay(900L)
            if (playbackModeToast === toast) {
                toast.cancel()
                playbackModeToast = null
            }
        }
    }

    private fun publishUserState(loginState: LoginState) {
        _userSessionUiState.value = loginState.toUserSessionUiState(
            isBusy = _userSessionUiState.value.isBusy
        )
    }

    private fun setUserBusy(isBusy: Boolean) {
        _userSessionUiState.value = _userSessionUiState.value.copy(isBusy = isBusy)
    }

    private companion object {
        private const val TAG = "PlayerViewModel"
        private const val UI_TEST_TITLE = "UI Local MP3 Test"
        private const val UI_TEST_MP3_URL = "http://10.0.2.2:18080/local-media-ui-test.mp3"
    }
}
