package com.wxy.playerlite.feature.player.runtime

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.wxy.playerlite.core.playlist.PlaylistController
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.core.playlist.SharedPreferencesPlaylistStorage
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.feature.player.model.PlayerLyricUiState
import com.wxy.playerlite.feature.player.model.PlayerTopTab
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.model.emptyAudioMeta
import com.wxy.playerlite.feature.player.model.withPlaybackSpeed
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
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

    fun onShowSongWiki() {
        if (uiState.currentSongId.isNullOrBlank()) {
            return
        }
        uiState = uiState.copy(
            showSongWikiSheet = true
        )
    }

    fun onDismissSongWiki() {
        uiState = uiState.copy(showSongWikiSheet = false)
    }

    fun updateSongWikiUiState(songWikiUiState: com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState) {
        uiState = uiState.copy(songWikiUiState = songWikiUiState)
    }

    fun updateLyricUiState(lyricUiState: PlayerLyricUiState) {
        uiState = uiState.copy(lyricUiState = lyricUiState)
    }

    fun selectTopTab(topTab: PlayerTopTab) {
        uiState = uiState.copy(selectedTopTab = topTab)
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
                showPlaylistSheet = false,
                showSongWikiSheet = false
            )
            return
        }

        if (previousActiveId != playlistSession.activeItem?.id) {
            resetPlaybackProjection()
            uiState = uiState.copy(
                statusText = "已移除当前项",
                showPlaylistSheet = false,
                showSongWikiSheet = false
            )
            return
        }

        uiState = uiState.copy(
            statusText = "已移除: ${target.displayName}",
            showPlaylistSheet = false,
            showSongWikiSheet = false
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
        isPreparing: Boolean = false,
        playbackSpeed: Float,
        playbackMode: PlaybackMode,
        currentMediaId: String?,
        isProgressAdvancing: Boolean,
        currentPlayable: PlayableItemSnapshot?,
        playbackOutputInfo: PlaybackOutputInfo?,
        audioMeta: AudioMetaDisplay?
    ) {
        val speedResolution = PlaybackSpeedSyncResolver.onRemoteUpdate(
            remoteSpeed = playbackSpeed,
            pendingSpeed = pendingPlaybackSpeed
        )
        pendingPlaybackSpeed = speedResolution.pendingSpeed
        val remoteProjection = resolveRemotePlaybackProjection(
            currentMediaId = currentMediaId,
            currentPlayable = currentPlayable
        )
        val localPlaybackMode = playlistSession.state.playbackMode
        val remoteModeAuthoritative = remoteProjection.isAuthoritative
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
        val shouldApplyRemoteProgress = remoteProjection.isAuthoritative
        val nextDuration = if (shouldApplyRemoteProgress && durationMs > 0L) {
            durationMs
        } else {
            uiState.durationMs
        }
        val bounded = if (shouldApplyRemoteProgress && nextDuration > 0L) {
            positionMs.coerceIn(0L, nextDuration)
        } else if (shouldApplyRemoteProgress) {
            positionMs.coerceAtLeast(0L)
        } else {
            uiState.seekPositionMs
        }
        remoteProgressShouldAdvance = shouldApplyRemoteProgress && isProgressAdvancing
        if (shouldApplyRemoteProgress) {
            updateRemoteProgressAnchor(bounded)
        }
        val currentQueueItem = remoteProjection.queueItem
        uiState = uiState.copy(
            selectedFileName = if (remoteProjection.isAuthoritative) {
                currentPlayable?.title?.takeIf { it.isNotBlank() }
                    ?: currentQueueItem?.displayName
                    ?: uiState.selectedFileName
            } else {
                uiState.selectedFileName
            },
            currentTrackTitle = if (remoteProjection.isAuthoritative) {
                currentPlayable?.title?.takeIf { it.isNotBlank() }
                    ?: currentQueueItem?.effectiveTitle
                    ?: uiState.currentTrackTitle
            } else {
                uiState.currentTrackTitle
            },
            currentTrackArtist = if (remoteProjection.isAuthoritative) {
                currentPlayable?.artistText?.takeIf { it.isNotBlank() }
                    ?: currentQueueItem?.artistText
                    ?: uiState.currentTrackArtist
            } else {
                uiState.currentTrackArtist
            },
            currentArtistId = if (remoteProjection.isAuthoritative) {
                currentQueueItem?.primaryArtistId?.takeIf { it.isNotBlank() }
                    ?: uiState.currentArtistId
            } else {
                uiState.currentArtistId
            },
            currentCoverUrl = if (remoteProjection.isAuthoritative) {
                currentPlayable?.coverUrl?.takeIf { it.isNotBlank() }
                    ?: currentQueueItem?.coverUrl?.takeIf { it.isNotBlank() }
                    ?: uiState.currentCoverUrl
            } else {
                uiState.currentCoverUrl
            },
            currentSongIdOverride = if (remoteProjection.isAuthoritative) {
                currentPlayable?.songId?.takeIf { it.isNotBlank() }
                    ?: currentQueueItem?.songId?.takeIf { it.isNotBlank() }
                    ?: uiState.currentSongIdOverride
            } else {
                uiState.currentSongIdOverride
            },
            playbackState = playbackState,
            isPreparing = isPreparing,
            durationMs = nextDuration,
            isSeekSupported = if (shouldApplyRemoteProgress) isSeekSupported else uiState.isSeekSupported,
            seekPositionMs = if (uiState.isSeekDragging) uiState.seekPositionMs else bounded,
            seekDragPositionMs = if (uiState.isSeekDragging) uiState.seekDragPositionMs else bounded,
            playbackOutputInfo = if (shouldApplyRemoteProgress) {
                playbackOutputInfo
            } else {
                uiState.playbackOutputInfo
            },
            audioMeta = resolveAudioMeta(
                current = uiState.audioMeta,
                remote = if (shouldApplyRemoteProgress) audioMeta else null,
                reportedDurationMs = if (shouldApplyRemoteProgress) durationMs else uiState.durationMs,
                isSeekSupported = if (shouldApplyRemoteProgress) isSeekSupported else uiState.isSeekSupported
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
            currentTrackArtist = null,
            currentArtistId = null,
            currentCoverUrl = null,
            currentSongIdOverride = null,
            seekPositionMs = 0L,
            seekDragPositionMs = 0L,
            isSeekDragging = false,
            isPreparing = false,
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            showSongWikiSheet = false,
            lyricUiState = PlayerLyricUiState.Placeholder,
            selectedTopTab = PlayerTopTab.SONG,
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

    fun playbackQueueItemsInOriginalOrder(): List<PlaylistItem> {
        return playlistSession.originalItems
    }

    fun applyExternalQueueSelection(
        items: List<PlaylistItem>,
        activeIndex: Int
    ): ExternalQueueSelectionResult {
        if (items.isEmpty()) {
            uiState = uiState.copy(statusText = "播放失败：当前详情页没有可播放条目")
            return ExternalQueueSelectionResult(
                replacedQueue = false,
                activeItemId = null
            )
        }
        val normalizedIndex = activeIndex.coerceIn(0, items.lastIndex)
        val targetItemId = items[normalizedIndex].id
        val previousActiveId = playlistSession.activeItem?.id
        val replacedQueue = playlistSession.originalItems.map { it.id } != items.map { it.id }

        if (replacedQueue) {
            playlistSession.replaceAll(items, normalizedIndex)
        } else {
            playlistSession.setActiveItemId(targetItemId)
        }
        syncSelectionFromPlaylist()
        if (previousActiveId != playlistSession.activeItem?.id) {
            resetPlaybackProjection()
        }
        uiState = uiState.copy(
            showPlaylistSheet = false,
            showSongWikiSheet = false,
            statusText = if (replacedQueue) {
                "已替换播放列表"
            } else {
                "已切换到: ${playlistSession.activeItem?.displayName.orEmpty()}"
            }
        )
        return ExternalQueueSelectionResult(
            replacedQueue = replacedQueue,
            activeItemId = playlistSession.activeItem?.id
        )
    }

    fun updatePlaylistItemsMetadata(
        updatesById: Map<String, PlaylistItem>
    ) {
        if (updatesById.isEmpty()) {
            return
        }
        val previousState = playlistSession.state
        playlistSession.updateItemsById(updatesById)
        if (playlistSession.state == previousState) {
            return
        }
        syncSelectionFromPlaylist(forceRefreshActiveMetadata = true)
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
            displayName = displayName,
            title = displayName,
            itemType = PlaylistItemType.LOCAL
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
            isPreparing = false,
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            showSongWikiSheet = false,
            lyricUiState = PlayerLyricUiState.Placeholder
        )
    }

    private fun syncSelectionFromPlaylist(forceRefreshActiveMetadata: Boolean = false) {
        val activeItem = playlistSession.activeItem
        val previousActiveId = uiState.playlistItems
            .getOrNull(uiState.activePlaylistIndex)
            ?.id
        val activeChanged = previousActiveId != activeItem?.id
        val shouldRefreshActiveMetadata = activeChanged || forceRefreshActiveMetadata
        val canShowSongWiki = !activeItem?.songId.isNullOrBlank()
        uiState = uiState.copy(
            selectedFileName = if (shouldRefreshActiveMetadata) {
                activeItem?.displayName ?: "No audio selected"
            } else {
                uiState.selectedFileName
            },
            currentTrackTitle = if (shouldRefreshActiveMetadata) {
                activeItem?.effectiveTitle ?: "No audio selected"
            } else {
                uiState.currentTrackTitle
            },
            hasSelection = playlistSession.items.isNotEmpty(),
            playlistItems = playlistSession.items,
            activePlaylistIndex = playlistSession.activeIndex,
            currentTrackArtist = if (shouldRefreshActiveMetadata) {
                activeItem?.artistText
            } else {
                uiState.currentTrackArtist
            },
            currentArtistId = if (shouldRefreshActiveMetadata) {
                activeItem?.primaryArtistId
            } else {
                uiState.currentArtistId
            },
            currentCoverUrl = if (shouldRefreshActiveMetadata) {
                activeItem?.coverUrl
            } else {
                uiState.currentCoverUrl
            },
            currentSongIdOverride = if (shouldRefreshActiveMetadata) {
                activeItem?.songId
            } else {
                uiState.currentSongIdOverride
            },
            showPlaylistSheet = if (playlistSession.items.isEmpty()) false else uiState.showPlaylistSheet,
            showSongWikiSheet = if (activeChanged) {
                false
            } else {
                uiState.showSongWikiSheet && canShowSongWiki
            },
            songWikiUiState = if (activeChanged || !canShowSongWiki) {
                com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState.Placeholder
            } else {
                uiState.songWikiUiState
            },
            lyricUiState = if (activeChanged) {
                PlayerLyricUiState.Placeholder
            } else {
                uiState.lyricUiState
            },
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

    private fun resolveRemotePlaybackProjection(
        currentMediaId: String?,
        currentPlayable: PlayableItemSnapshot?
    ): RemotePlaybackProjection {
        val remoteIds = buildList {
            currentMediaId?.takeIf { it.isNotBlank() }?.let(::add)
            currentPlayable?.id?.takeIf { it.isNotBlank() }?.let { playableId ->
                if (playableId !in this) {
                    add(playableId)
                }
            }
        }
        val queueItem = remoteIds.firstNotNullOfOrNull { remoteId ->
            playlistSession.originalItems.firstOrNull { it.id == remoteId }
        }
        val hasIdentity = remoteIds.isNotEmpty()
        val isAuthoritative = queueItem != null || (playlistSession.originalItems.isEmpty() && hasIdentity)
        return RemotePlaybackProjection(
            queueItem = queueItem,
            hasIdentity = hasIdentity,
            isAuthoritative = isAuthoritative
        )
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

internal data class ExternalQueueSelectionResult(
    val replacedQueue: Boolean,
    val activeItemId: String?
)

private data class RemotePlaybackProjection(
    val queueItem: PlaylistItem?,
    val hasIdentity: Boolean,
    val isAuthoritative: Boolean
)
