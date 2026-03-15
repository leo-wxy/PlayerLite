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
import com.wxy.playerlite.feature.player.runtime.toQueuePlayableItem
import com.wxy.playerlite.feature.user.model.UserSessionUiState
import com.wxy.playerlite.feature.user.model.toUserSessionUiState
import com.wxy.playerlite.playback.client.PlayerServiceBridge
import com.wxy.playerlite.playback.client.RemotePlaybackSnapshot
import com.wxy.playerlite.playback.model.LocalMusicInfo
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

internal class PlayerViewModel(
    application: Application,
    private val runtime: com.wxy.playerlite.feature.player.runtime.PlayerRuntime = PlayerRuntimeRegistry.get(application.applicationContext),
    private val userRepository: com.wxy.playerlite.user.UserRepository = AppContainer.userRepository(application.applicationContext),
    private val songWikiRepository: SongWikiRepository = AppContainer.songWikiRepository(application.applicationContext),
    private val serviceBridge: PlayerControlBridge = MediaControllerPlayerControlBridge(
        context = application.applicationContext,
        onControllerError = { errorMessage ->
            runtime.setStatusText(errorMessage)
            Log.w(TAG, errorMessage)
        }
    ),
    private val initializeSessionRestore: Boolean = true,
    private val remoteSyncIntervalMs: Long = 250L,
    private val uiProgressIntervalMs: Long = 100L
) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val remoteSyncJob: Job
    private val uiProgressJob: Job
    private val userStateJob: Job
    private var songWikiJob: Job? = null
    private var playbackModeToast: Toast? = null
    private var playbackModeToastDismissJob: Job? = null
    private val _userSessionUiState = MutableStateFlow(UserSessionUiState(isBusy = true))

    val uiStateFlow: StateFlow<PlayerUiState> = runtime.uiStateFlow
    val userSessionUiStateFlow: StateFlow<UserSessionUiState> = _userSessionUiState.asStateFlow()

    constructor(application: Application) : this(
        application = application,
        runtime = PlayerRuntimeRegistry.get(application.applicationContext),
        userRepository = AppContainer.userRepository(application.applicationContext),
        songWikiRepository = AppContainer.songWikiRepository(application.applicationContext),
        serviceBridge = MediaControllerPlayerControlBridge(
            context = application.applicationContext,
            onControllerError = { errorMessage ->
                val runtime = PlayerRuntimeRegistry.get(application.applicationContext)
                runtime.setStatusText(errorMessage)
                Log.w(TAG, errorMessage)
            }
        ),
        initializeSessionRestore = true,
        remoteSyncIntervalMs = 250L,
        uiProgressIntervalMs = 100L
    )

    init {
        serviceBridge.prewarmConnection()
        userStateJob = viewModelScope.launch {
            userRepository.loginStateFlow.collect(::publishUserState)
        }
        remoteSyncJob = viewModelScope.launch {
            while (isActive) {
                syncRemotePlaybackState()
                delay(remoteSyncIntervalMs)
            }
        }
        uiProgressJob = viewModelScope.launch {
            while (isActive) {
                runtime.tickRemotePlaybackPosition()
                delay(uiProgressIntervalMs)
            }
        }
        if (initializeSessionRestore) {
            viewModelScope.launch {
                setUserBusy(true)
                userRepository.restorePersistedSession()
                setUserBusy(false)
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

    fun onShowSongWiki() {
        val songId = uiStateFlow.value.currentSongId ?: return
        when (uiStateFlow.value.songWikiUiState) {
            is com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState.Content,
            is com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState.Empty,
            is com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState.Loading -> {
                runtime.onShowSongWiki()
            }

            com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState.Placeholder,
            is com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState.Error -> {
                loadSongWiki(songId)
            }
        }
    }

    fun onDismissSongWiki() {
        runtime.onDismissSongWiki()
    }

    fun onRetrySongWiki() {
        val songId = uiStateFlow.value.currentSongId ?: return
        loadSongWiki(songId)
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
        val shouldContinue = shouldContinuePlayback()
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
        val shouldContinue = shouldContinuePlayback()
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
        val shouldContinue = (previous.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING || previous.isPreparing) &&
            index == previous.activePlaylistIndex
        syncQueueToPlaybackProcess(playWhenReady = shouldContinue)
    }

    fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        val shouldContinue = shouldContinuePlayback()
        runtime.movePlaylistItem(fromIndex, toIndex)
        syncQueueToPlaybackProcess(playWhenReady = shouldContinue)
    }

    fun skipToPreviousTrack() {
        ensureRemoteQueueReadyForSkip()
        serviceBridge.ensurePlaybackServiceStartedForPlayback()
        serviceBridge.connectIfNeeded()
        if (!serviceBridge.seekToPreviousMediaItem()) {
            runtime.setStatusText("切换上一首失败：后台播放进程未连接")
        }
    }

    fun skipToNextTrack() {
        ensureRemoteQueueReadyForSkip()
        serviceBridge.ensurePlaybackServiceStartedForPlayback()
        serviceBridge.connectIfNeeded()
        if (!serviceBridge.seekToNextMediaItem()) {
            runtime.setStatusText("切换下一首失败：后台播放进程未连接")
        }
    }

    fun playSelectedAudio() {
        syncQueueToPlaybackProcess(playWhenReady = true)
    }

    fun runUiTestEntry() {
        serviceBridge.ensurePlaybackServiceStartedForPlayback()
        serviceBridge.connectIfNeeded()
        val resourceKey = "ui-test-${UUID.randomUUID()}"
        val synced = serviceBridge.syncQueue(
            queue = listOf(
                LocalMusicInfo(
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

    fun onShareCurrentTrack() {
        showTransientToast("分享功能待补充")
    }

    fun onFavoriteCurrentTrack() {
        showTransientToast("收藏功能待补充")
    }

    fun onShowPlayerMoreActions() {
        showTransientToast("更多功能待补充")
    }

    fun pausePlayback() {
        serviceBridge.connectIfNeeded()
        if (!serviceBridge.pause()) {
            runtime.setStatusText("暂停失败：后台播放进程未连接")
        }
    }

    fun resumePlayback() {
        serviceBridge.ensurePlaybackServiceStartedForPlayback()
        serviceBridge.connectIfNeeded()
        if (!serviceBridge.play()) {
            runtime.setStatusText("恢复失败：后台播放进程未连接")
        }
    }

    fun stopAll(updateStatus: Boolean) {
        serviceBridge.stop()
        runtime.stopAll(updateStatus = updateStatus)
    }

    private fun loadSongWiki(songId: String) {
        songWikiJob?.cancel()
        runtime.onShowSongWiki()
        runtime.updateSongWikiUiState(com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState.Loading)
        songWikiJob = viewModelScope.launch {
            val nextState = runCatching {
                songWikiRepository.fetchSongWiki(songId)
            }.fold(
                onSuccess = { summary ->
                    if (summary == null) {
                        com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState.Empty("暂无歌曲百科")
                    } else {
                        com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState.Content(summary)
                    }
                },
                onFailure = {
                    com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState.Error("歌曲百科加载失败")
                }
            )
            if (uiStateFlow.value.currentSongId == songId) {
                runtime.updateSongWikiUiState(nextState)
            }
        }
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
            return
        }
        runtime.updateRemotePlaybackState(
            playbackState = mapRemotePlaybackState(snapshot),
            positionMs = snapshot.currentPositionMs,
            durationMs = snapshot.durationMs,
            isSeekSupported = snapshot.isSeekSupported,
            isPreparing = snapshot.playbackState == Player.STATE_BUFFERING,
            playbackSpeed = snapshot.playbackSpeed,
            playbackMode = snapshot.playbackMode,
            currentMediaId = snapshot.currentMediaId,
            isProgressAdvancing = snapshot.isPlaying,
            currentPlayable = snapshot.currentPlayable,
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
            snapshot.playbackState == Player.STATE_BUFFERING -> AUDIO_TRACK_PLAYSTATE_PAUSED
            snapshot.playbackState == Player.STATE_READY && snapshot.playWhenReady -> AUDIO_TRACK_PLAYSTATE_PLAYING
            snapshot.playbackState == Player.STATE_READY -> AUDIO_TRACK_PLAYSTATE_PAUSED
            else -> AUDIO_TRACK_PLAYSTATE_STOPPED
        }
    }

    private fun ensureRemoteQueueReadyForSkip() {
        val snapshot = serviceBridge.currentSnapshot()
        if (!snapshot?.currentMediaId.isNullOrBlank()) {
            return
        }
        if (runtime.playbackQueueItems().isEmpty()) {
            return
        }
        val shouldContinue = shouldContinuePlayback(snapshot)
        syncQueueToPlaybackProcess(
            playWhenReady = shouldContinue,
            requirePlaybackServiceStart = true
        )
    }

    private fun shouldContinuePlayback(snapshot: RemotePlaybackSnapshot? = serviceBridge.currentSnapshot()): Boolean {
        if (snapshot != null) {
            return snapshot.playWhenReady || snapshot.isPlaying || snapshot.playbackState == Player.STATE_BUFFERING
        }
        val uiState = uiStateFlow.value
        return uiState.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING || uiState.isPreparing
    }

    private fun syncQueueToPlaybackProcess(
        playWhenReady: Boolean,
        startPositionMs: Long = C.TIME_UNSET,
        requirePlaybackServiceStart: Boolean = playWhenReady
    ): Boolean {
        val queueItems = runtime.playbackQueueItems()
        if (queueItems.isEmpty()) {
            runtime.setStatusText("Pick audio first")
            return false
        }

        val activeIndex = runtime.playbackQueueActiveIndex().takeIf { it in queueItems.indices } ?: 0
        val activeItemId = queueItems.getOrNull(activeIndex)?.id
        val authHeaders = userRepository.currentSession()?.toAuthHeaders().orEmpty()
        val queueEntries = queueItems.mapNotNull { item ->
            val playable = item.toQueuePlayableItem()?.let { candidate ->
                if (candidate is com.wxy.playerlite.playback.model.MusicInfo) {
                    candidate.copy(requestHeaders = authHeaders)
                } else {
                    candidate
                }
            }
            playable?.let { item.id to it }
        }
        if (queueEntries.isEmpty()) {
            runtime.setStatusText("播放失败：当前列表没有可投影的可播放条目")
            return false
        }
        val queue = queueEntries.map { it.second }
        val normalizedActiveIndex = queueEntries.indexOfFirst { it.first == activeItemId }
            .takeIf { it >= 0 }
            ?: 0

        if (requirePlaybackServiceStart) {
            serviceBridge.ensurePlaybackServiceStartedForPlayback()
        }
        serviceBridge.connectIfNeeded()
        val synced = serviceBridge.syncQueue(
            queue = queue,
            activeIndex = normalizedActiveIndex,
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
        showTransientToast(message)
    }

    private fun showTransientToast(message: String) {
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

internal interface PlayerControlBridge {
    fun prewarmConnection()
    fun ensurePlaybackServiceStartedForPlayback()
    fun connectIfNeeded()
    fun syncQueue(
        queue: List<com.wxy.playerlite.playback.model.PlayableItem>,
        activeIndex: Int,
        playWhenReady: Boolean,
        startPositionMs: Long = C.TIME_UNSET
    ): Boolean

    fun play(): Boolean
    fun pause(): Boolean
    fun seekTo(positionMs: Long): Boolean
    fun seekToNextMediaItem(): Boolean
    fun seekToPreviousMediaItem(): Boolean
    fun stop(): Boolean
    fun clearCache(): Boolean
    fun setPlaybackSpeed(speed: Float, onResult: ((Boolean) -> Unit)? = null): Boolean
    fun setPlaybackMode(playbackMode: PlaybackMode, onResult: ((Boolean) -> Unit)? = null): Boolean
    fun currentSnapshot(): RemotePlaybackSnapshot?
    fun release()
}

private class MediaControllerPlayerControlBridge(
    context: android.content.Context,
    onControllerError: (String) -> Unit
) : PlayerControlBridge {
    private val delegate = PlayerServiceBridge(
        context = context,
        serviceClass = PlayerMediaSessionService::class.java,
        onControllerError = onControllerError
    )

    override fun prewarmConnection() = delegate.prewarmConnection()

    override fun ensurePlaybackServiceStartedForPlayback() =
        delegate.ensurePlaybackServiceStartedForPlayback()

    override fun connectIfNeeded() = delegate.connectIfNeeded()

    override fun syncQueue(
        queue: List<com.wxy.playerlite.playback.model.PlayableItem>,
        activeIndex: Int,
        playWhenReady: Boolean,
        startPositionMs: Long
    ): Boolean = delegate.syncQueue(queue, activeIndex, playWhenReady, startPositionMs)

    override fun play(): Boolean = delegate.play()

    override fun pause(): Boolean = delegate.pause()

    override fun seekTo(positionMs: Long): Boolean = delegate.seekTo(positionMs)

    override fun seekToNextMediaItem(): Boolean = delegate.seekToNextMediaItem()

    override fun seekToPreviousMediaItem(): Boolean = delegate.seekToPreviousMediaItem()

    override fun stop(): Boolean = delegate.stop()

    override fun clearCache(): Boolean = delegate.clearCache()

    override fun setPlaybackSpeed(speed: Float, onResult: ((Boolean) -> Unit)?): Boolean {
        return delegate.setPlaybackSpeed(speed, onResult)
    }

    override fun setPlaybackMode(playbackMode: PlaybackMode, onResult: ((Boolean) -> Unit)?): Boolean {
        return delegate.setPlaybackMode(playbackMode, onResult)
    }

    override fun currentSnapshot(): RemotePlaybackSnapshot? = delegate.currentSnapshot()

    override fun release() = delegate.release()
}
