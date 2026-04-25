package com.wxy.playerlite.feature.player.runtime

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.feature.player.model.PlayerAudioQualityCatalogUiState
import com.wxy.playerlite.feature.player.model.PlayerLyricUiState
import com.wxy.playerlite.feature.player.model.PlayerMoreActionsPage
import com.wxy.playerlite.feature.player.model.PlayerOrientationMode
import com.wxy.playerlite.feature.player.model.PlayerTopTab
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.model.emptyAudioMeta
import com.wxy.playerlite.feature.player.model.withAudioQuality
import com.wxy.playerlite.feature.player.model.withAudioEffectPreset
import com.wxy.playerlite.feature.player.model.withPlaybackSpeed
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.orchestrator.AudioEffectPresetSyncResolver
import com.wxy.playerlite.playback.orchestrator.PlaybackRuntimePort
import com.wxy.playerlite.playback.orchestrator.PlaybackSpeedSyncResolver
import com.wxy.playerlite.playback.session.SharedPreferencesPlaybackSessionStateStorage
import com.wxy.playerlite.playlist.core.PlaylistController
import com.wxy.playerlite.playlist.core.PlaylistSessionCoordinator
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.PlaybackOutputInfo
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class PlayerRuntime(
    appContext: Context,
    private val audioEffectPresetStorage: AudioEffectPresetStorage? = null,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) : PlaybackRuntimePort {
    private val mediaSourceRepository = MediaSourceRepository(appContext)
    private val playbackSessionStateStorage =
        SharedPreferencesPlaybackSessionStateStorage.fromContext(appContext)
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
    private var pendingPreferredAudioQuality: PlaybackAudioQuality? = null
    private var pendingAudioEffectPreset: AudioEffectPreset? = null
    private var lastRemotePlaybackIds: Set<String> = emptySet()
    private var pendingRemotePlaybackItemId: String? = null

    private var uiState: PlayerUiState
        get() = _uiState.value
        set(value) {
            _uiState.value = value
        }

    init {
        restoreAudioEffectPreset()
        restorePlaylistState()
        restorePlaybackSessionState()
    }

    fun onAudioPicked(uri: Uri?) {
        if (uri == null) {
            uiState = uiState.copy(statusText = "Selection canceled")
            return
        }
        addPickedUriToPlaylist(uri)
    }

    fun onTogglePlaylistSheet() {
        val nextVisible = !uiState.showPlaylistSheet
        uiState = uiState.copy(
            showPlaylistSheet = nextVisible,
            showMoreActionsSheet = if (nextVisible) false else uiState.showMoreActionsSheet,
            showAudioEffectPage = if (nextVisible) false else uiState.showAudioEffectPage,
            showAudioQualitySheet = if (nextVisible) false else uiState.showAudioQualitySheet,
            moreActionsPage = if (nextVisible) PlayerMoreActionsPage.ROOT else uiState.moreActionsPage
        )
    }

    fun onDismissPlaylistSheet() {
        uiState = uiState.copy(showPlaylistSheet = false)
    }

    fun onShowPlayerMoreActions() {
        uiState = uiState.copy(
            showPlaylistSheet = false,
            showMoreActionsSheet = true,
            showAudioEffectPage = false,
            showAudioQualitySheet = false,
            moreActionsPage = PlayerMoreActionsPage.ROOT
        )
    }

    fun onDismissPlayerMoreActions() {
        uiState = uiState.copy(
            showMoreActionsSheet = false,
            showAudioEffectPage = false,
            showAudioQualitySheet = false,
            moreActionsPage = PlayerMoreActionsPage.ROOT
        )
    }

    fun showPlaybackSpeedSettings() {
        uiState = uiState.copy(
            showMoreActionsSheet = true,
            showAudioEffectPage = false,
            showAudioQualitySheet = false,
            moreActionsPage = PlayerMoreActionsPage.SPEED
        )
    }

    fun showAudioEffectSettings() {
        uiState = uiState.copy(
            showMoreActionsSheet = false,
            showAudioEffectPage = true,
            showAudioQualitySheet = false,
            moreActionsPage = PlayerMoreActionsPage.ROOT
        )
    }

    fun showAudioQualitySettings() {
        uiState = uiState.copy(
            showMoreActionsSheet = false,
            showAudioEffectPage = false,
            showAudioQualitySheet = true,
            moreActionsPage = PlayerMoreActionsPage.ROOT
        )
    }

    fun returnToPlayerMoreActionsRoot() {
        uiState = uiState.copy(
            showMoreActionsSheet = true,
            showAudioEffectPage = false,
            showAudioQualitySheet = false,
            moreActionsPage = PlayerMoreActionsPage.ROOT
        )
    }

    fun dismissAudioEffectSettings() {
        uiState = uiState.copy(showAudioEffectPage = false)
    }

    fun dismissAudioQualitySettings() {
        uiState = uiState.copy(showAudioQualitySheet = false)
    }

    fun updateAudioQualityCatalogUiState(audioQualityCatalogUiState: PlayerAudioQualityCatalogUiState) {
        uiState = uiState.copy(audioQualityCatalogUiState = audioQualityCatalogUiState)
    }

    fun updateLyricUiState(lyricUiState: PlayerLyricUiState) {
        uiState = uiState.copy(lyricUiState = lyricUiState)
    }

    fun selectTopTab(topTab: PlayerTopTab) {
        uiState = uiState.copy(selectedTopTab = topTab)
    }

    fun setOrientationMode(orientationMode: PlayerOrientationMode) {
        if (uiState.orientationMode == orientationMode) {
            return
        }
        uiState = uiState.copy(orientationMode = orientationMode)
    }

    override fun updateLocalPlaybackMode(playbackMode: PlaybackMode) {
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

    override fun selectPlaylistItem(index: Int) {
        val target = playlistSession.itemAt(index) ?: return
        val previousActiveId = playlistSession.activeItem?.id
        if (playlistSession.activeIndex == index) {
            uiState = uiState.copy(
                showPlaylistSheet = false,
                showMoreActionsSheet = false,
                showAudioEffectPage = false,
                showAudioQualitySheet = false,
                moreActionsPage = PlayerMoreActionsPage.ROOT
            )
            return
        }

        playlistSession.setActiveIndex(index)
        syncSelectionFromPlaylist()
        if (previousActiveId != target.id) {
            pendingRemotePlaybackItemId = target.id
            resetPlaybackProjection()
        }
        uiState = uiState.copy(
            showPlaylistSheet = false,
            showMoreActionsSheet = false,
            showAudioEffectPage = false,
            showAudioQualitySheet = false,
            moreActionsPage = PlayerMoreActionsPage.ROOT,
            statusText = "已切换到: ${target.displayName}"
        )
    }

    override fun removePlaylistItem(index: Int) {
        val target = playlistSession.itemAt(index) ?: return
        val previousActiveId = playlistSession.activeItem?.id
        playlistSession.removeAt(index)
        syncSelectionFromPlaylist()

        if (playlistSession.items.isEmpty()) {
            resetPlaybackProjection()
            uiState = uiState.copy(
                statusText = "播放列表已清空",
                showPlaylistSheet = false,
                showMoreActionsSheet = false,
                showAudioEffectPage = false,
                showAudioQualitySheet = false,
                moreActionsPage = PlayerMoreActionsPage.ROOT
            )
            return
        }

        if (previousActiveId != playlistSession.activeItem?.id) {
            pendingRemotePlaybackItemId = playlistSession.activeItem?.id
            resetPlaybackProjection()
            uiState = uiState.copy(
                statusText = "已移除当前项",
                showPlaylistSheet = false,
                showMoreActionsSheet = false,
                showAudioEffectPage = false,
                showAudioQualitySheet = false,
                moreActionsPage = PlayerMoreActionsPage.ROOT
            )
            return
        }

        uiState = uiState.copy(
            statusText = "已移除: ${target.displayName}",
            showPlaylistSheet = false,
            showMoreActionsSheet = false,
            showAudioEffectPage = false,
            showAudioQualitySheet = false,
            moreActionsPage = PlayerMoreActionsPage.ROOT
        )
    }

    override fun clearPlaylist() {
        if (playlistSession.items.isEmpty()) {
            uiState = uiState.copy(
                statusText = "播放列表已清空",
                showPlaylistSheet = false,
                showMoreActionsSheet = false,
                showAudioEffectPage = false,
                showAudioQualitySheet = false,
                moreActionsPage = PlayerMoreActionsPage.ROOT
            )
            return
        }

        playlistSession.replaceAll(emptyList(), activeIndex = 0)
        pendingRemotePlaybackItemId = null
        syncSelectionFromPlaylist()
        resetPlaybackProjection()
        uiState = uiState.copy(
            statusText = "播放列表已清空",
            showPlaylistSheet = false,
            showMoreActionsSheet = false,
            showAudioEffectPage = false,
            moreActionsPage = PlayerMoreActionsPage.ROOT
        )
    }

    override fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) {
            return
        }
        if (!playlistSession.containsIndex(fromIndex) || !playlistSession.containsIndex(toIndex)) {
            return
        }

        playlistSession.moveItem(fromIndex, toIndex)
        syncSelectionFromPlaylist()
    }

    fun insertPlaylistItemNext(item: PlaylistItem): Boolean {
        val activeItem = playlistSession.activeItem ?: run {
            uiState = uiState.copy(statusText = "下一首播放失败：当前没有有效播放上下文")
            return false
        }
        val queueItem = item.copy(
            id = "${item.id}:next:${UUID.randomUUID()}"
        )
        playlistSession.insertAfterActive(queueItem)
        syncSelectionFromPlaylist()
        uiState = uiState.copy(
            statusText = "已加入下一首播放: ${queueItem.displayName}",
            showPlaylistSheet = false,
            showMoreActionsSheet = false,
            showAudioEffectPage = false,
            showAudioQualitySheet = false,
            moreActionsPage = PlayerMoreActionsPage.ROOT
        )
        return activeItem.id == playlistSession.activeItem?.id
    }

    override fun setStatusText(statusText: String) {
        uiState = uiState.copy(statusText = statusText)
    }

    override fun syncActiveItemById(itemId: String?) {
        if (itemId.isNullOrBlank()) {
            return
        }
        val pendingItemId = pendingRemotePlaybackItemId
        if (pendingItemId != null && pendingItemId != itemId) {
            return
        }
        if (playlistSession.activeItem?.id == itemId) {
            pendingRemotePlaybackItemId = null
            return
        }
        playlistSession.setActiveItemId(itemId)
        pendingRemotePlaybackItemId = null
        syncSelectionFromPlaylist()
    }

    override fun updateRemotePlaybackState(
        playbackState: Int,
        positionMs: Long,
        bufferedPositionMs: Long,
        durationMs: Long,
        isSeekSupported: Boolean,
        isPreparing: Boolean,
        playbackSpeed: Float,
        playbackMode: PlaybackMode,
        currentMediaId: String?,
        isProgressAdvancing: Boolean,
        currentPlayable: PlayableItemSnapshot?,
        playbackOutputInfo: PlaybackOutputInfo?,
        audioMeta: AudioMetaDisplay?,
        audioEffectPreset: AudioEffectPreset?,
        preferredAudioQuality: PlaybackAudioQuality?,
        appliedAudioQuality: PlaybackAudioQuality?,
        cacheProgress: PlaybackCacheProgressSnapshot?
    ) {
        val speedResolution = PlaybackSpeedSyncResolver.onRemoteUpdate(
            remoteSpeed = playbackSpeed,
            pendingSpeed = pendingPlaybackSpeed
        )
        pendingPlaybackSpeed = speedResolution.pendingSpeed
        val resolvedAudioEffectPreset = if (audioEffectPreset != null) {
            val audioEffectResolution = AudioEffectPresetSyncResolver.onRemoteUpdate(
                remotePreset = audioEffectPreset,
                pendingPreset = pendingAudioEffectPreset
            )
            pendingAudioEffectPreset = audioEffectResolution.pendingPreset
            audioEffectResolution.resolvedPreset
        } else {
            uiState.audioEffectPreset
        }
        val remoteProjection = resolveRemotePlaybackProjection(
            currentMediaId = currentMediaId,
            currentPlayable = currentPlayable
        )
        val currentRemotePlaybackIds = collectRemotePlaybackIds(
            currentMediaId = currentMediaId,
            currentPlayable = currentPlayable
        )
        val pendingItemId = pendingRemotePlaybackItemId
        val remoteMatchesPendingSelection = pendingItemId == null ||
            currentRemotePlaybackIds.contains(pendingItemId)
        if (pendingItemId != null && remoteMatchesPendingSelection) {
            pendingRemotePlaybackItemId = null
        }
        val remoteProjectionAuthoritative = remoteProjection.isAuthoritative &&
            remoteMatchesPendingSelection
        val sameRemotePlaybackIdentity = currentRemotePlaybackIds.isNotEmpty() &&
            currentRemotePlaybackIds.any(lastRemotePlaybackIds::contains)
        val localPlaybackMode = playlistSession.state.playbackMode
        val remoteModeAuthoritative = remoteProjectionAuthoritative
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
        val shouldApplyRemoteProgress = remoteProjectionAuthoritative
        val nextDuration = if (shouldApplyRemoteProgress && durationMs > 0L) {
            durationMs
        } else {
            uiState.durationMs
        }
        val remoteBoundedPosition = if (shouldApplyRemoteProgress && nextDuration > 0L) {
            positionMs.coerceIn(0L, nextDuration)
        } else if (shouldApplyRemoteProgress) {
            positionMs.coerceAtLeast(0L)
        } else {
            uiState.seekPositionMs
        }
        val remoteBufferedPosition = if (shouldApplyRemoteProgress && nextDuration > 0L) {
            bufferedPositionMs.coerceIn(0L, nextDuration)
        } else if (shouldApplyRemoteProgress) {
            bufferedPositionMs.coerceAtLeast(0L)
        } else {
            uiState.bufferedPositionMs
        }
        val currentProjectedPosition = uiState.displayedSeekMs
        val sameActiveQueueItem = remoteProjection.queueItem?.id == playlistSession.activeItem?.id
        val bounded = if (
            shouldApplyRemoteProgress &&
            sameActiveQueueItem &&
            isPreparing &&
            currentProjectedPosition > 0L &&
            remoteBoundedPosition < currentProjectedPosition
        ) {
            currentProjectedPosition
        } else if (
            shouldApplyRemoteProgress &&
            sameActiveQueueItem &&
            isProgressAdvancing &&
            !isPreparing &&
            playbackState == com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING &&
            uiState.playbackState == com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING &&
            remoteBoundedPosition < currentProjectedPosition &&
            currentProjectedPosition - remoteBoundedPosition <= MINOR_REMOTE_PROGRESS_REGRESSION_TOLERANCE_MS
        ) {
            currentProjectedPosition
        } else if (
            shouldApplyRemoteProgress &&
            sameActiveQueueItem &&
            isPreparing &&
            !isProgressAdvancing &&
            currentProjectedPosition > 0L &&
            remoteBoundedPosition == 0L
        ) {
            currentProjectedPosition
        } else {
            remoteBoundedPosition
        }
        val boundedBufferedPosition = if (shouldApplyRemoteProgress) {
            maxOf(bounded, remoteBufferedPosition)
        } else {
            uiState.bufferedPositionMs
        }
        val resolvedCacheProgress = if (shouldApplyRemoteProgress) {
            stabilizeRemoteCacheProgress(
                previous = uiState.cacheProgress,
                next = cacheProgress,
                sameRemotePlaybackIdentity = sameRemotePlaybackIdentity
            )
        } else {
            uiState.cacheProgress
        }
        remoteProgressShouldAdvance = shouldApplyRemoteProgress && isProgressAdvancing && !isPreparing
        if (shouldApplyRemoteProgress) {
            updateRemoteProgressAnchor(bounded)
        }
        val currentQueueItem = remoteProjection.queueItem
        uiState = uiState.copy(
            selectedFileName = if (remoteProjectionAuthoritative) {
                currentPlayable?.title?.takeIf { it.isNotBlank() }
                    ?: currentQueueItem?.displayName
                    ?: uiState.selectedFileName
            } else {
                uiState.selectedFileName
            },
            currentTrackTitle = if (remoteProjectionAuthoritative) {
                currentPlayable?.title?.takeIf { it.isNotBlank() }
                    ?: currentQueueItem?.effectiveTitle
                    ?: uiState.currentTrackTitle
            } else {
                uiState.currentTrackTitle
            },
            currentTrackArtist = if (remoteProjectionAuthoritative) {
                currentPlayable?.artistText?.takeIf { it.isNotBlank() }
                    ?: currentQueueItem?.artistText
                    ?: uiState.currentTrackArtist
            } else {
                uiState.currentTrackArtist
            },
            currentArtistId = if (remoteProjectionAuthoritative) {
                currentQueueItem?.primaryArtistId?.takeIf { it.isNotBlank() }
                    ?: uiState.currentArtistId
            } else {
                uiState.currentArtistId
            },
            currentCoverUrl = if (remoteProjectionAuthoritative) {
                currentPlayable?.coverUrl?.takeIf { it.isNotBlank() }
                    ?: currentQueueItem?.coverUrl?.takeIf { it.isNotBlank() }
                    ?: uiState.currentCoverUrl
            } else {
                uiState.currentCoverUrl
            },
            currentSongIdOverride = if (remoteProjectionAuthoritative) {
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
            bufferedPositionMs = boundedBufferedPosition,
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
            cacheProgress = resolvedCacheProgress,
            playbackMode = playlistSession.state.playbackMode
        ).withPlaybackSpeed(speedResolution.resolvedSpeed)
            .withAudioQuality(
                preferredAudioQuality = preferredAudioQuality ?: pendingPreferredAudioQuality
                    ?: uiState.preferredAudioQuality,
                appliedAudioQuality = appliedAudioQuality
            )
            .withAudioEffectPreset(resolvedAudioEffectPreset)
        if (preferredAudioQuality != null && preferredAudioQuality == pendingPreferredAudioQuality) {
            pendingPreferredAudioQuality = null
        }
        if (audioEffectPreset != null) {
            persistAudioEffectPreset(resolvedAudioEffectPreset)
        }
        if (remoteMatchesPendingSelection && currentRemotePlaybackIds.isNotEmpty()) {
            lastRemotePlaybackIds = currentRemotePlaybackIds
        }
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
        currentPlayable: PlayableItemSnapshot?,
        playbackOutputInfo: PlaybackOutputInfo?,
        audioMeta: AudioMetaDisplay?
    ) {
        updateRemotePlaybackState(
            playbackState = playbackState,
            positionMs = positionMs,
            bufferedPositionMs = positionMs,
            durationMs = durationMs,
            isSeekSupported = isSeekSupported,
            isPreparing = false,
            playbackSpeed = playbackSpeed,
            playbackMode = playbackMode,
            currentMediaId = currentMediaId,
            isProgressAdvancing = isProgressAdvancing,
            currentPlayable = currentPlayable,
            playbackOutputInfo = playbackOutputInfo,
            audioMeta = audioMeta,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = null
        )
    }

    fun updateRemotePlaybackState(
        playbackState: Int,
        positionMs: Long,
        durationMs: Long,
        isSeekSupported: Boolean,
        isPreparing: Boolean,
        playbackSpeed: Float,
        playbackMode: PlaybackMode,
        currentMediaId: String?,
        isProgressAdvancing: Boolean,
        currentPlayable: PlayableItemSnapshot?,
        playbackOutputInfo: PlaybackOutputInfo?,
        audioMeta: AudioMetaDisplay?
    ) {
        updateRemotePlaybackState(
            playbackState = playbackState,
            positionMs = positionMs,
            bufferedPositionMs = positionMs,
            durationMs = durationMs,
            isSeekSupported = isSeekSupported,
            isPreparing = isPreparing,
            playbackSpeed = playbackSpeed,
            playbackMode = playbackMode,
            currentMediaId = currentMediaId,
            isProgressAdvancing = isProgressAdvancing,
            currentPlayable = currentPlayable,
            playbackOutputInfo = playbackOutputInfo,
            audioMeta = audioMeta,
            audioEffectPreset = null,
            preferredAudioQuality = null,
            appliedAudioQuality = null,
            cacheProgress = null
        )
    }

    override fun updateLocalPlaybackSpeed(playbackSpeed: Float) {
        val speedResolution = PlaybackSpeedSyncResolver.onLocalRequest(
            requestedSpeed = playbackSpeed
        )
        pendingPlaybackSpeed = speedResolution.pendingSpeed
        uiState = uiState.withPlaybackSpeed(speedResolution.resolvedSpeed)
        updateRemoteProgressAnchor(uiState.displayedSeekMs)
    }

    override fun updateLocalPreferredAudioQuality(audioQuality: PlaybackAudioQuality) {
        pendingPreferredAudioQuality = audioQuality
        uiState = uiState.withAudioQuality(
            preferredAudioQuality = audioQuality,
            appliedAudioQuality = uiState.appliedAudioQuality
        ).copy(showAudioQualitySheet = false)
    }

    override fun revertPendingPreferredAudioQuality(audioQuality: PlaybackAudioQuality) {
        pendingPreferredAudioQuality = null
        uiState = uiState.withAudioQuality(
            preferredAudioQuality = audioQuality,
            appliedAudioQuality = uiState.appliedAudioQuality
        )
    }

    fun syncRemoteAudioQualityState(
        preferredAudioQuality: PlaybackAudioQuality?,
        appliedAudioQuality: PlaybackAudioQuality?
    ) {
        val resolvedPreferredAudioQuality = preferredAudioQuality ?: pendingPreferredAudioQuality
            ?: uiState.preferredAudioQuality
        if (preferredAudioQuality != null && preferredAudioQuality == pendingPreferredAudioQuality) {
            pendingPreferredAudioQuality = null
        }
        uiState = uiState.withAudioQuality(
            preferredAudioQuality = resolvedPreferredAudioQuality,
            appliedAudioQuality = appliedAudioQuality
        )
    }

    override fun revertPendingPlaybackSpeed(playbackSpeed: Float) {
        val speedResolution = PlaybackSpeedSyncResolver.onCommandRejected(playbackSpeed)
        pendingPlaybackSpeed = speedResolution.pendingSpeed
        uiState = uiState.withPlaybackSpeed(speedResolution.resolvedSpeed)
    }

    override fun updateLocalAudioEffectPreset(audioEffectPreset: AudioEffectPreset) {
        val effectResolution = AudioEffectPresetSyncResolver.onLocalRequest(audioEffectPreset)
        pendingAudioEffectPreset = effectResolution.pendingPreset
        uiState = uiState.withAudioEffectPreset(effectResolution.resolvedPreset)
            .copy(showAudioEffectPage = false, showAudioQualitySheet = false)
        persistAudioEffectPreset(effectResolution.resolvedPreset)
    }

    override fun revertPendingAudioEffectPreset(audioEffectPreset: AudioEffectPreset) {
        val effectResolution = AudioEffectPresetSyncResolver.onCommandRejected(audioEffectPreset)
        pendingAudioEffectPreset = effectResolution.pendingPreset
        uiState = uiState.withAudioEffectPreset(effectResolution.resolvedPreset)
        persistAudioEffectPreset(effectResolution.resolvedPreset)
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
        pendingPreferredAudioQuality = null
        pendingAudioEffectPreset = null
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
            bufferedPositionMs = 0L,
            seekDragPositionMs = 0L,
            isSeekDragging = false,
            appliedAudioQuality = null,
            cacheProgress = null,
            isPreparing = false,
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            showMoreActionsSheet = false,
            showAudioEffectPage = false,
            showAudioQualitySheet = false,
            moreActionsPage = PlayerMoreActionsPage.ROOT,
            lyricUiState = PlayerLyricUiState.Placeholder,
            selectedTopTab = PlayerTopTab.SONG,
            statusText = if (updateStatus) "Stopped" else uiState.statusText
        )
    }

    fun formatDuration(durationMs: Long): String {
        return PlayerUiFormatter.formatDuration(durationMs)
    }

    private fun stabilizeRemoteCacheProgress(
        previous: PlaybackCacheProgressSnapshot?,
        next: PlaybackCacheProgressSnapshot?,
        sameRemotePlaybackIdentity: Boolean
    ): PlaybackCacheProgressSnapshot? {
        if (!sameRemotePlaybackIdentity) {
            return next
        }
        if (previous == null) {
            return next
        }
        if (next == null) {
            return previous
        }
        if (previous.isFullyCached && !next.isFullyCached) {
            return previous
        }
        if (next.isFullyCached) {
            return next
        }
        if (next.normalizedDisplayRatio < previous.normalizedDisplayRatio) {
            return previous
        }
        if (
            next.normalizedDisplayRatio == previous.normalizedDisplayRatio &&
            next.cachedBytes < previous.cachedBytes
        ) {
            return previous
        }
        return next
    }

    override fun playbackQueueItems(): List<PlaylistItem> {
        return playlistSession.playbackItems
    }

    override fun playbackQueueActiveIndex(): Int {
        return playlistSession.playbackActiveIndex
    }

    override fun currentPlaybackMode(): PlaybackMode {
        return uiState.playbackMode
    }

    override fun playbackQueueItemsInOriginalOrder(): List<PlaylistItem> {
        return playlistSession.originalItems
    }

    override fun replaceQueueFromDetail(
        items: List<PlaylistItem>,
        activeIndex: Int
    ) {
        applyExternalQueueSelection(
            items = items,
            activeIndex = activeIndex
        )
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
            pendingRemotePlaybackItemId = playlistSession.activeItem?.id
            resetPlaybackProjection()
        }
        uiState = uiState.copy(
            showPlaylistSheet = false,
            showMoreActionsSheet = false,
            showAudioEffectPage = false,
            showAudioQualitySheet = false,
            moreActionsPage = PlayerMoreActionsPage.ROOT,
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

    override fun updatePlaylistItemsMetadata(
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

    private fun restorePlaybackSessionState() {
        val session = playbackSessionStateStorage.read() ?: return
        val restoredItem = playlistSession.originalItems.firstOrNull { it.id == session.activeItemId } ?: return
        if (playlistSession.activeItem?.id != restoredItem.id) {
            playlistSession.setActiveItemId(restoredItem.id)
            syncSelectionFromPlaylist()
        }
        val restoredPositionMs = restoredItem.durationMs
            .takeIf { it > 0L }
            ?.let { session.positionMs.coerceIn(0L, it) }
            ?: session.positionMs.coerceAtLeast(0L)
        remoteProgressShouldAdvance = false
        updateRemoteProgressAnchor(restoredPositionMs)
        uiState = uiState.copy(
            playbackState = AUDIO_TRACK_PLAYSTATE_PAUSED,
            isPreparing = false,
            durationMs = restoredItem.durationMs.coerceAtLeast(0L),
            seekPositionMs = restoredPositionMs,
            bufferedPositionMs = restoredPositionMs,
            seekDragPositionMs = restoredPositionMs,
            isSeekDragging = false,
            cacheProgress = null
        )
    }

    private fun restoreAudioEffectPreset() {
        val restoredPreset = audioEffectPresetStorage?.read() ?: return
        uiState = uiState.withAudioEffectPreset(restoredPreset)
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
            pendingRemotePlaybackItemId = item.id
            resetPlaybackProjection()
        }
        uiState = uiState.copy(statusText = "Added to playlist: $displayName")
    }

    private fun resetPlaybackProjection() {
        remoteProgressShouldAdvance = false
        pendingPreferredAudioQuality = null
        resetRemoteProgressAnchor()
        uiState = uiState.copy(
            audioMeta = emptyAudioMeta(),
            playbackOutputInfo = null,
            isSeekSupported = false,
            durationMs = 0L,
            seekPositionMs = 0L,
            bufferedPositionMs = 0L,
            seekDragPositionMs = 0L,
            isSeekDragging = false,
            appliedAudioQuality = null,
            cacheProgress = null,
            isPreparing = false,
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            showMoreActionsSheet = false,
            showAudioEffectPage = false,
            showAudioQualitySheet = false,
            moreActionsPage = PlayerMoreActionsPage.ROOT,
            lyricUiState = PlayerLyricUiState.Placeholder,
            audioQualityCatalogUiState = if (uiState.currentSongId.isNullOrBlank()) {
                PlayerAudioQualityCatalogUiState.Placeholder
            } else {
                PlayerAudioQualityCatalogUiState.Loading
            }
        )
    }

    private fun syncSelectionFromPlaylist(forceRefreshActiveMetadata: Boolean = false) {
        val activeItem = playlistSession.activeItem
        val previousActiveId = uiState.playlistItems
            .getOrNull(uiState.activePlaylistIndex)
            ?.id
        val activeChanged = previousActiveId != activeItem?.id
        val shouldRefreshActiveMetadata = activeChanged || forceRefreshActiveMetadata
        val previousSongId = uiState.currentSongId?.takeIf { it.isNotBlank() }
        val nextSongIdOverride = if (shouldRefreshActiveMetadata) {
            activeItem?.songId
        } else {
            uiState.currentSongIdOverride
        }
        val nextSongId = nextSongIdOverride?.takeIf { it.isNotBlank() }
            ?: activeItem?.songId?.takeIf { it.isNotBlank() }
        val songChanged = previousSongId != nextSongId
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
            lyricUiState = if (songChanged) {
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

    private fun persistAudioEffectPreset(audioEffectPreset: AudioEffectPreset) {
        audioEffectPresetStorage?.write(audioEffectPreset)
    }

    private fun resetRemoteProgressAnchor() {
        remoteProgressAnchorPositionMs = 0L
        remoteProgressAnchorElapsedRealtimeMs = elapsedRealtimeProvider()
    }

    private fun resolveRemotePlaybackProjection(
        currentMediaId: String?,
        currentPlayable: PlayableItemSnapshot?
    ): RemotePlaybackProjection {
        val remoteIds = collectRemotePlaybackIds(
            currentMediaId = currentMediaId,
            currentPlayable = currentPlayable
        )
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

    private fun collectRemotePlaybackIds(
        currentMediaId: String?,
        currentPlayable: PlayableItemSnapshot?
    ): LinkedHashSet<String> {
        return linkedSetOf<String>().apply {
            currentMediaId?.takeIf { it.isNotBlank() }?.let(::add)
            currentPlayable?.id?.takeIf { it.isNotBlank() }?.let(::add)
        }
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

private const val MINOR_REMOTE_PROGRESS_REGRESSION_TOLERANCE_MS = 450L

internal data class ExternalQueueSelectionResult(
    val replacedQueue: Boolean,
    val activeItemId: String?
)

private data class RemotePlaybackProjection(
    val queueItem: PlaylistItem?,
    val hasIdentity: Boolean,
    val isAuthoritative: Boolean
)
