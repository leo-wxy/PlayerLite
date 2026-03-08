package com.wxy.playerlite.feature.player.runtime

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.wxy.playerlite.core.playlist.PlaylistController
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.SharedPreferencesPlaylistStorage
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.model.emptyAudioMeta
import com.wxy.playerlite.feature.player.model.withPlaybackSpeed
import com.wxy.playerlite.feature.player.runtime.action.PlayActionHandler
import com.wxy.playerlite.feature.player.runtime.action.PlaybackControlActionHandler
import com.wxy.playerlite.feature.player.runtime.action.PlaylistActionHandler
import com.wxy.playerlite.feature.player.runtime.action.PreparationActionHandler
import com.wxy.playerlite.player.NativePlayer
import com.wxy.playerlite.player.PlaybackOutputInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

internal class PlayerRuntime(
    appContext: Context,
    private val scope: CoroutineScope,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val mediaSourceRepository = MediaSourceRepository(appContext)
    private val playbackCoordinator = PlaybackCoordinator(
        player = NativePlayer(),
        scope = scope
    )
    private val playlistSession = PlaylistSessionCoordinator(
        controller = PlaylistController(
            storage = SharedPreferencesPlaylistStorage(
                appContext.getSharedPreferences("playlist_state", Context.MODE_PRIVATE)
            ),
            scope = scope
        )
    )
    private val sourceSession = PreparedSourceSession()
    private val trackPreparationCoordinator = TrackPreparationCoordinator(
        sourceRepository = mediaSourceRepository,
        playbackCoordinator = playbackCoordinator
    )

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiStateFlow: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private var remoteProgressAnchorPositionMs: Long = 0L
    private var remoteProgressAnchorElapsedRealtimeMs: Long = 0L
    private var remoteProgressShouldAdvance: Boolean = false
    private var pendingPlaybackSpeed: Float? = null

    private var uiState: PlayerUiState
        get() = _uiState.value
        set(value) {
            _uiState.value = value
        }

    private val playlistActions = PlaylistActionHandler(
        playlistSession = playlistSession,
        sourceSession = sourceSession,
        getUiState = { uiState },
        setUiState = { state -> uiState = state },
        syncSelectionFromPlaylist = ::syncSelectionFromPlaylist,
        switchToPlaylistIndex = ::switchToPlaylistIndex,
        prepareActiveItem = { prepareActiveItem() },
        stopPlaybackOnly = ::stopPlaybackOnly,
        releaseSelectedSource = ::releaseSelectedSource,
        resetAudioMetaState = ::resetAudioMetaState
    )

    private val playbackControlActions = PlaybackControlActionHandler(
        playbackCoordinator = playbackCoordinator,
        getUiState = { uiState },
        setUiState = { state -> uiState = state },
        refreshPlaybackState = { scope.launch { refreshPlaybackStateNow() } },
        formatDuration = ::formatDuration
    )

    private val playActions = PlayActionHandler(
        playlistSession = playlistSession,
        sourceSession = sourceSession,
        playbackCoordinator = playbackCoordinator,
        getUiState = { uiState },
        setUiState = { state -> uiState = state },
        prepareActiveItem = { prepareActiveItem() },
        advanceToNextAfterCompletion = ::advanceToNextAfterCompletion,
        refreshPlaybackState = { scope.launch { refreshPlaybackStateNow() } }
    )

    private val prepareActions = PreparationActionHandler(
        scope = scope,
        playlistSession = playlistSession,
        sourceSession = sourceSession,
        trackPreparationCoordinator = trackPreparationCoordinator,
        getUiState = { uiState },
        setUiState = { state -> uiState = state },
        stopPlaybackOnly = ::stopPlaybackOnly,
        releaseSelectedSource = ::releaseSelectedSource,
        resetAudioMetaState = ::resetAudioMetaState,
        syncSelectionFromPlaylist = ::syncSelectionFromPlaylist,
        removeInvalidActiveItem = ::removeInvalidActiveItem,
        playSelectedAudio = ::playSelectedAudio
    )

    init {
        playbackCoordinator.setProgressListener { progressMs ->
            if (!playbackCoordinator.isPlayInFlight()) {
                return@setProgressListener
            }
            val normalized = progressMs.coerceAtLeast(0L)
            val bounded = if (uiState.durationMs > 0L) normalized.coerceAtMost(uiState.durationMs) else normalized
            if (!uiState.isSeekDragging) {
                uiState = uiState.copy(seekPositionMs = bounded)
            }
        }

        playbackCoordinator.setPlaybackOutputInfoListener { info ->
            val routeText = PlayerUiFormatter.formatPlaybackOutputInfo(info)
            Log.i(TAG, "Native playback route: $routeText")
            uiState = uiState.copy(playbackOutputInfo = info)
        }

        restorePlaylistState()
    }

    fun onAudioPicked(uri: Uri?) {
        if (uri == null) {
            uiState = uiState.copy(statusText = "Selection canceled")
            return
        }
        addPickedUriToPlaylist(uri)
    }

    fun onTogglePlaylistSheet() {
        uiState = uiState.copy(showPlaylistSheet = !uiState.showPlaylistSheet)
    }

    fun onDismissPlaylistSheet() {
        uiState = uiState.copy(showPlaylistSheet = false)
    }

    fun onSeekValueChange(value: Long) {
        if (!uiState.isSeekSupported) {
            return
        }
        uiState = uiState.copy(
            isSeekDragging = true,
            seekDragPositionMs = value
        )
    }

    fun onSeekFinished() {
        if (uiState.isSeekDragging) {
            playbackControlActions.applySeek(uiState.seekDragPositionMs)
        }
        uiState = uiState.copy(isSeekDragging = false)
    }

    fun finishSeekDrag() {
        val bounded = if (uiState.durationMs > 0L) {
            uiState.seekDragPositionMs.coerceIn(0L, uiState.durationMs)
        } else {
            uiState.seekDragPositionMs.coerceAtLeast(0L)
        }
        uiState = uiState.copy(
            seekPositionMs = bounded,
            seekDragPositionMs = bounded,
            isSeekDragging = false
        )
        updateRemoteProgressAnchor(bounded)
    }

    fun seekTo(positionMs: Long) {
        onSeekValueChange(positionMs)
        onSeekFinished()
    }

    fun onHostStop() {
        playlistSession.flush()
    }

    fun selectPlaylistItem(index: Int) {
        playlistActions.selectPlaylistItem(index)
    }

    fun removePlaylistItem(index: Int) {
        playlistActions.removePlaylistItem(index)
    }

    fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        playlistActions.movePlaylistItem(fromIndex, toIndex)
    }

    fun activePlaylistItem(): PlaylistItem? {
        return playlistSession.activeItem
    }

    fun setStatusText(statusText: String) {
        uiState = uiState.copy(statusText = statusText)
    }

    fun updateRemotePlaybackOutputInfo(playbackOutputInfo: PlaybackOutputInfo?) {
        uiState = uiState.copy(playbackOutputInfo = playbackOutputInfo)
    }

    fun syncActiveItemById(itemId: String?) {
        if (itemId.isNullOrBlank()) {
            return
        }
        val targetIndex = playlistSession.items.indexOfFirst { it.id == itemId }
        if (targetIndex < 0 || targetIndex == playlistSession.activeIndex) {
            return
        }

        playlistSession.setActiveIndex(targetIndex)
        syncSelectionFromPlaylist()
    }

    fun updateRemotePlaybackState(
        playbackState: Int,
        positionMs: Long,
        durationMs: Long,
        isSeekSupported: Boolean,
        playbackSpeed: Float,
        isProgressAdvancing: Boolean
    ) {
        val speedResolution = PlaybackSpeedSyncResolver.onRemoteUpdate(
            remoteSpeed = playbackSpeed,
            pendingSpeed = pendingPlaybackSpeed
        )
        pendingPlaybackSpeed = speedResolution.pendingSpeed
        val nextDuration = if (durationMs > 0L) durationMs else uiState.durationMs
        val bounded = if (nextDuration > 0L) {
            positionMs.coerceIn(0L, nextDuration)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        remoteProgressShouldAdvance = isProgressAdvancing
        updateRemoteProgressAnchor(bounded)
        uiState = uiState.copy(
            playbackState = playbackState,
            durationMs = nextDuration,
            isSeekSupported = isSeekSupported,
            seekPositionMs = if (uiState.isSeekDragging) uiState.seekPositionMs else bounded,
            seekDragPositionMs = if (uiState.isSeekDragging) uiState.seekDragPositionMs else bounded
        ).withPlaybackSpeed(speedResolution.resolvedSpeed)
    }

    fun updateLocalPlaybackSpeed(playbackSpeed: Float) {
        val speedResolution = PlaybackSpeedSyncResolver.onLocalRequest(
            requestedSpeed = playbackSpeed
        )
        pendingPlaybackSpeed = speedResolution.pendingSpeed
        uiState = uiState.withPlaybackSpeed(speedResolution.resolvedSpeed)
        updateRemoteProgressAnchor(uiState.displayedSeekMs)
    }

    fun revertPendingPlaybackSpeed(playbackSpeed: Float) {
        val speedResolution = PlaybackSpeedSyncResolver.onCommandRejected(playbackSpeed)
        pendingPlaybackSpeed = speedResolution.pendingSpeed
        uiState = uiState.withPlaybackSpeed(speedResolution.resolvedSpeed)
    }

    fun tickRemotePlaybackPosition() {
        if (uiState.isSeekDragging || !remoteProgressShouldAdvance) {
            return
        }
        val estimated = PlaybackProgressEstimator.estimatePositionMs(
            anchorPositionMs = remoteProgressAnchorPositionMs,
            anchorElapsedRealtimeMs = remoteProgressAnchorElapsedRealtimeMs,
            nowElapsedRealtimeMs = elapsedRealtimeProvider(),
            playbackState = uiState.playbackState,
            playbackSpeed = uiState.playbackSpeed,
            durationMs = uiState.durationMs
        )
        if (estimated != uiState.seekPositionMs) {
            uiState = uiState.copy(seekPositionMs = estimated)
        }
    }

    fun skipToPreviousTrack() {
        playlistActions.skipToPreviousTrack()
    }

    fun skipToNextTrack() {
        playlistActions.skipToNextTrack()
    }

    fun playSelectedAudio() {
        playActions.playSelectedAudio()
    }

    fun pausePlayback() {
        playbackControlActions.pausePlayback()
    }

    fun resumePlayback() {
        playbackControlActions.resumePlayback()
    }

    fun stopAll(updateStatus: Boolean) {
        prepareActions.cancelPreparation()
        sourceSession.setAutoPlayWhenPrepared(false)

        stopPlaybackOnly(updateStatus = updateStatus)
    }

    fun formatDuration(durationMs: Long): String {
        return PlayerUiFormatter.formatDuration(durationMs)
    }

    fun close() {
        stopAll(updateStatus = false)
        releaseSelectedSource()
        playlistSession.flush()
        playbackCoordinator.close()
    }

    private fun restorePlaylistState() {
        playlistSession.restore(mediaSourceRepository::isPlaylistItemReadable)
        syncSelectionFromPlaylist()
        if (playlistSession.activeItem != null) {
            prepareActiveItem(stopPlayback = false)
        }
    }

    private fun addPickedUriToPlaylist(uri: Uri) {
        val permission = mediaSourceRepository.ensurePersistentReadPermission(uri)
        if (permission.isFailure) {
            uiState = uiState.copy(statusText = "该内容源未授予持久读取权限，已拒绝添加")
            return
        }
        if (!mediaSourceRepository.hasReadableAccess(uri)) {
            uiState = uiState.copy(statusText = "Failed to read selected audio")
            return
        }
        val displayName = mediaSourceRepository.queryDisplayName(uri)
        val item = PlaylistItem(
            id = UUID.randomUUID().toString(),
            uri = uri.toString(),
            displayName = displayName
        )
        prepareActions.addPickedUri(
            displayName = displayName,
            item = item
        )
    }

    private fun switchToPlaylistIndex(targetIndex: Int) {
        val target = playlistSession.itemAt(targetIndex) ?: return
        playlistSession.setActiveIndex(targetIndex)
        syncSelectionFromPlaylist()
        sourceSession.setAutoPlayWhenPrepared(false)
        uiState = uiState.copy(
            statusText = "已切换到: ${target.displayName}"
        )
        prepareActiveItem()
    }

    private fun prepareActiveItem(stopPlayback: Boolean = true) {
        prepareActions.prepareActiveItem(stopPlayback = stopPlayback)
    }

    private fun removeInvalidActiveItem(item: PlaylistItem, message: String) {
        playlistSession.removeItemById(item.id)
        syncSelectionFromPlaylist()
        releaseSelectedSource()
        resetAudioMetaState()
        uiState = uiState.copy(statusText = "$message，已从播放列表移除")
        if (playlistSession.activeItem != null) {
            prepareActiveItem(stopPlayback = false)
        }
    }

    private fun resetAudioMetaState() {
        uiState = uiState.copy(
            audioMeta = emptyAudioMeta(),
            playbackOutputInfo = null,
            isSeekSupported = false,
            durationMs = 0L,
            seekPositionMs = 0L,
            seekDragPositionMs = 0L,
            isSeekDragging = false
        )
        pendingPlaybackSpeed = null
        resetRemoteProgressAnchor()
    }

    private fun syncSelectionFromPlaylist() {
        val activeItem = playlistSession.activeItem
        uiState = uiState.copy(
            selectedFileName = activeItem?.displayName ?: "No audio selected",
            hasSelection = playlistSession.items.isNotEmpty(),
            playlistItems = playlistSession.items,
            activePlaylistIndex = playlistSession.activeIndex,
            showPlaylistSheet = if (playlistSession.items.isEmpty()) false else uiState.showPlaylistSheet
        )
    }

    private fun stopPlaybackOnly(updateStatus: Boolean) {
        sourceSession.stopCurrent()
        playbackCoordinator.stopPlayback()
        remoteProgressShouldAdvance = false

        uiState = uiState.copy(
            seekPositionMs = 0L,
            seekDragPositionMs = 0L,
            isSeekDragging = false,
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            statusText = if (updateStatus) "Stopped" else uiState.statusText
        )
        pendingPlaybackSpeed = null
        resetRemoteProgressAnchor()
    }

    private fun releaseSelectedSource() {
        sourceSession.release()
    }

    private fun startPlaybackStateObserver() {
        playbackCoordinator.startPlaybackStateObserver(intervalMs = 200L) { playState ->
            uiState = uiState.copy(playbackState = playState)
        }
    }

    private fun updateRemoteProgressAnchor(positionMs: Long) {
        remoteProgressAnchorPositionMs = positionMs
        remoteProgressAnchorElapsedRealtimeMs = elapsedRealtimeProvider()
    }

    private fun resetRemoteProgressAnchor() {
        remoteProgressAnchorPositionMs = 0L
        remoteProgressAnchorElapsedRealtimeMs = elapsedRealtimeProvider()
    }

    private suspend fun refreshPlaybackStateNow() {
        playbackCoordinator.refreshPlaybackState { playState ->
            uiState = uiState.copy(playbackState = playState)
        }
    }

    private fun advanceToNextAfterCompletion(): Boolean {
        val oldState = playlistSession.state
        val nextState = playlistSession.moveToNext()
        if (nextState == oldState) {
            return false
        }

        syncSelectionFromPlaylist()
        sourceSession.setAutoPlayWhenPrepared(true)
        uiState = uiState.copy(statusText = "Track finished, preparing next...")
        prepareActiveItem(stopPlayback = false)
        return true
    }

    private companion object {
        private const val TAG = "PlayerRuntime"
    }
}
