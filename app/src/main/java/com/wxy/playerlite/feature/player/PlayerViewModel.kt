package com.wxy.playerlite.feature.player

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.core.playback.AppPlaybackGraph
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.feature.player.model.PlayerLyricUiState
import com.wxy.playerlite.feature.player.model.PlayerTopTab
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.user.model.UserSessionUiState
import com.wxy.playerlite.feature.user.model.toUserSessionUiState
import com.wxy.playerlite.playback.client.RemotePlaybackSnapshot
import com.wxy.playerlite.playback.model.LocalMusicInfo
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.orchestrator.PlaybackQueueController
import com.wxy.playerlite.playback.orchestrator.PlaybackSettingsController
import com.wxy.playerlite.playback.orchestrator.PlaybackServiceSynchronizer
import com.wxy.playerlite.playback.orchestrator.PlaybackTransportController
import com.wxy.playerlite.playback.orchestrator.PlayerServiceController
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.PlaybackSpeed
import com.wxy.playerlite.user.model.LoginState
import com.wxy.playerlite.user.model.toAuthHeaders
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class PlayerViewModel(
    application: Application,
    private val runtime: com.wxy.playerlite.feature.player.runtime.PlayerRuntime = AppPlaybackGraph.runtime(
        application.applicationContext
    ),
    private val userRepository: com.wxy.playerlite.user.UserRepository = AppContainer.userRepository(application.applicationContext),
    private val songWikiRepository: SongWikiRepository = AppContainer.songWikiRepository(application.applicationContext),
    private val lyricRepository: LyricRepository = AppContainer.lyricRepository(application.applicationContext),
    private val serviceBridge: PlayerServiceController = AppPlaybackGraph.playerServiceController(
        context = application.applicationContext,
        onControllerError = { errorMessage ->
            runtime.setStatusText(errorMessage)
            Log.w(TAG, errorMessage)
        }
    ),
    private val initializeSessionRestore: Boolean = true,
    private val remoteSyncIntervalMs: Long = 250L,
    private val uiProgressIntervalMs: Long = 100L,
    private val lyricRequestDelayMs: Long = DEFAULT_LYRIC_REQUEST_DELAY_MS
) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val remoteSyncJob: Job
    private val uiProgressJob: Job
    private val userStateJob: Job
    private val lyricTargetJob: Job
    private val displayMetadataJob: Job
    private var songWikiJob: Job? = null
    private var lyricJob: Job? = null
    private var playbackModeToast: Toast? = null
    private var playbackModeToastDismissJob: Job? = null
    private val _userSessionUiState = MutableStateFlow(UserSessionUiState(isBusy = true))

    val uiStateFlow: StateFlow<PlayerUiState> = runtime.uiStateFlow
    val userSessionUiStateFlow: StateFlow<UserSessionUiState> = _userSessionUiState.asStateFlow()
    private val playbackSynchronizer = PlaybackServiceSynchronizer(
        runtime = runtime,
        serviceController = serviceBridge,
        authHeadersProvider = { userRepository.currentSession()?.toAuthHeaders().orEmpty() },
        playbackStateMapper = ::mapRemotePlaybackState,
        localShouldContinuePlayback = {
            val uiState = uiStateFlow.value
            uiState.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING || uiState.isPreparing
        }
    )
    private val playbackTransportController = PlaybackTransportController(
        runtime = runtime,
        serviceController = serviceBridge,
        playbackSynchronizer = playbackSynchronizer
    )
    private val playbackQueueController = PlaybackQueueController(
        runtime = runtime,
        serviceController = serviceBridge,
        playbackSynchronizer = playbackSynchronizer
    )
    private val playbackSettingsController = PlaybackSettingsController(
        runtime = runtime,
        serviceController = serviceBridge
    )

    constructor(application: Application) : this(
        application = application,
        runtime = AppPlaybackGraph.runtime(application.applicationContext),
        userRepository = AppContainer.userRepository(application.applicationContext),
        songWikiRepository = AppContainer.songWikiRepository(application.applicationContext),
        lyricRepository = AppContainer.lyricRepository(application.applicationContext),
        serviceBridge = AppPlaybackGraph.playerServiceController(
            context = application.applicationContext,
            onControllerError = { errorMessage ->
                val runtime = AppPlaybackGraph.runtime(application.applicationContext)
                runtime.setStatusText(errorMessage)
                Log.w(TAG, errorMessage)
            }
        ),
        initializeSessionRestore = true,
        remoteSyncIntervalMs = 250L,
        uiProgressIntervalMs = 100L,
        lyricRequestDelayMs = DEFAULT_LYRIC_REQUEST_DELAY_MS
    )

    init {
        serviceBridge.prewarmConnection()
        userStateJob = viewModelScope.launch {
            userRepository.loginStateFlow.collect(::publishUserState)
        }
        lyricTargetJob = viewModelScope.launch {
            uiStateFlow.map { state ->
                LyricLoadTarget(
                    songId = state.currentSongId,
                    hasSelection = state.hasSelection,
                    shouldLoad = state.isPreparing ||
                        state.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING ||
                        state.playbackState == AUDIO_TRACK_PLAYSTATE_PAUSED
                )
            }.distinctUntilChanged()
                .collect(::scheduleLyricsForTarget)
        }
        displayMetadataJob = viewModelScope.launch {
            var hasPublishedDisplayMetadata = false
            uiStateFlow.map(::resolveDisplayMetadataTarget)
                .distinctUntilChanged()
                .collect { target ->
                    if (target == null) {
                        if (!hasPublishedDisplayMetadata) {
                            return@collect
                        }
                        serviceBridge.connectIfNeeded()
                        serviceBridge.setDisplayMetadata(title = null, subtitle = null)
                        hasPublishedDisplayMetadata = false
                        return@collect
                    }
                    serviceBridge.connectIfNeeded()
                    serviceBridge.setDisplayMetadata(
                        title = target.title,
                        subtitle = target.subtitle
                    )
                    hasPublishedDisplayMetadata = true
                }
        }
        remoteSyncJob = viewModelScope.launch {
            while (isActive) {
                playbackSynchronizer.syncRemotePlaybackState()
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

    fun onRetryLyrics() {
        val songId = uiStateFlow.value.currentSongId ?: return
        loadLyricsNow(songId = songId, showLoading = true)
    }

    fun onSelectTopTab(topTab: PlayerTopTab) {
        runtime.selectTopTab(topTab)
    }

    fun onPlayerSurfaceVisibilityChanged(isVisible: Boolean) {
        // Lyrics follow the active playback state instead of screen visibility.
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
        playbackTransportController.seekTo(target)
    }

    fun updatePlaybackSpeed(speed: Float) {
        val previousSpeed = uiStateFlow.value.playbackSpeed
        val normalizedSpeed = PlaybackSpeed.normalizeValue(speed)
        playbackSettingsController.updatePlaybackSpeed(
            playbackSpeed = normalizedSpeed,
            previousPlaybackSpeed = previousSpeed
        )
    }

    fun updatePlaybackMode(playbackMode: PlaybackMode) {
        val currentPosition = uiStateFlow.value.displayedSeekMs
        playbackQueueController.updatePlaybackMode(
            playbackMode = playbackMode,
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
        playbackQueueController.selectPlaylistItem(index)
    }

    fun removePlaylistItem(index: Int) {
        val previous = uiStateFlow.value
        val removedActiveWhilePlayingOrPreparing =
            (previous.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING || previous.isPreparing) &&
            index == previous.activePlaylistIndex
        playbackQueueController.removePlaylistItem(
            index = index,
            removedActiveWhilePlayingOrPreparing = removedActiveWhilePlayingOrPreparing
        )
    }

    fun clearPlaylist() {
        playbackQueueController.clearPlaylist()
    }

    fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        playbackQueueController.movePlaylistItem(fromIndex, toIndex)
    }

    fun skipToPreviousTrack() {
        playbackTransportController.skipToPreviousTrack()
    }

    fun skipToNextTrack() {
        playbackTransportController.skipToNextTrack()
    }

    fun playSelectedAudio() {
        playbackTransportController.playSelectedAudio()
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
        playbackTransportController.clearCache()
    }

    fun onShareCurrentTrack() {
        showTransientToast("分享功能待补充")
    }

    fun onFavoriteCurrentTrack() {
        showTransientToast("收藏功能待补充")
    }

    fun onShowPlayerMoreActions() {
        runtime.onShowPlayerMoreActions()
    }

    fun onDismissPlayerMoreActions() {
        runtime.onDismissPlayerMoreActions()
    }

    fun showPlaybackSpeedSettings() {
        runtime.showPlaybackSpeedSettings()
    }

    fun showAudioEffectSettings() {
        runtime.showAudioEffectSettings()
    }

    fun dismissAudioEffectSettings() {
        runtime.dismissAudioEffectSettings()
    }

    fun returnToPlayerMoreActionsRoot() {
        runtime.returnToPlayerMoreActionsRoot()
    }

    fun updateAudioEffectPreset(audioEffectPreset: AudioEffectPreset) {
        val previousPreset = uiStateFlow.value.audioEffectPreset
        playbackSettingsController.updateAudioEffectPreset(
            audioEffectPreset = audioEffectPreset,
            previousAudioEffectPreset = previousPreset
        )
    }

    fun pausePlayback() {
        playbackTransportController.pausePlayback()
    }

    fun resumePlayback() {
        playbackTransportController.resumePlayback()
    }

    fun stopAll(updateStatus: Boolean) {
        playbackTransportController.stopPlayback()
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

    private fun scheduleLyricsForTarget(target: LyricLoadTarget) {
        lyricJob?.cancel()
        val songId = target.songId
        if (songId.isNullOrBlank()) {
            runtime.updateLyricUiState(
                if (target.hasSelection) {
                    PlayerLyricUiState.Empty("暂无歌词")
                } else {
                    PlayerLyricUiState.Placeholder
                }
            )
            return
        }
        if (!target.shouldLoad) {
            return
        }
        val currentLyricState = uiStateFlow.value.lyricUiState
        val alreadyResolvedForCurrentSong = currentLyricState is PlayerLyricUiState.Content &&
            currentLyricState.lyrics.songId == songId
        if (alreadyResolvedForCurrentSong) {
            return
        }
        lyricJob = viewModelScope.launch {
            val cached = runCatching {
                lyricRepository.readCachedLyrics(songId)
            }.getOrNull()
            if (uiStateFlow.value.currentSongId != songId) {
                return@launch
            }
            if (cached != null) {
                runtime.updateLyricUiState(PlayerLyricUiState.Content(cached))
                return@launch
            }
            runtime.updateLyricUiState(PlayerLyricUiState.Loading)
            loadLyricsInternal(songId = songId, showLoading = false)
        }
    }

    private fun loadLyricsNow(songId: String, showLoading: Boolean) {
        lyricJob?.cancel()
        lyricJob = viewModelScope.launch {
            loadLyricsInternal(songId = songId, showLoading = showLoading)
        }
    }

    private suspend fun loadLyricsInternal(songId: String, showLoading: Boolean) {
        if (showLoading) {
            runtime.updateLyricUiState(PlayerLyricUiState.Loading)
        }
        val nextState = runCatching {
            lyricRepository.fetchLyrics(songId)
        }.fold(
            onSuccess = { lyrics ->
                if (lyrics == null) {
                    PlayerLyricUiState.Empty("暂无歌词")
                } else {
                    PlayerLyricUiState.Content(lyrics)
                }
            },
            onFailure = {
                PlayerLyricUiState.Error("歌词加载失败")
            }
        )
        if (uiStateFlow.value.currentSongId == songId) {
            runtime.updateLyricUiState(nextState)
        }
    }

    fun formatDuration(durationMs: Long): String {
        return runtime.formatDuration(durationMs)
    }

    override fun onCleared() {
        playbackModeToastDismissJob?.cancel()
        playbackModeToast?.cancel()
        lyricJob?.cancel()
        lyricTargetJob.cancel()
        displayMetadataJob.cancel()
        userStateJob.cancel()
        remoteSyncJob.cancel()
        uiProgressJob.cancel()
        runtime.onHostStop()
        serviceBridge.release()
        super.onCleared()
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

    private fun resolveDisplayMetadataTarget(state: PlayerUiState): DisplayMetadataTarget? {
        if (!state.hasSelection) {
            return null
        }
        val projection = resolvePlayerDisplayContentProjection(
            playerState = state,
            emptyTitle = "",
            emptySubtitle = ""
        )
        return DisplayMetadataTarget(
            title = projection.displayMetadataTitle.takeIf { it.isNotBlank() },
            subtitle = projection.songArtistLine.takeIf { it.isNotBlank() }
        )
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
        private const val DEFAULT_LYRIC_REQUEST_DELAY_MS = 400L
    }
}

private data class LyricLoadTarget(
    val songId: String?,
    val hasSelection: Boolean,
    val shouldLoad: Boolean
)

private data class DisplayMetadataTarget(
    val title: String?,
    val subtitle: String?
)
