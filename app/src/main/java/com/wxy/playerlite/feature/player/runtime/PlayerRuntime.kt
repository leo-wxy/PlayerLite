package com.wxy.playerlite.feature.player.runtime

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.wxy.playerlite.core.playlist.PlaylistController
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.SharedPreferencesPlaylistStorage
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.model.emptyAudioMeta
import com.wxy.playerlite.feature.player.model.withPlaybackSpeed
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.PlaybackOutputInfo
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class PlayerRuntime(
    appContext: Context,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val mediaSourceRepository = MediaSourceRepository(appContext)
    private val playlistSession = PlaylistSessionCoordinator(
        controller = PlaylistController(
            storage = SharedPreferencesPlaylistStorage(
                appContext.getSharedPreferences("playlist_state", Context.MODE_PRIVATE)
            ),
            scope = PlayerRuntimeRegistry.runtimeScope
        )
    )

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiStateFlow: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private var remoteProgressAnchorPositionMs: Long = 0L
    private var remoteProgressAnchorElapsedRealtimeMs: Long = 0L
    private var remoteProgressShouldAdvance: Boolean = false
    private var pendingPlaybackSpeed: Float? = null
    private var pendingPlaybackMode: PlaybackMode? = null

    private var uiState: PlayerUiState
        get() = _uiState.value
        set(value) {
            _uiState.value = value
        }

    init {
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

    fun updateLocalPlaybackMode(playbackMode: PlaybackMode) {
        pendingPlaybackMode = playbackMode
        playlistSession.setPlaybackMode(playbackMode)
        syncSelectionFromPlaylist()
    }

    fun revertPendingPlaybackMode(playbackMode: PlaybackMode) {
        pendingPlaybackMode = null
        playlistSession.setPlaybackMode(playbackMode)
        syncSelectionFromPlaylist()
    }

    fun setShowOriginalOrderInShuffle(show: Boolean) {
        playlistSession.setShowOriginalOrderInShuffle(show)
        syncSelectionFromPlaylist()
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

    fun onHostStop() {
        playlistSession.flush()
    }

    fun selectPlaylistItem(index: Int) {
        val target = playlistSession.itemAt(index) ?: return
        val previousActiveId = playlistSession.activeItem?.id
        if (playlistSession.activeIndex == index) {
            uiState = uiState.copy(showPlaylistSheet = false)
            return
        }

        playlistSession.setActiveIndex(index)
        syncSelectionFromPlaylist()
        if (previousActiveId != target.id) {
            resetPlaybackProjection()
        }
        uiState = uiState.copy(
            showPlaylistSheet = false,
            statusText = "已切换到: ${target.displayName}"
        )
    }

    fun removePlaylistItem(index: Int) {
        val target = playlistSession.itemAt(index) ?: return
        val previousActiveId = playlistSession.activeItem?.id
        playlistSession.removeAt(index)
        syncSelectionFromPlaylist()

        if (playlistSession.items.isEmpty()) {
            resetPlaybackProjection()
            uiState = uiState.copy(
                statusText = "播放列表已清空",
                showPlaylistSheet = false
            )
            return
        }

        if (previousActiveId != playlistSession.activeItem?.id) {
            resetPlaybackProjection()
            uiState = uiState.copy(
                statusText = "已移除当前项",
                showPlaylistSheet = false
            )
            return
        }

        uiState = uiState.copy(
            statusText = "已移除: ${target.displayName}",
            showPlaylistSheet = false
        )
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

    fun setStatusText(statusText: String) {
        uiState = uiState.copy(statusText = statusText)
    }

    fun syncActiveItemById(itemId: String?) {
        if (itemId.isNullOrBlank()) {
            return
        }
        if (playlistSession.activeItem?.id == itemId) {
            return
        }
        playlistSession.setActiveItemId(itemId)
        syncSelectionFromPlaylist()
    }

    fun updateRemotePlaybackState(
        playbackState: Int,
        positionMs: Long,
        durationMs: Long,
        isSeekSupported: Boolean,
        playbackSpeed: Float,
        playbackMode: PlaybackMode,
        currentMediaId: String?,
        isProgressAdvancing: Boolean,
        playbackOutputInfo: PlaybackOutputInfo?,
        audioMeta: AudioMetaDisplay?
    ) {
        val speedResolution = PlaybackSpeedSyncResolver.onRemoteUpdate(
            remoteSpeed = playbackSpeed,
            pendingSpeed = pendingPlaybackSpeed
        )
        pendingPlaybackSpeed = speedResolution.pendingSpeed
        val localPlaybackMode = playlistSession.state.playbackMode
        val remoteModeAuthoritative = !currentMediaId.isNullOrBlank()
        val resolvedPlaybackMode = when {
            pendingPlaybackMode != null && pendingPlaybackMode != playbackMode -> pendingPlaybackMode!!
            remoteModeAuthoritative -> playbackMode
            else -> localPlaybackMode
        }
        pendingPlaybackMode = if (remoteModeAuthoritative && resolvedPlaybackMode == playbackMode) {
            null
        } else {
            pendingPlaybackMode
        }
        playlistSession.setPlaybackMode(resolvedPlaybackMode)
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
            seekDragPositionMs = if (uiState.isSeekDragging) uiState.seekDragPositionMs else bounded,
            playbackOutputInfo = playbackOutputInfo,
            audioMeta = resolveAudioMeta(
                current = uiState.audioMeta,
                remote = audioMeta,
                reportedDurationMs = durationMs,
                isSeekSupported = isSeekSupported
            ),
            playbackMode = playlistSession.state.playbackMode
        ).withPlaybackSpeed(speedResolution.resolvedSpeed)
        syncSelectionFromPlaylist()
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

    fun stopAll(updateStatus: Boolean) {
        remoteProgressShouldAdvance = false
        pendingPlaybackSpeed = null
        pendingPlaybackMode = null
        resetRemoteProgressAnchor()
        uiState = uiState.copy(
            audioMeta = emptyAudioMeta(),
            playbackOutputInfo = null,
            isSeekSupported = false,
            durationMs = 0L,
            seekPositionMs = 0L,
            seekDragPositionMs = 0L,
            isSeekDragging = false,
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            statusText = if (updateStatus) "Stopped" else uiState.statusText
        )
    }

    fun formatDuration(durationMs: Long): String {
        return PlayerUiFormatter.formatDuration(durationMs)
    }

    fun playbackQueueItems(): List<PlaylistItem> {
        return playlistSession.playbackItems
    }

    fun playbackQueueActiveIndex(): Int {
        return playlistSession.playbackActiveIndex
    }

    private fun restorePlaylistState() {
        playlistSession.restore(mediaSourceRepository::isPlaylistItemReadable)
        syncSelectionFromPlaylist()
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
        val previousActiveId = playlistSession.activeItem?.id
        val item = PlaylistItem(
            id = UUID.randomUUID().toString(),
            uri = uri.toString(),
            displayName = displayName
        )
        playlistSession.addItem(item, makeActive = true)
        syncSelectionFromPlaylist()
        if (previousActiveId != item.id) {
            resetPlaybackProjection()
        }
        uiState = uiState.copy(statusText = "Added to playlist: $displayName")
    }

    private fun resetPlaybackProjection() {
        remoteProgressShouldAdvance = false
        resetRemoteProgressAnchor()
        uiState = uiState.copy(
            audioMeta = emptyAudioMeta(),
            playbackOutputInfo = null,
            isSeekSupported = false,
            durationMs = 0L,
            seekPositionMs = 0L,
            seekDragPositionMs = 0L,
            isSeekDragging = false,
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED
        )
    }

    private fun syncSelectionFromPlaylist() {
        val activeItem = playlistSession.activeItem
        uiState = uiState.copy(
            selectedFileName = activeItem?.displayName ?: "No audio selected",
            hasSelection = playlistSession.items.isNotEmpty(),
            playlistItems = playlistSession.items,
            activePlaylistIndex = playlistSession.activeIndex,
            showPlaylistSheet = if (playlistSession.items.isEmpty()) false else uiState.showPlaylistSheet,
            playbackMode = playlistSession.state.playbackMode,
            showOriginalOrderInShuffle = playlistSession.state.showOriginalOrderInShuffle,
            canReorderPlaylist = playlistSession.canReorderCurrentView
        )
    }

    private fun updateRemoteProgressAnchor(positionMs: Long) {
        remoteProgressAnchorPositionMs = positionMs
        remoteProgressAnchorElapsedRealtimeMs = elapsedRealtimeProvider()
    }

    private fun resetRemoteProgressAnchor() {
        remoteProgressAnchorPositionMs = 0L
        remoteProgressAnchorElapsedRealtimeMs = elapsedRealtimeProvider()
    }

    private fun resolveAudioMeta(
        current: AudioMetaDisplay,
        remote: AudioMetaDisplay?,
        reportedDurationMs: Long,
        isSeekSupported: Boolean
    ): AudioMetaDisplay {
        return remote ?: if (reportedDurationMs <= 0L && !isSeekSupported) emptyAudioMeta() else current
    }
}
