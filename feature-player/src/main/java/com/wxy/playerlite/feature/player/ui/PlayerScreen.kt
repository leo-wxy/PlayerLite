package com.wxy.playerlite.feature.player.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.ScreenRotationAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import androidx.core.view.WindowInsetsControllerCompat
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.designsystem.theme.PlayerLiteThemeContract
import com.wxy.playerlite.designsystem.theme.PlayerLiteVisualTheme
import com.wxy.playerlite.feature.player.LyricLine
import com.wxy.playerlite.feature.player.resolveActiveLyricLineProjection
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.feature.player.model.PlayerAudioQualityCatalogUiState
import com.wxy.playerlite.feature.player.model.PlayerLyricUiState
import com.wxy.playerlite.feature.player.model.PlayerMoreActionsPage
import com.wxy.playerlite.feature.player.model.PlayerOrientationMode
import com.wxy.playerlite.feature.player.model.PlayerTopTab
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.model.resolvePlayerOrientationToggleTarget
import com.wxy.playerlite.feature.player.ui.components.PlaybackControls
import com.wxy.playerlite.feature.player.ui.components.PlayerMoreActionsSheet
import com.wxy.playerlite.feature.player.ui.components.PlaylistBottomSheet
import com.wxy.playerlite.feature.player.ui.components.modeContentDescription
import com.wxy.playerlite.feature.player.ui.components.modeIcon
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.PlaybackSpeed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

val PlayerLyricsFirstVisibleIndexKey =
    SemanticsPropertyKey<Int>("PlayerLyricsFirstVisibleIndex")

val PlayerTopTabTextSizeSpKey =
    SemanticsPropertyKey<Float>("PlayerTopTabTextSizeSp")

val PlayerTopTabFontWeightKey =
    SemanticsPropertyKey<Int>("PlayerTopTabFontWeight")

val PlayerTopTabHorizontalPaddingDpKey =
    SemanticsPropertyKey<Float>("PlayerTopTabHorizontalPaddingDp")

val PlayerTopTabIndicatorHeightDpKey =
    SemanticsPropertyKey<Float>("PlayerTopTabIndicatorHeightDp")

internal var SemanticsPropertyReceiver.playerLyricsFirstVisibleIndex by
    PlayerLyricsFirstVisibleIndexKey

internal var SemanticsPropertyReceiver.playerTopTabTextSizeSp by
    PlayerTopTabTextSizeSpKey

internal var SemanticsPropertyReceiver.playerTopTabFontWeight by
    PlayerTopTabFontWeightKey

internal var SemanticsPropertyReceiver.playerTopTabHorizontalPaddingDp by
    PlayerTopTabHorizontalPaddingDpKey

internal var SemanticsPropertyReceiver.playerTopTabIndicatorHeightDp by
    PlayerTopTabIndicatorHeightDpKey

@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    currentDurationText: String,
    totalDurationText: String,
    currentArtistId: String? = uiState.currentArtistId
        ?.takeIf { it.isNotBlank() }
        ?: uiState.playlistItems
            .getOrNull(uiState.activePlaylistIndex)
            ?.primaryArtistId
            ?.takeIf { it.isNotBlank() },
    canOpenSongDetail: Boolean = uiState.playlistItems
        .getOrNull(uiState.activePlaylistIndex)
        ?.let { item ->
            !(
                uiState.currentSongId.isNullOrBlank() &&
                    item.songId.isNullOrBlank() &&
                    item.uri.isBlank()
                )
        }
        ?: false,
    enableEnterMotion: Boolean = true,
    modifier: Modifier = Modifier,
    callbacks: PlayerScreenCallbacks
) {
    PlayerScreen(
        fileName = uiState.currentTrackTitle,
        artistText = uiState.currentTrackArtist,
        status = uiState.statusText,
        hasSelection = uiState.hasSelection,
        playlistItems = uiState.playlistItems,
        activePlaylistIndex = uiState.activePlaylistIndex,
        showPlaylistSheet = uiState.showPlaylistSheet,
        showMoreActionsSheet = uiState.showMoreActionsSheet,
        showAudioEffectPage = uiState.showAudioEffectPage,
        showAudioQualitySheet = uiState.showAudioQualitySheet,
        moreActionsPage = uiState.moreActionsPage,
        lyricUiState = uiState.lyricUiState,
        selectedTopTab = uiState.selectedTopTab,
        orientationMode = uiState.orientationMode,
        isPreparing = uiState.isPreparing,
        playbackState = uiState.playbackState,
        isSeekSupported = uiState.isSeekSupported,
        playbackSpeed = uiState.playbackSpeed,
        playbackMode = uiState.playbackMode,
        preferredAudioQuality = uiState.preferredAudioQuality,
        appliedAudioQuality = uiState.appliedAudioQuality,
        audioQualityCatalogUiState = uiState.audioQualityCatalogUiState,
        audioEffectPreset = uiState.audioEffectPreset,
        canSkipPrevious = uiState.canSkipPrevious,
        canSkipNext = uiState.canSkipNext,
        showOriginalOrderInShuffle = uiState.showOriginalOrderInShuffle,
        canReorderPlaylist = uiState.canReorderPlaylist,
        seekValueMs = uiState.displayedSeekMs,
        cacheProgressStart = uiState.displayedCacheProgressStartRatio,
        cacheProgress = uiState.displayedCacheProgressRatio,
        currentDurationText = currentDurationText,
        durationMs = uiState.durationMs,
        totalDurationText = totalDurationText,
        currentSongId = uiState.currentSongId,
        currentArtistId = currentArtistId,
        currentCoverUrl = uiState.currentCoverUrl,
        canOpenSongDetail = canOpenSongDetail,
        enableEnterMotion = enableEnterMotion,
        modifier = modifier,
        onPickAudio = callbacks.onPickAudio,
        onTogglePlaylistSheet = callbacks.onTogglePlaylistSheet,
        onDismissPlaylistSheet = callbacks.onDismissPlaylistSheet,
        onRetryLyrics = callbacks.onRetryLyrics,
        onSelectTopTab = callbacks.onSelectTopTab,
        onCycleOrientationMode = callbacks.onCycleOrientationMode,
        onSelectPlaylistItem = callbacks.onSelectPlaylistItem,
        onClearPlaylist = callbacks.onClearPlaylist,
        onRemovePlaylistItem = callbacks.onRemovePlaylistItem,
        onOpenQueueSongDetail = callbacks.onOpenQueueSongDetail,
        onOpenQueueArtist = callbacks.onOpenQueueArtist,
        onOpenQueueAlbum = callbacks.onOpenQueueAlbum,
        onMovePlaylistItem = callbacks.onMovePlaylistItem,
        onPlay = callbacks.onPlay,
        onPrevious = callbacks.onPrevious,
        onNext = callbacks.onNext,
        onPause = callbacks.onPause,
        onResume = callbacks.onResume,
        onCyclePlaybackMode = callbacks.onCyclePlaybackMode,
        onShowOriginalOrderInShuffleChange = callbacks.onShowOriginalOrderInShuffleChange,
        onSeekValueChange = callbacks.onSeekValueChange,
        onSeekFinished = callbacks.onSeekFinished,
        onDismissMoreActionsSheet = callbacks.onDismissMoreActionsSheet,
        onDismissAudioEffectPage = callbacks.onDismissAudioEffectPage,
        onDismissAudioQualitySheet = callbacks.onDismissAudioQualitySheet,
        onShowPlaybackSpeedSettings = callbacks.onShowPlaybackSpeedSettings,
        onShowAudioEffectSettings = callbacks.onShowAudioEffectSettings,
        onShowAudioQualitySettings = callbacks.onShowAudioQualitySettings,
        onReturnToMoreActionsRoot = callbacks.onReturnToMoreActionsRoot,
        onSelectPlaybackSpeed = callbacks.onSelectPlaybackSpeed,
        onSelectAudioQuality = callbacks.onSelectAudioQuality,
        onSelectAudioEffectPreset = callbacks.onSelectAudioEffectPreset,
        onBackClick = callbacks.onBackClick,
        onOpenSongDetail = callbacks.onOpenSongDetail,
        onShareClick = callbacks.onShareClick,
        onArtistClick = callbacks.onArtistClick,
        onFavoriteClick = callbacks.onFavoriteClick,
        onMoreClick = callbacks.onMoreClick
    )
}

@Composable
fun PlayerScreen(
    fileName: String,
    artistText: String? = null,
    status: String,
    hasSelection: Boolean,
    playlistItems: List<PlaylistItem>,
    activePlaylistIndex: Int,
    showPlaylistSheet: Boolean,
    showMoreActionsSheet: Boolean = false,
    showAudioEffectPage: Boolean = false,
    showAudioQualitySheet: Boolean = false,
    moreActionsPage: PlayerMoreActionsPage = PlayerMoreActionsPage.ROOT,
    lyricUiState: PlayerLyricUiState = PlayerLyricUiState.Placeholder,
    selectedTopTab: PlayerTopTab = PlayerTopTab.SONG,
    orientationMode: PlayerOrientationMode = PlayerOrientationMode.AUTO,
    isPreparing: Boolean,
    playbackState: Int,
    isSeekSupported: Boolean,
    playbackSpeed: Float = PlaybackSpeed.DEFAULT.value,
    playbackMode: PlaybackMode,
    preferredAudioQuality: PlaybackAudioQuality = PlaybackAudioQuality.EXHIGH,
    appliedAudioQuality: PlaybackAudioQuality? = null,
    audioQualityCatalogUiState: PlayerAudioQualityCatalogUiState = PlayerAudioQualityCatalogUiState.Placeholder,
    audioEffectPreset: AudioEffectPreset = AudioEffectPreset.DEFAULT,
    canSkipPrevious: Boolean = playlistItems.size > 1,
    canSkipNext: Boolean = playlistItems.size > 1,
    showOriginalOrderInShuffle: Boolean,
    canReorderPlaylist: Boolean,
    seekValueMs: Long,
    cacheProgressStart: Float? = null,
    cacheProgress: Float? = null,
    currentDurationText: String,
    durationMs: Long,
    totalDurationText: String,
    currentSongId: String? = playlistItems
        .getOrNull(activePlaylistIndex)
        ?.songId
        ?.takeIf { it.isNotBlank() },
    currentArtistId: String? = playlistItems
        .getOrNull(activePlaylistIndex)
        ?.primaryArtistId
        ?.takeIf { it.isNotBlank() },
    currentCoverUrl: String? = playlistItems
        .getOrNull(activePlaylistIndex)
        ?.coverUrl
        ?.takeIf { it.isNotBlank() },
    canOpenSongDetail: Boolean = playlistItems
        .getOrNull(activePlaylistIndex)
        ?.let { item ->
            !(currentSongId.isNullOrBlank() && item.songId.isNullOrBlank() && item.uri.isBlank())
        }
        ?: false,
    enableEnterMotion: Boolean = true,
    modifier: Modifier = Modifier,
    onPickAudio: () -> Unit,
    onTogglePlaylistSheet: () -> Unit,
    onDismissPlaylistSheet: () -> Unit,
    onRetryLyrics: () -> Unit = {},
    onSelectTopTab: ((PlayerTopTab) -> Unit)? = null,
    onCycleOrientationMode: (PlayerOrientationMode) -> Unit = {},
    onSelectPlaylistItem: (Int) -> Unit,
    onClearPlaylist: () -> Unit = {},
    onRemovePlaylistItem: (Int) -> Unit,
    onOpenQueueSongDetail: (PlaylistItem) -> Unit = {},
    onOpenQueueArtist: (String) -> Unit = {},
    onOpenQueueAlbum: (String) -> Unit = {},
    onMovePlaylistItem: (Int, Int) -> Unit,
    onPlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onShowOriginalOrderInShuffleChange: (Boolean) -> Unit,
    onSeekValueChange: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onDismissMoreActionsSheet: () -> Unit = {},
    onDismissAudioEffectPage: () -> Unit = {},
    onDismissAudioQualitySheet: () -> Unit = {},
    onShowPlaybackSpeedSettings: () -> Unit = {},
    onShowAudioEffectSettings: () -> Unit = {},
    onShowAudioQualitySettings: () -> Unit = {},
    onReturnToMoreActionsRoot: () -> Unit = {},
    onSelectPlaybackSpeed: (Float) -> Unit = {},
    onSelectAudioQuality: (PlaybackAudioQuality) -> Unit = {},
    onSelectAudioEffectPreset: (AudioEffectPreset) -> Unit = {},
    onBackClick: () -> Unit = {},
    onOpenSongDetail: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onArtistClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onMoreClick: () -> Unit = {}
) {
    val visualTokens = PlayerLiteVisualTheme.colors
    val isPlaying = playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING
    val isPaused = playbackState == AUDIO_TRACK_PLAYSTATE_PAUSED
    val hasKnownDuration = durationMs > 0L
    val resolvedCoverUrl = currentCoverUrl?.takeIf { it.isNotBlank() }
    val resolvedArtistId = currentArtistId?.takeIf { it.isNotBlank() }
    val sliderMax = if (hasKnownDuration) {
        durationMs.toFloat()
    } else {
        maxOf(300_000L, seekValueMs + 30_000L).toFloat()
    }
    val combinedStatusUi = remember(
        preferredAudioQuality,
        appliedAudioQuality,
        audioEffectPreset,
        isPreparing,
        audioQualityCatalogUiState
    ) {
        val audioQualityLabel = when {
            appliedAudioQuality != null -> appliedAudioQuality.displayName
            isPreparing && audioQualityCatalogUiState is PlayerAudioQualityCatalogUiState.Content ->
                "${preferredAudioQuality.displayName}..."
            else -> null
        }
        val audioEffectLabel = audioEffectPreset
            .takeIf { it != AudioEffectPreset.DEFAULT }
            ?.displayName
        if (audioQualityLabel == null && audioEffectLabel == null) {
            null
        } else {
            com.wxy.playerlite.feature.player.model.PlayerCombinedStatusUi(
                audioQualityLabel = audioQualityLabel,
                audioEffectLabel = audioEffectLabel
            )
        }
    }
    val sliderValue = seekValueMs.coerceIn(0L, sliderMax.toLong()).toFloat()
    val seekEnabled = isSeekSupported && (isPlaying || isPaused)
    var backdropColor by remember {
        mutableStateOf(Color(0xFF171A21))
    }
    LaunchedEffect(currentSongId, resolvedCoverUrl) {
        if (currentSongId == null) {
            backdropColor = Color(0xFF171A21)
        }
    }
    val animatedBackdropColor by animateColorAsState(
        targetValue = backdropColor,
        animationSpec = tween(durationMillis = 300),
        label = "player_backdrop_color"
    )
    var localSelectedTopTab by rememberSaveable { mutableStateOf(selectedTopTab) }
    val effectiveSelectedTopTab = if (onSelectTopTab != null) {
        selectedTopTab
    } else {
        localSelectedTopTab
    }
    val handleSelectTopTab: (PlayerTopTab) -> Unit = { nextTab ->
        onSelectTopTab?.invoke(nextTab) ?: run {
            localSelectedTopTab = nextTab
        }
    }

    var reveal by remember(enableEnterMotion) { mutableStateOf(!enableEnterMotion) }
    LaunchedEffect(Unit) {
        if (enableEnterMotion) {
            reveal = true
        }
    }
    val motionState = PlayerScreenMotionSpec.resolve(
        enableEnterMotion = enableEnterMotion,
        hasRevealed = reveal
    )
    val showSettingsSheet = showAudioEffectPage || showAudioQualitySheet
    val contentAlpha by animateFloatAsState(
        targetValue = motionState.alpha,
        animationSpec = tween(durationMillis = 550),
        label = "content_alpha"
    )
    val contentOffset by animateDpAsState(
        targetValue = motionState.offsetDp.dp,
        animationSpec = tween(durationMillis = 550),
        label = "content_offset"
    )
    PlayerStatusBarStyleEffect(backdropColor = backdropColor)

    PlayerScreenBackground(
        backdropColor = animatedBackdropColor,
        coverUrl = resolvedCoverUrl,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffset.toPx()
            }
    ) {
        if (!hasSelection) {
            PlayerScreenEmptyState(
                status = status,
                onPickAudio = onPickAudio,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PlayerScreenContent(
                fileName = fileName,
                artistText = artistText,
                status = status,
                coverUrl = resolvedCoverUrl,
                showSongDetailAction = canOpenSongDetail,
                lyricUiState = lyricUiState,
                selectedTopTab = effectiveSelectedTopTab,
                orientationMode = orientationMode,
                isPreparing = isPreparing,
                isPlaying = isPlaying,
                isPaused = isPaused,
                hasPreviousTrack = canSkipPrevious,
                hasNextTrack = canSkipNext,
                playlistItemCount = playlistItems.size,
                playbackMode = playbackMode,
                sliderValue = sliderValue,
                sliderMax = sliderMax,
                cacheProgressStart = cacheProgressStart,
                cacheProgress = cacheProgress,
                seekEnabled = seekEnabled,
                currentPositionMs = seekValueMs,
                currentDurationText = currentDurationText,
                totalDurationText = totalDurationText,
                currentSongId = currentSongId,
                currentArtistId = resolvedArtistId,
                combinedStatusUi = combinedStatusUi,
                showInlineCombinedStatusRow = !showSettingsSheet,
                onSeekValueChange = onSeekValueChange,
                onSeekFinished = onSeekFinished,
                onBackClick = onBackClick,
                onOpenSongDetail = onOpenSongDetail,
                onShareClick = onShareClick,
                onArtistClick = onArtistClick,
                onPlay = onPlay,
                onPrevious = onPrevious,
                onNext = onNext,
                onPause = onPause,
                onResume = onResume,
                onCyclePlaybackMode = onCyclePlaybackMode,
                onTogglePlaylistSheet = onTogglePlaylistSheet,
                onRetryLyrics = onRetryLyrics,
                onSelectTopTab = handleSelectTopTab,
                onCycleOrientationMode = onCycleOrientationMode,
                onBackdropColorChange = { backdropColor = it },
                onShowAudioQualitySettings = onShowAudioQualitySettings,
                onShowAudioEffectSettings = onShowAudioEffectSettings,
                onFavoriteClick = onFavoriteClick,
                onMoreClick = onMoreClick,
                visualTokens = visualTokens,
                modifier = Modifier.fillMaxSize()
            )

            PlaylistBottomSheet(
                visible = showPlaylistSheet,
                items = playlistItems,
                activeIndex = activePlaylistIndex,
                playbackMode = playbackMode,
                showOriginalOrderInShuffle = showOriginalOrderInShuffle,
                canReorder = canReorderPlaylist,
                onDismiss = onDismissPlaylistSheet,
                onCyclePlaybackMode = onCyclePlaybackMode,
                onShowOriginalOrderInShuffleChange = onShowOriginalOrderInShuffleChange,
                onSelect = onSelectPlaylistItem,
                onClearAll = onClearPlaylist,
                onRemove = onRemovePlaylistItem,
                onOpenSongDetail = onOpenQueueSongDetail,
                onOpenArtist = onOpenQueueArtist,
                onOpenAlbum = onOpenQueueAlbum,
                onMove = onMovePlaylistItem,
                modifier = Modifier.testTag("player_screen_playlist_sheet")
            )

            PlayerMoreActionsSheet(
                visible = showMoreActionsSheet,
                page = moreActionsPage,
                playbackSpeed = playbackSpeed,
                audioEffectPreset = audioEffectPreset,
                onDismiss = onDismissMoreActionsSheet,
                onShowPlaybackSpeedSettings = onShowPlaybackSpeedSettings,
                onShowAudioEffectSettings = onShowAudioEffectSettings,
                onReturnToRoot = onReturnToMoreActionsRoot,
                onSelectPlaybackSpeed = onSelectPlaybackSpeed,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            PlayerAudioEffectPage(
                visible = showAudioEffectPage && !showAudioQualitySheet,
                audioEffectPreset = audioEffectPreset,
                onDismiss = onDismissAudioEffectPage,
                onSelectAudioEffectPreset = onSelectAudioEffectPreset,
                modifier = Modifier.fillMaxSize()
            )

            PlayerAudioQualitySheet(
                visible = showAudioQualitySheet && !showAudioEffectPage,
                preferredAudioQuality = preferredAudioQuality,
                appliedAudioQuality = appliedAudioQuality,
                isPreparing = isPreparing,
                catalogUiState = audioQualityCatalogUiState,
                onDismiss = onDismissAudioQualitySheet,
                onSelectAudioQuality = onSelectAudioQuality,
                modifier = Modifier.fillMaxSize()
            )

            if (showSettingsSheet && combinedStatusUi != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 72.dp, start = 16.dp, end = 16.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF14171D).copy(alpha = 0.94f),
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp
                ) {
                    PlayerCombinedStatusRow(
                        combinedStatusUi = combinedStatusUi,
                        onShowAudioQualitySettings = onShowAudioQualitySettings,
                        onShowAudioEffectSettings = onShowAudioEffectSettings,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("player_screen_combined_status_row")
                    )
                }
            }
    }
}
}
@Composable
private fun PlayerAudioEffectPage(
    visible: Boolean,
    audioEffectPreset: AudioEffectPreset,
    onDismiss: () -> Unit,
    onSelectAudioEffectPreset: (AudioEffectPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerSettingsBottomSheet(
        visible = visible,
        surfaceTag = "player_screen_audio_effect_sheet",
        title = "音效设置",
        subtitle = "选择后立即生效",
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        AudioEffectPreset.entries.forEach { preset ->
            val selected = preset == audioEffectPreset
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_screen_audio_effect_option_${preset.wireValue.replace('-', '_')}"),
                onClick = { onSelectAudioEffectPreset(preset) },
                shape = RoundedCornerShape(24.dp),
                color = if (selected) {
                    PlayerLiteVisualTheme.colors.accentStrong.copy(alpha = 0.12f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = preset.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) {
                            PlayerLiteVisualTheme.colors.accentStrong
                        } else {
                            Color.White
                        }
                    )
                    Text(
                        text = if (selected) "当前使用中" else "点击后立即切换",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.64f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerAudioQualitySheet(
    visible: Boolean,
    preferredAudioQuality: PlaybackAudioQuality,
    appliedAudioQuality: PlaybackAudioQuality?,
    isPreparing: Boolean,
    catalogUiState: PlayerAudioQualityCatalogUiState,
    onDismiss: () -> Unit,
    onSelectAudioQuality: (PlaybackAudioQuality) -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerSettingsBottomSheet(
        visible = visible,
        surfaceTag = "player_screen_audio_quality_sheet",
        title = "音质选择",
        subtitle = "仅展示当前歌曲真实可用档位",
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        when (catalogUiState) {
            PlayerAudioQualityCatalogUiState.Placeholder -> {
                PlayerSheetMessage(text = "等待当前歌曲音质目录")
            }

            PlayerAudioQualityCatalogUiState.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Text(
                        text = "音质目录加载中...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.84f)
                    )
                }
            }

            is PlayerAudioQualityCatalogUiState.Empty -> {
                PlayerSheetMessage(text = catalogUiState.message)
            }

            is PlayerAudioQualityCatalogUiState.Unsupported -> {
                PlayerSheetMessage(text = catalogUiState.message)
            }

            is PlayerAudioQualityCatalogUiState.Content -> {
                catalogUiState.catalog.options.forEach { option ->
                    val isRequested = option.quality == preferredAudioQuality
                    val isApplied = option.quality == appliedAudioQuality
                    val statusText = resolveAudioQualityOptionStatusText(
                        optionQuality = option.quality,
                        preferredAudioQuality = preferredAudioQuality,
                        appliedAudioQuality = appliedAudioQuality,
                        isPreparing = isPreparing
                    )
                    val borderStroke = when {
                        isRequested && !isApplied -> BorderStroke(
                            width = 1.dp,
                            color = PlayerLiteVisualTheme.colors.accentStrong.copy(alpha = 0.42f)
                        )

                        isApplied && !isRequested -> BorderStroke(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.18f)
                        )

                        else -> null
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (borderStroke == null) {
                                    Modifier
                                } else {
                                    Modifier.border(
                                        border = borderStroke,
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                }
                            )
                            .testTag("player_screen_audio_quality_option_${option.quality.wireValue}"),
                        onClick = { onSelectAudioQuality(option.quality) },
                        shape = RoundedCornerShape(24.dp),
                        color = when {
                            isRequested && isApplied -> {
                                PlayerLiteVisualTheme.colors.accentStrong.copy(alpha = 0.12f)
                            }

                            isRequested -> {
                                PlayerLiteVisualTheme.colors.accentStrong.copy(alpha = 0.10f)
                            }

                            isApplied -> {
                                Color.White.copy(alpha = 0.12f)
                            }

                            else -> {
                                Color.White.copy(alpha = 0.08f)
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = option.quality.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isRequested) {
                                    PlayerLiteVisualTheme.colors.accentStrong
                                } else {
                                    Color.White
                                }
                            )
                            Text(
                                text = listOfNotNull(
                                    statusText,
                                    formatAudioQualityOptionMeta(option)
                                ).joinToString(separator = " · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.64f)
                            )
                        }
                    }
                }
            }
    }
}
}

private fun resolveAudioQualityOptionStatusText(
    optionQuality: PlaybackAudioQuality,
    preferredAudioQuality: PlaybackAudioQuality,
    appliedAudioQuality: PlaybackAudioQuality?,
    isPreparing: Boolean
): String? {
    return when {
        optionQuality == preferredAudioQuality && optionQuality == appliedAudioQuality -> {
            "当前使用中"
        }

        optionQuality == preferredAudioQuality && appliedAudioQuality == null && isPreparing -> {
            "已选择，等待切换"
        }

        optionQuality == preferredAudioQuality && appliedAudioQuality == null -> {
            "已选择"
        }

        optionQuality == preferredAudioQuality && appliedAudioQuality != preferredAudioQuality -> {
            "已选择"
        }

        optionQuality == appliedAudioQuality && appliedAudioQuality != preferredAudioQuality -> {
            "当前实际使用"
        }

        else -> null
    }
}

@Composable
private fun PlayerSettingsBottomSheet(
    visible: Boolean,
    surfaceTag: String,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!visible) {
        return
    }
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.26f))
                .clickable(onClick = onDismiss)
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.58f)
                .testTag(surfaceTag),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = Color(0xFF14171D).copy(alpha = 0.98f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 42.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.size(40.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.68f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭设置浮层",
                            tint = Color.White
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun PlayerSheetMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun PlayerCombinedStatusRow(
    combinedStatusUi: com.wxy.playerlite.feature.player.model.PlayerCombinedStatusUi?,
    onShowAudioQualitySettings: () -> Unit,
    onShowAudioEffectSettings: () -> Unit,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    modifier: Modifier = Modifier
) {
    val state = combinedStatusUi ?: return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.audioQualityLabel?.let { audioQualityLabel ->
            Text(
                text = audioQualityLabel,
                style = textStyle,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(onClick = onShowAudioQualitySettings)
                    .testTag("player_screen_audio_quality_name")
            )
        }
        if (state.audioQualityLabel != null && state.audioEffectLabel != null) {
            Text(
                text = " · ",
                style = textStyle,
                color = Color.White.copy(alpha = 0.46f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
        state.audioEffectLabel?.let { audioEffectLabel ->
            Text(
                text = audioEffectLabel,
                style = textStyle,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(onClick = onShowAudioEffectSettings)
                    .testTag("player_screen_audio_effect_name")
            )
        }
    }
}

private fun formatAudioQualityOptionMeta(option: com.wxy.playerlite.playback.model.SongAudioQualityOption): String {
    val parts = buildList {
        if (option.bitRate > 0) {
            add("${option.bitRate / 1000} kbps")
        }
        option.sampleRate?.takeIf { it > 0 }?.let { sampleRate ->
            add("${sampleRate / 1000} kHz")
        }
        option.sizeBytes?.takeIf { it > 0L }?.let { sizeBytes ->
            add(formatBytesToMegabytes(sizeBytes))
        }
    }
    return if (parts.isEmpty()) {
        "当前歌曲可用音质"
    } else {
        parts.joinToString(separator = " · ")
    }
}

private fun formatBytesToMegabytes(sizeBytes: Long): String {
    val megaBytes = sizeBytes / 1_048_576.0
    return if (megaBytes >= 100) {
        "${megaBytes.toInt()} MB"
    } else {
        String.format("%.1f MB", megaBytes)
    }
}

@Composable
private fun PlayerScreenBackground(
    backdropColor: Color,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    var lastSuccessfulBackdropPainter by remember { mutableStateOf<Painter?>(null) }
    val middleTone = remember(backdropColor) {
        deriveBackdropGradientStop(
            baseColor = backdropColor,
            saturationScale = 0.90f,
            valueScale = 0.78f,
            minValue = 0.13f,
            maxValue = 0.20f
        )
    }
    val bottomTone = remember(backdropColor) {
        deriveBackdropGradientStop(
            baseColor = backdropColor,
            saturationScale = 0.82f,
            valueScale = 0.56f,
            minValue = 0.08f,
            maxValue = 0.14f
        )
    }
    Box(
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("player_screen_backdrop_base")
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            backdropColor,
                            middleTone,
                            bottomTone
                        )
                    )
                )
        )
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false)
                    .build(),
                transform = { state ->
                    val cachedPainter = lastSuccessfulBackdropPainter
                    when {
                        coverUrl.isNullOrBlank() -> state
                        cachedPainter == null -> state
                        state is AsyncImagePainter.State.Loading -> state.copy(painter = cachedPainter)
                        state is AsyncImagePainter.State.Error -> state.copy(painter = cachedPainter)
                        else -> state
                    }
                },
                onState = { state ->
                    val successState = state as? AsyncImagePainter.State.Success ?: return@AsyncImage
                    lastSuccessfulBackdropPainter = successState.painter
                },
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.44f
                        scaleX = 1.10f
                        scaleY = 1.10f
                    }
                    .blur(56.dp)
                    .testTag("player_screen_backdrop_cover_blur"),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("player_screen_backdrop_scrim")
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            backdropColor.copy(alpha = 0.34f),
                            middleTone.copy(alpha = 0.52f),
                            bottomTone.copy(alpha = 0.76f)
                        )
                    )
                )
        )
        content()
    }
}

fun extractBackdropColor(bitmap: Bitmap): Color {
    var redTotal = 0L
    var greenTotal = 0L
    var blueTotal = 0L
    var sampleCount = 0L
    val stepX = kotlin.math.max(1, bitmap.width / 12)
    val stepY = kotlin.math.max(1, bitmap.height / 12)

    var x = 0
    while (x < bitmap.width) {
        var y = 0
        while (y < bitmap.height) {
            val pixel = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) >= 48) {
                redTotal += android.graphics.Color.red(pixel)
                greenTotal += android.graphics.Color.green(pixel)
                blueTotal += android.graphics.Color.blue(pixel)
                sampleCount += 1
            }
            y += stepY
        }
        x += stepX
    }

    if (sampleCount == 0L) {
        return Color(0xFF171A21)
    }

    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (redTotal / sampleCount).toInt(),
        (greenTotal / sampleCount).toInt(),
        (blueTotal / sampleCount).toInt(),
        hsv
    )
    val originalSaturation = hsv[1]
    val originalValue = hsv[2]
    hsv[1] = when {
        originalSaturation < 0.14f -> originalSaturation.coerceIn(0.05f, 0.14f)
        originalSaturation < 0.45f -> (originalSaturation * 0.72f).coerceIn(0.14f, 0.30f)
        else -> (originalSaturation * 0.60f).coerceIn(0.32f, 0.50f)
    }
    hsv[2] = when {
        originalValue > 0.82f -> 0.20f
        originalValue > 0.55f -> (originalValue * 0.32f).coerceIn(0.16f, 0.24f)
        else -> (originalValue * 0.46f).coerceIn(0.14f, 0.22f)
    }
    return Color(android.graphics.Color.HSVToColor(hsv))
}

fun extractBackdropColorSafely(bitmap: Bitmap?): Color? {
    if (bitmap == null) {
        return null
    }
    return runCatching {
        val readableBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        if (readableBitmap.isRecycled) {
            return@runCatching null
        }
        extractBackdropColor(readableBitmap)
    }.getOrNull()
}

fun shouldUseLightStatusBarContent(backdropColor: Color): Boolean {
    return backdropColor.luminance() < 0.45f
}

@Composable
private fun PlayerStatusBarStyleEffect(
    backdropColor: Color
) {
    val view = LocalView.current
    val defaultUseLightStatusBarContent = isSystemInDarkTheme()
    DisposableEffect(view, backdropColor, defaultUseLightStatusBarContent) {
        val activity = view.context.findActivity()
        if (activity == null) {
            onDispose { }
        } else {
            val controller = WindowInsetsControllerCompat(activity.window, view)
            controller.isAppearanceLightStatusBars = !shouldUseLightStatusBarContent(backdropColor)
            onDispose {
                controller.isAppearanceLightStatusBars = !defaultUseLightStatusBarContent
            }
        }
    }
}

private fun deriveBackdropGradientStop(
    baseColor: Color,
    saturationScale: Float,
    valueScale: Float,
    minValue: Float,
    maxValue: Float
): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(baseColor.toArgb(), hsv)
    hsv[1] = (hsv[1] * saturationScale).coerceIn(0.08f, 0.42f)
    hsv[2] = (hsv[2] * valueScale).coerceIn(minValue, maxValue)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@Composable
private fun PlayerScreenTopBar(
    onBackClick: () -> Unit,
    onMoreClick: () -> Unit,
    layoutMetrics: PlayerScreenLayoutMetrics,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(layoutMetrics.topBarHeight)
            .testTag("player_screen_top_bar")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                PlayerTopBarActionButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回首页",
                    tag = "player_screen_top_back_button",
                    buttonSize = layoutMetrics.topBarActionButtonSize,
                    onClick = onBackClick
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .testTag("player_screen_top_actions"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerTopBarActionButton(
                    icon = Icons.Rounded.MoreHoriz,
                    contentDescription = "更多操作",
                    tag = "player_screen_top_more_button",
                    buttonSize = layoutMetrics.topBarActionButtonSize,
                    onClick = onMoreClick
                )
            }
        }
    }
}

@Composable
private fun PlayerLandscapeOverlayActions(
    orientationMode: PlayerOrientationMode,
    onCycleOrientationMode: (PlayerOrientationMode) -> Unit,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    layoutMetrics: PlayerScreenLayoutMetrics,
    modifier: Modifier = Modifier
) {
    val orientationTarget = remember(orientationMode) {
        resolvePlayerOrientationToggleTarget(
            currentMode = orientationMode,
            isCurrentlyLandscape = true
        )
    }
    val orientationAction = remember(orientationTarget) {
        resolvePlayerOrientationModeAction(orientationTarget)
    }
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 6.dp)
            .testTag("player_screen_landscape_overlay_actions"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerTopBarActionButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回首页",
            tag = "player_screen_top_back_button",
            buttonSize = layoutMetrics.topBarActionButtonSize,
            onClick = onBackClick
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerTopBarActionButton(
                icon = orientationAction.icon,
                contentDescription = orientationAction.contentDescription,
                tag = "player_screen_orientation_mode_button",
                buttonSize = layoutMetrics.topBarActionButtonSize,
                onClick = { onCycleOrientationMode(orientationTarget) }
            )
            PlayerTopBarActionButton(
                icon = Icons.Rounded.Share,
                contentDescription = "分享当前歌曲",
                tag = "player_screen_top_share_button",
                buttonSize = layoutMetrics.topBarActionButtonSize,
                onClick = onShareClick
            )
        }
    }
}

private data class PlayerOrientationModeAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val contentDescription: String
)

private fun resolvePlayerOrientationModeAction(
    orientationMode: PlayerOrientationMode
): PlayerOrientationModeAction {
    return when (orientationMode) {
        PlayerOrientationMode.AUTO -> PlayerOrientationModeAction(
            icon = Icons.Rounded.ScreenRotationAlt,
            contentDescription = "恢复自动旋转"
        )

        PlayerOrientationMode.LANDSCAPE_LOCKED -> PlayerOrientationModeAction(
            icon = Icons.Rounded.StayCurrentLandscape,
            contentDescription = "切换到横屏锁定"
        )

        PlayerOrientationMode.PORTRAIT_LOCKED -> PlayerOrientationModeAction(
            icon = Icons.Rounded.StayCurrentPortrait,
            contentDescription = "切换到竖屏锁定"
        )
    }
}

@Composable
private fun PlayerTopBarTab(
    text: String,
    selected: Boolean,
    tag: String,
    indicatorTag: String,
    textSizeSp: Float,
    horizontalPadding: Dp,
    minWidth: Dp,
    indicatorWidth: Dp,
    indicatorHeight: Dp,
    onClick: () -> Unit
) {
    val visuals = resolvePlayerTopBarTabVisuals(
        selected = selected,
        visualTokens = PlayerLiteVisualTheme.colors
    )
    Column(
        modifier = Modifier
            .widthIn(min = minWidth)
            .testTag(tag)
            .semantics {
                playerTopTabTextSizeSp = textSizeSp
                playerTopTabFontWeight = visuals.fontWeight.weight
                playerTopTabHorizontalPaddingDp = horizontalPadding.value
                playerTopTabIndicatorHeightDp = indicatorHeight.value
            }
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = textSizeSp.sp),
            fontWeight = visuals.fontWeight,
            color = visuals.textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
        Box(
            modifier = Modifier
                .size(width = indicatorWidth, height = indicatorHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (selected) visuals.indicatorColor else Color.Transparent
                )
                .let { baseModifier ->
                    if (selected) {
                        baseModifier.testTag(indicatorTag)
                    } else {
                        baseModifier
                    }
                }
        )
    }
}

data class PlayerTopBarTabVisuals(
    val textColor: Color,
    val indicatorColor: Color,
    val fontWeight: FontWeight
)

fun resolvePlayerTopBarTabVisuals(
    selected: Boolean,
    visualTokens: com.wxy.playerlite.designsystem.theme.PlayerLiteVisualTokens
): PlayerTopBarTabVisuals {
    return PlayerTopBarTabVisuals(
        textColor = if (selected) {
            visualTokens.accentStrong.copy(alpha = 0.92f)
        } else {
            Color.White.copy(alpha = 0.80f)
        },
        indicatorColor = visualTokens.accentStrong.copy(alpha = 0.88f),
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
    )
}

@Composable
private fun PlayerTopBarActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tag: String,
    buttonSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag(tag),
        shape = RoundedCornerShape(buttonSize / 2),
        color = Color.White.copy(alpha = 0.06f),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(buttonSize),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size((buttonSize.value * 0.48f).dp)
            )
        }
    }
}

@Composable
private fun PlayerToolActionRow(
    showSongDetailAction: Boolean,
    playbackMode: PlaybackMode,
    showFavoriteAction: Boolean = false,
    onOpenSongDetail: () -> Unit,
    onFavoriteClick: (() -> Unit)? = null,
    onPlaybackModeClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    onAudioEffectClick: () -> Unit,
    onMoreClick: () -> Unit,
    layoutMetrics: PlayerScreenLayoutMetrics,
    compact: Boolean = false,
    alignToEnd: Boolean = false,
    modifier: Modifier = Modifier
) {
    val buttonSize = if (compact) {
        (layoutMetrics.toolButtonSize * 0.72f).coerceIn(34.dp, 40.dp)
    } else {
        layoutMetrics.toolButtonSize
    }
	    val iconSize = if (compact) {
	        (layoutMetrics.toolIconSize * 0.88f).coerceIn(20.dp, 23.dp)
	    } else {
	        layoutMetrics.toolIconSize
	    }
	    val itemSpacing = if (compact) 8.dp else 12.dp
	    Row(
	        modifier = modifier
	            .fillMaxWidth()
	            .padding(horizontal = if (compact) 10.dp else 8.dp)
	            .testTag("player_screen_tool_row"),
	        horizontalArrangement = if (alignToEnd) {
	            Arrangement.spacedBy(itemSpacing, Alignment.End)
	        } else {
	            Arrangement.SpaceBetween
	        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerToolActionButton(
            tag = "player_screen_playback_mode_button",
            icon = playbackMode.modeIcon(),
            contentDescription = playbackMode.modeContentDescription(),
            buttonSize = buttonSize,
            iconSize = iconSize,
            transparent = true,
            onClick = onPlaybackModeClick
        )

        if (showSongDetailAction) {
            PlayerToolActionButton(
                tag = "player_screen_song_detail_tool_button",
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = "打开歌曲详情",
                buttonSize = buttonSize,
                iconSize = iconSize,
                transparent = true,
                onClick = onOpenSongDetail
            )
        }

        if (showFavoriteAction && onFavoriteClick != null) {
            PlayerToolActionButton(
                tag = "player_screen_favorite_button",
                icon = Icons.Rounded.FavoriteBorder,
                contentDescription = "收藏当前歌曲",
                buttonSize = buttonSize,
                iconSize = iconSize,
                transparent = true,
                onClick = onFavoriteClick
            )
        }

	    PlayerToolActionButton(
	        tag = "player_screen_audio_effect_button",
	        icon = Icons.Rounded.GraphicEq,
	        contentDescription = "音效设置",
            buttonSize = buttonSize,
            iconSize = iconSize,
            transparent = true,
            onClick = onAudioEffectClick
        )

        PlayerToolActionButton(
            tag = "player_screen_playlist_button",
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = "打开播放列表",
            buttonSize = buttonSize,
            iconSize = iconSize,
            transparent = true,
            onClick = onPlaylistClick
        )

        PlayerToolActionButton(
            tag = "player_screen_more_button",
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = "更多操作",
            buttonSize = buttonSize,
            iconSize = iconSize,
            transparent = true,
            onClick = onMoreClick
        )
    }
}

@Composable
private fun PlayerToolActionButton(
    tag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    buttonSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    transparent: Boolean = false
) {
    val visualTokens = PlayerLiteVisualTheme.colors
    Surface(
        modifier = Modifier.testTag(tag),
        shape = RoundedCornerShape(buttonSize / 2),
        color = if (transparent) {
            Color.Transparent
        } else {
            visualTokens.surfacePrimary.copy(alpha = 0.12f)
        }
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(buttonSize),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White.copy(alpha = 0.88f)
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun PlayerBufferingIndicator(
    modifier: Modifier = Modifier
) {
    val visualTokens = PlayerLiteVisualTheme.colors
    Surface(
        modifier = modifier.testTag("player_screen_buffering_indicator"),
        shape = RoundedCornerShape(999.dp),
        color = visualTokens.surfacePrimary.copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = visualTokens.accentStrong
            )
            Text(
                text = "缓冲中...",
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun PlayerCoverStatusChip(
    text: String,
    modifier: Modifier = Modifier
) {
    val visualTokens = PlayerLiteVisualTheme.colors
    Surface(
        modifier = modifier.testTag("player_screen_status_chip"),
        shape = RoundedCornerShape(999.dp),
        color = visualTokens.surfacePrimary.copy(alpha = 0.16f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color.White.copy(alpha = 0.84f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlayerScreenEmptyState(
    status: String,
    onPickAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 28.dp, vertical = 32.dp)
            .testTag("player_screen_empty_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 28.dp, vertical = 32.dp)
                    .widthIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                DeckDisc(
                    isPlaying = false,
                    isPaused = false,
                    modifier = Modifier.size(120.dp)
                )
                Text(
                    text = "还没有播放内容",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = status.ifBlank { "选一首本地音频，我们就从这里开始播放。" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onPickAudio,
                    modifier = Modifier.testTag("player_screen_pick_audio_button")
                ) {
                    Text(text = "选择音频")
                }
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun PlayerScreenContent(
    fileName: String,
    artistText: String?,
    status: String,
    coverUrl: String?,
    showSongDetailAction: Boolean,
    lyricUiState: PlayerLyricUiState,
    selectedTopTab: PlayerTopTab,
    orientationMode: PlayerOrientationMode,
    isPreparing: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    hasPreviousTrack: Boolean,
    hasNextTrack: Boolean,
    playlistItemCount: Int,
    playbackMode: PlaybackMode,
    sliderValue: Float,
    sliderMax: Float,
    cacheProgressStart: Float?,
    cacheProgress: Float?,
    seekEnabled: Boolean,
    currentPositionMs: Long,
    currentDurationText: String,
    totalDurationText: String,
    currentSongId: String?,
    currentArtistId: String?,
    combinedStatusUi: com.wxy.playerlite.feature.player.model.PlayerCombinedStatusUi?,
    showInlineCombinedStatusRow: Boolean,
    onSeekValueChange: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onArtistClick: () -> Unit,
    onPlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onTogglePlaylistSheet: () -> Unit,
    onRetryLyrics: () -> Unit,
    onSelectTopTab: (PlayerTopTab) -> Unit,
    onCycleOrientationMode: (PlayerOrientationMode) -> Unit,
    onBackdropColorChange: (Color) -> Unit,
    onShowAudioQualitySettings: () -> Unit,
    onShowAudioEffectSettings: () -> Unit,
    onOpenSongDetail: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMoreClick: () -> Unit,
    visualTokens: com.wxy.playerlite.designsystem.theme.PlayerLiteVisualTokens,
    modifier: Modifier = Modifier
) {
    val trackText = artistText
        ?.takeIf { it.isNotBlank() }
        ?.let {
            PlayerTrackText(
                title = fileName.ifBlank { "未命名音频" },
                artist = it
            )
        }
        ?: resolvePlayerTrackText(fileName)

    BoxWithConstraints(modifier = modifier) {
        val layoutMetrics = resolvePlayerScreenLayoutMetrics(
            viewportWidthDp = maxWidth.value,
            viewportHeightDp = maxHeight.value
        )
        val titleTextStyle = MaterialTheme.typography.headlineMedium.copy(
            fontSize = layoutMetrics.titleFontSizeSp.sp,
            lineHeight = (layoutMetrics.titleFontSizeSp * 1.08f).sp
        )
        val artistTextStyle = MaterialTheme.typography.titleMedium.copy(
            fontSize = layoutMetrics.artistFontSizeSp.sp,
            lineHeight = (layoutMetrics.artistFontSizeSp * 1.12f).sp
        )
        val lyricTextStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = layoutMetrics.lyricFontSizeSp.sp,
            lineHeight = (layoutMetrics.lyricFontSizeSp * 1.12f).sp
        )
        val shouldShowStatusHint = !isPreparing &&
            status.isNotBlank() &&
            status !in setOf("正在播放", "Playing", "Paused", "Preparing", "Stopped")
        val lyricPresentation = resolvePlayerLyricPresentation(
            lyricUiState = lyricUiState,
            currentPositionMs = currentPositionMs
        )
        val compactControls = maxHeight.value < 600f || maxWidth.value < 340f
        val bottomLeadSpacerWeight = when {
            compactControls -> 1f
            maxHeight.value >= 840f -> 0.94f
            else -> 1f
        }
        val bottomTailSpacerWeight = when {
            compactControls -> 0f
            maxHeight.value >= 840f -> 0.06f
            else -> 0f
        }
        val isLandscapeLayout = resolvePlayerScreenLandscapeLayout(
            viewportWidthDp = maxWidth.value,
            viewportHeightDp = maxHeight.value,
            orientationMode = orientationMode
        )
        val pagerState = rememberPagerState(
            initialPage = selectedTopTab.toPageIndex(),
            pageCount = { 2 }
        )
        val handleTopTabSelection: (PlayerTopTab) -> Unit = { nextTab ->
            if (nextTab != selectedTopTab) {
                onSelectTopTab(nextTab)
            }
        }
        LaunchedEffect(selectedTopTab, pagerState) {
            val targetPage = selectedTopTab.toPageIndex()
            if (pagerState.settledPage != targetPage) {
                pagerState.scrollToPage(targetPage)
            }
        }

        LaunchedEffect(pagerState, selectedTopTab) {
            snapshotFlow { pagerState.settledPage }
                .filter { it in 0..1 }
                .distinctUntilChanged()
                .collect { page ->
                    val nextTab = page.toPlayerTopTab()
                    if (nextTab != selectedTopTab) {
                        onSelectTopTab(nextTab)
                    }
                }
        }

        val pagerContent: @Composable (Modifier) -> Unit = { pagerModifier ->
            HorizontalPager(
                state = pagerState,
                modifier = pagerModifier
                    .testTag("player_screen_content_pager"),
                beyondViewportPageCount = 1
            ) { page ->
                when (page.toPlayerTopTab()) {
                    PlayerTopTab.SONG -> {
                        if (isLandscapeLayout) {
                            PlayerLandscapeSongPage(
                                trackTitle = trackText.title,
                                trackArtist = trackText.artist,
                                currentArtistId = currentArtistId,
                                lyricSummaryText = formatLyricSummaryLine(lyricPresentation.summaryText),
                                lyricSummaryColor = lyricPresentation.summaryColor,
                                lyricSummaryTag = lyricPresentation.summaryTag,
                                titleTextStyle = titleTextStyle,
                                artistTextStyle = artistTextStyle,
                                lyricTextStyle = lyricTextStyle,
                                status = status,
                                coverUrl = coverUrl,
                                isPreparing = isPreparing,
                                shouldShowStatusHint = shouldShowStatusHint,
                                isPlaying = isPlaying,
                                isPaused = isPaused,
                                hasPreviousTrack = hasPreviousTrack,
                                hasNextTrack = hasNextTrack,
                                playlistItemCount = playlistItemCount,
                                playbackMode = playbackMode,
                                sliderValue = sliderValue,
                                sliderMax = sliderMax,
                                cacheProgressStart = cacheProgressStart,
                                cacheProgress = cacheProgress,
                                seekEnabled = seekEnabled,
                                currentDurationText = currentDurationText,
                                totalDurationText = totalDurationText,
                                layoutMetrics = layoutMetrics,
                                compactControls = compactControls,
                                onSeekValueChange = onSeekValueChange,
                                onSeekFinished = onSeekFinished,
                                onArtistClick = onArtistClick,
                                onPlay = onPlay,
                                onPrevious = onPrevious,
                                onNext = onNext,
                                onPause = onPause,
                                onResume = onResume,
                                onCyclePlaybackMode = onCyclePlaybackMode,
                                onTogglePlaylistSheet = onTogglePlaylistSheet,
                                onBackdropColorChange = onBackdropColorChange
                            )
	                        } else {
	                            Column(
	                                modifier = Modifier
	                                    .fillMaxSize()
	                                    .padding(horizontal = layoutMetrics.horizontalPadding)
	                                    .navigationBarsPadding()
	                                    .padding(bottom = layoutMetrics.verticalPadding),
	                                horizontalAlignment = Alignment.CenterHorizontally
	                            ) {
	                                Column(
	                                    modifier = Modifier
	                                        .fillMaxWidth()
	                                        .height(layoutMetrics.songInfoHeight)
	                                        .padding(
	                                            horizontal = layoutMetrics.topBarActionButtonSize +
	                                                16.dp
	                                        )
	                                        .padding(top = layoutMetrics.titleTopSpacing)
	                                        .testTag("player_screen_info_section"),
	                                    horizontalAlignment = Alignment.CenterHorizontally
	                                ) {
	                                    Spacer(
	                                        modifier = Modifier
	                                            .fillMaxWidth()
	                                            .height(1.dp)
	                                            .testTag("player_screen_song_content_top_anchor")
	                                    )
	                                    Text(
	                                        text = trackText.title,
	                                        style = titleTextStyle.copy(textAlign = TextAlign.Center),
	                                        fontWeight = FontWeight.Bold,
	                                        color = Color.White,
	                                        maxLines = 1,
	                                        overflow = TextOverflow.Clip,
	                                        modifier = Modifier
	                                            .playerTitleMarquee()
	                                            .testTag("player_screen_title")
	                                    )
	                                    Text(
	                                        text = trackText.artist,
	                                        style = artistTextStyle.copy(textAlign = TextAlign.Center),
	                                        color = Color.White.copy(alpha = if (currentArtistId.isNullOrBlank()) 0.62f else 0.72f),
	                                        maxLines = 1,
	                                        overflow = TextOverflow.Ellipsis,
	                                        modifier = Modifier
	                                            .padding(top = layoutMetrics.summaryTopPadding)
	                                            .then(
	                                                if (currentArtistId.isNullOrBlank()) {
	                                                    Modifier
	                                                } else {
	                                                    Modifier.clickable(
	                                                        onClickLabel = "打开歌手详情",
	                                                        onClick = onArtistClick
	                                                    )
	                                                }
	                                            )
	                                            .testTag("player_screen_artist")
	                                    )
	                                }

	                                Column(
	                                    modifier = Modifier
	                                        .fillMaxWidth()
	                                        .padding(top = layoutMetrics.coverTopSpacing),
	                                    horizontalAlignment = Alignment.CenterHorizontally
	                                ) {
	                                    Box(
	                                        modifier = Modifier
	                                            .size(layoutMetrics.coverSize)
	                                            .testTag("player_screen_song_page"),
	                                        contentAlignment = Alignment.Center
	                                    ) {
	                                        Box(
	                                            modifier = Modifier
	                                                .fillMaxSize()
	                                                .testTag("player_screen_visual_section"),
	                                        ) {
	                                            Box(
	                                                modifier = Modifier.fillMaxSize(),
	                                                contentAlignment = Alignment.Center
	                                            ) {
	                                                PlayerCoverCard(
	                                                    isPlaying = isPlaying,
	                                                    isPaused = isPaused,
	                                                    coverUrl = coverUrl,
	                                                    onBackdropColorExtracted = onBackdropColorChange,
	                                                    modifier = Modifier.fillMaxSize()
	                                                )
	                                                if (isPreparing) {
	                                                    PlayerBufferingIndicator(
	                                                        modifier = Modifier
	                                                            .align(Alignment.BottomEnd)
	                                                            .padding(10.dp)
	                                                    )
	                                                } else if (shouldShowStatusHint) {
	                                                    PlayerCoverStatusChip(
	                                                        text = status,
	                                                        modifier = Modifier
	                                                            .align(Alignment.BottomEnd)
	                                                            .padding(10.dp)
	                                                    )
	                                                }
	                                            }
	                                        }
	                                    }
	                                    Text(
	                                        text = formatLyricSummaryLine(lyricPresentation.summaryText),
	                                        style = lyricTextStyle.copy(
	                                            fontStyle = FontStyle.Italic,
	                                            textAlign = TextAlign.Center
	                                        ),
	                                        color = lyricPresentation.summaryColor,
	                                        maxLines = 1,
	                                        overflow = TextOverflow.Ellipsis,
	                                        modifier = Modifier
	                                            .fillMaxWidth()
	                                            .padding(top = layoutMetrics.lyricBelowCoverSpacing)
	                                            .testTag(lyricPresentation.summaryTag)
	                                    )
	                                }

	                                Spacer(modifier = Modifier.weight(bottomLeadSpacerWeight))

	                                Column(
	                                    modifier = Modifier
	                                        .fillMaxWidth()
	                                        .testTag("player_screen_bottom_section"),
	                                    verticalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacing)
	                                ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("player_screen_progress_section"),
                                    verticalArrangement = Arrangement.spacedBy(layoutMetrics.progressSectionSpacing)
                                ) {
                                    val progressTimeTextStyle = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = layoutMetrics.progressTimeFontSizeSp.sp
                                    )
                                    val combinedStatusTextStyle = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = layoutMetrics.progressTimeFontSizeSp.sp
                                    )
                                    PlayerProgressBarSlider(
                                        value = sliderValue,
                                        max = sliderMax,
                                        cacheProgressStart = cacheProgressStart,
                                        cacheProgress = cacheProgress,
                                        enabled = seekEnabled,
                                        onValueChange = { value -> onSeekValueChange(value.toLong()) },
                                        onValueChangeFinished = onSeekFinished,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (showInlineCombinedStatusRow) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = currentDurationText,
                                                style = progressTimeTextStyle,
                                                color = Color.White.copy(alpha = 0.62f),
                                                textAlign = TextAlign.Start,
                                                maxLines = 1,
                                                modifier = Modifier
                                                    .widthIn(min = 48.dp)
                                                    .testTag("player_screen_current_duration")
                                            )
                                            Box(
                                                modifier = Modifier.weight(1f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                PlayerCombinedStatusRow(
                                                    combinedStatusUi = combinedStatusUi,
                                                    onShowAudioQualitySettings = onShowAudioQualitySettings,
                                                    onShowAudioEffectSettings = onShowAudioEffectSettings,
                                                    textStyle = combinedStatusTextStyle,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("player_screen_combined_status_row")
                                                )
                                            }
                                            Text(
                                                text = totalDurationText,
                                                style = progressTimeTextStyle,
                                                color = Color.White.copy(alpha = 0.62f),
                                                textAlign = TextAlign.End,
                                                maxLines = 1,
                                                modifier = Modifier
                                                    .widthIn(min = 48.dp)
                                                    .testTag("player_screen_total_duration")
                                            )
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = currentDurationText,
                                                style = progressTimeTextStyle,
                                                color = Color.White.copy(alpha = 0.62f),
                                                modifier = Modifier
                                                    .testTag("player_screen_current_duration")
                                            )
                                            Text(
                                                text = totalDurationText,
                                                style = progressTimeTextStyle,
                                                color = Color.White.copy(alpha = 0.62f),
                                                modifier = Modifier
                                                    .testTag("player_screen_total_duration")
                                            )
                                        }
                                    }
                                }

                                PlaybackControls(
                                    hasSelection = true,
                                    hasPreviousTrack = hasPreviousTrack,
                                    hasNextTrack = hasNextTrack,
                                    isPreparing = isPreparing,
                                    isPlaying = isPlaying,
                                    isPaused = isPaused,
                                    onPlay = onPlay,
                                    onPrevious = onPrevious,
                                    onNext = onNext,
                                    onPause = onPause,
                                    onResume = onResume,
                                    compactMode = compactControls,
                                    denseMode = compactControls,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("player_screen_controls_section")
                                )
                                PlayerToolActionRow(
                                    showSongDetailAction = showSongDetailAction,
                                    playbackMode = playbackMode,
                                    onOpenSongDetail = onOpenSongDetail,
                                    onPlaybackModeClick = onCyclePlaybackMode,
                                    onPlaylistClick = onTogglePlaylistSheet,
                                    onAudioEffectClick = onShowAudioEffectSettings,
                                    onMoreClick = onMoreClick,
                                    layoutMetrics = layoutMetrics,
                                    compact = compactControls
                                )
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .testTag("player_screen_song_controls_bottom_anchor")
                                )
	                                }
	                                if (bottomTailSpacerWeight > 0f) {
	                                    Spacer(modifier = Modifier.weight(bottomTailSpacerWeight))
	                                }
	                            }
	                        }
                    }

                    PlayerTopTab.LYRICS -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = layoutMetrics.horizontalPadding)
                                .testTag("player_screen_lyrics_page")
                        ) {
                            Spacer(modifier = Modifier.height(layoutMetrics.lyricsTopInset))
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = layoutMetrics.coverSideInset)
                                    .navigationBarsPadding()
                                    .padding(bottom = layoutMetrics.lyricsBottomInset)
                            ) {
                                PlayerLyricsPage(
                                    lyricUiState = lyricUiState,
                                    activeLineIndex = lyricPresentation.activeLineIndex,
                                    isVisible = page == pagerState.settledPage,
                                    onRetryLyrics = onRetryLyrics,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("player_screen_lyrics_viewport")
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isLandscapeLayout) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("player_screen_landscape_root")
            ) {
                pagerContent(Modifier.fillMaxSize())
                PlayerLandscapeOverlayActions(
                    orientationMode = orientationMode,
                    onCycleOrientationMode = onCycleOrientationMode,
                    onBackClick = onBackClick,
                    onShareClick = onShareClick,
                    layoutMetrics = layoutMetrics,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = layoutMetrics.horizontalPadding)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = layoutMetrics.horizontalPadding)
                ) {
	                    PlayerScreenTopBar(
	                        onBackClick = onBackClick,
	                        onMoreClick = onMoreClick,
	                        layoutMetrics = layoutMetrics
	                    )
                }
                pagerContent(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PlayerLandscapeSongPage(
    trackTitle: String,
    trackArtist: String,
    currentArtistId: String?,
    lyricSummaryText: String,
    lyricSummaryColor: Color,
    lyricSummaryTag: String,
    titleTextStyle: TextStyle,
    artistTextStyle: TextStyle,
    lyricTextStyle: TextStyle,
    status: String,
    coverUrl: String?,
    isPreparing: Boolean,
    shouldShowStatusHint: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    hasPreviousTrack: Boolean,
    hasNextTrack: Boolean,
    playlistItemCount: Int,
    playbackMode: PlaybackMode,
    sliderValue: Float,
    sliderMax: Float,
    cacheProgressStart: Float?,
    cacheProgress: Float?,
    seekEnabled: Boolean,
    currentDurationText: String,
    totalDurationText: String,
    layoutMetrics: PlayerScreenLayoutMetrics,
    compactControls: Boolean,
    onSeekValueChange: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onArtistClick: () -> Unit,
    onPlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onTogglePlaylistSheet: () -> Unit,
    onBackdropColorChange: (Color) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val progressTimeTextStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = layoutMetrics.progressTimeFontSizeSp.sp
    )
    val landscapeLyricSummaryText = if (lyricSummaryTag == "player_screen_lyric_summary") {
        formatLyricSummaryLine(lyricSummaryText)
    } else {
        ""
    }
    val landscapeSafeStartInset = WindowInsets.safeDrawing
        .asPaddingValues()
        .calculateStartPadding(layoutDirection)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = layoutMetrics.horizontalPadding + landscapeSafeStartInset,
                end = layoutMetrics.horizontalPadding
            )
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacing * 1.1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("player_screen_landscape_controls_panel")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = layoutMetrics.sectionSpacing * 0.4f)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(0.78f)
                        .padding(
                            top = layoutMetrics.topBarHeight + (layoutMetrics.sectionSpacing * 2.45f),
                            bottom = layoutMetrics.sectionSpacing * 0.2f
                        )
                        .testTag("player_screen_landscape_info_group")
                ) {
                    PlayerLandscapeHeroPanel(
                        trackTitle = trackTitle,
                        trackArtist = trackArtist,
                        currentArtistId = currentArtistId,
                        lyricSummaryText = landscapeLyricSummaryText,
                        lyricSummaryColor = lyricSummaryColor,
                        lyricSummaryTag = lyricSummaryTag,
                        titleTextStyle = titleTextStyle,
                        artistTextStyle = artistTextStyle,
                        lyricTextStyle = lyricTextStyle,
                        layoutMetrics = layoutMetrics,
                        onArtistClick = onArtistClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_screen_info_section")
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.89f)
                        .testTag("player_screen_landscape_bottom_group"),
                    verticalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacing * 0.18f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_screen_landscape_progress_band"),
                        verticalArrangement = Arrangement.spacedBy(layoutMetrics.progressSectionSpacing)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_screen_progress_section"),
                            verticalArrangement = Arrangement.spacedBy(layoutMetrics.progressSectionSpacing)
                        ) {
                            PlayerProgressBarSlider(
                                value = sliderValue,
                                max = sliderMax,
                                cacheProgressStart = cacheProgressStart,
                                cacheProgress = cacheProgress,
                                enabled = seekEnabled,
                                onValueChange = { value -> onSeekValueChange(value.toLong()) },
                                onValueChangeFinished = onSeekFinished,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentDurationText,
                                    style = progressTimeTextStyle,
                                    color = Color.White.copy(alpha = 0.62f),
                                    modifier = Modifier.testTag("player_screen_current_duration")
                                )
                                Text(
                                    text = totalDurationText,
                                    style = progressTimeTextStyle,
                                    color = Color.White.copy(alpha = 0.62f),
                                    modifier = Modifier.testTag("player_screen_total_duration")
                                )
                            }
                        }
                    }

                    PlaybackControls(
                        hasSelection = true,
                        hasPreviousTrack = hasPreviousTrack,
                        hasNextTrack = hasNextTrack,
                        isPreparing = isPreparing,
                        isPlaying = isPlaying,
                        isPaused = isPaused,
                        onPlay = onPlay,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onPause = onPause,
                        onResume = onResume,
                        compactMode = compactControls,
                        denseMode = compactControls,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_screen_controls_section")
                    )
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .testTag("player_screen_landscape_controls_anchor")
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(0.88f)
                .fillMaxHeight()
                .testTag("player_screen_landscape_visual_panel"),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("player_screen_song_page")
            ) {
                val visualHostSize = if (maxWidth < maxHeight) {
                    maxWidth * 0.90f
                } else {
                    maxHeight * 0.90f
                }
                val coverFrameSize = visualHostSize * 0.94f
                val reflectionHeight = (coverFrameSize * 0.22f).coerceIn(24.dp, 72.dp)
                val visualCompositionHeight = coverFrameSize + reflectionHeight
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("player_screen_visual_section"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = reflectionHeight / 2)
                            .size(width = coverFrameSize, height = visualCompositionHeight)
                    ) {
                        PlayerLandscapeVisualReflection(
                            coverUrl = coverUrl,
                            isPlaying = isPlaying,
                            isPaused = isPaused,
                            isPreparing = isPreparing,
                            sourceSize = coverFrameSize,
                            reflectionHeight = reflectionHeight,
                            onBackdropColorExtracted = onBackdropColorChange,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isPreparing) {
                            PlayerBufferingIndicator(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = coverFrameSize - 44.dp, end = 10.dp)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .testTag("player_screen_landscape_visual_anchor")
                )
            }
        }
    }
}

private fun resolvePlayerScreenLandscapeLayout(
    viewportWidthDp: Float,
    viewportHeightDp: Float,
    orientationMode: PlayerOrientationMode
): Boolean {
    return when (orientationMode) {
        PlayerOrientationMode.LANDSCAPE_LOCKED -> true
        PlayerOrientationMode.PORTRAIT_LOCKED -> false
        PlayerOrientationMode.AUTO -> viewportWidthDp > viewportHeightDp
    }
}

private fun PlayerTopTab.toPageIndex(): Int {
    return when (this) {
        PlayerTopTab.SONG -> 0
        PlayerTopTab.LYRICS -> 1
    }
}

private fun Int.toPlayerTopTab(): PlayerTopTab {
    return when (this) {
        1 -> PlayerTopTab.LYRICS
        else -> PlayerTopTab.SONG
    }
}

@Composable
fun PlayerLyricsPage(
    lyricUiState: PlayerLyricUiState,
    activeLineIndex: Int,
    isVisible: Boolean,
    onRetryLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visualTokens = PlayerLiteVisualTheme.colors
    when (lyricUiState) {
        PlayerLyricUiState.Placeholder -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "歌词待加载",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.56f)
                )
            }
        }

        PlayerLyricUiState.Loading -> {
            Row(
                modifier = modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Text(
                    text = "歌词加载中...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.84f)
                )
            }
        }

        is PlayerLyricUiState.Empty -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lyricUiState.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.62f)
                )
            }
        }

        is PlayerLyricUiState.Error -> {
            Column(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = lyricUiState.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.88f)
                )
                TextButton(
                    onClick = onRetryLyrics,
                    modifier = Modifier.testTag("player_screen_lyrics_retry_button")
                ) {
                    Text(text = "重试")
                }
            }
        }

        is PlayerLyricUiState.Content -> {
            val listState = rememberLazyListState()
            val firstVisibleLyricIndex by remember(listState) {
                derivedStateOf { listState.firstVisibleItemIndex }
            }
            val latestActiveLineIndex by rememberUpdatedState(activeLineIndex)
            val latestIsVisible by rememberUpdatedState(isVisible)
            var needsInitialPlacement by remember(lyricUiState.lyrics.songId, isVisible) {
                mutableStateOf(true)
            }
            var lastRequestedTargetIndex by remember(lyricUiState.lyrics.songId, isVisible) {
                mutableStateOf<Int?>(null)
            }
            LaunchedEffect(lyricUiState.lyrics.songId) {
                snapshotFlow { latestActiveLineIndex to latestIsVisible }
                    .distinctUntilChanged()
                    .collect { (nextActiveLineIndex, visible) ->
                        if (!visible) {
                            return@collect
                        }
                        snapshotFlow { listState.layoutInfo.totalItemsCount }
                            .filter { it > 0 }
                            .first()
                        val request = resolveLyricsAutoScrollRequest(
                            activeLineIndex = nextActiveLineIndex,
                            firstVisibleItemIndex = listState.firstVisibleItemIndex,
                            isInitialPlacement = needsInitialPlacement,
                            lastRequestedTargetIndex = lastRequestedTargetIndex
                        )
                        if (request != null) {
                            lastRequestedTargetIndex = request.targetIndex
                            when (request.mode) {
                                LyricsAutoScrollMode.Snap -> listState.scrollToItem(request.targetIndex)
                                LyricsAutoScrollMode.Animate -> listState.animateScrollToItem(request.targetIndex)
                            }
                        }
                        needsInitialPlacement = false
                    }
            }
            LazyColumn(
                state = listState,
                modifier = modifier
                    .fillMaxSize()
                    .semantics {
                        playerLyricsFirstVisibleIndex = firstVisibleLyricIndex
                    }
                    .testTag("player_screen_lyrics_list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp,
                    vertical = 56.dp
                ),
                verticalArrangement = Arrangement.spacedBy(34.dp)
            ) {
                items(lyricUiState.lyrics.lines.size) { index ->
                    val line = lyricUiState.lyrics.lines[index]
                    val isActiveLine = index == activeLineIndex
                    val visuals = resolvePlayerLyricsLineVisuals(isActiveLine = isActiveLine)
                    val distanceFromActive = if (activeLineIndex < 0) {
                        Int.MAX_VALUE
                    } else {
                        kotlin.math.abs(index - activeLineIndex)
                    }
                    val lineAlpha = when {
                        isActiveLine -> 1f
                        distanceFromActive == 1 -> 0.42f
                        distanceFromActive == 2 -> 0.28f
                        else -> visuals.inactiveAlphaFloor
                    }
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 30.sp,
                            lineHeight = 48.sp,
                            shadow = if (isActiveLine) {
                                Shadow(
                                    color = visualTokens.accentSupport.copy(alpha = visuals.glowAlpha),
                                    offset = Offset.Zero,
                                    blurRadius = 26f
                                )
                            } else {
                                Shadow(color = Color.Transparent, offset = Offset.Zero, blurRadius = 0f)
                            }
                        ),
                        fontWeight = visuals.fontWeight,
                        color = Color.White.copy(alpha = lineAlpha),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = visuals.scale
                                scaleY = visuals.scale
                            }
                            .testTag(
                                if (isActiveLine) {
                                    "player_screen_lyrics_line_active_$index"
                                } else {
                                    "player_screen_lyrics_line_$index"
                                }
                            )
                    )
                }
            }
        }
    }
}

private data class PlayerLyricPresentation(
    val summaryText: String,
    val summaryColor: Color,
    val summaryTag: String,
    val activeLineIndex: Int
)

private fun resolvePlayerLyricPresentation(
    lyricUiState: PlayerLyricUiState,
    currentPositionMs: Long
): PlayerLyricPresentation {
    return when (lyricUiState) {
        PlayerLyricUiState.Placeholder -> PlayerLyricPresentation(
            summaryText = "歌词待补充",
            summaryColor = Color.White.copy(alpha = 0.48f),
            summaryTag = "player_screen_lyric_placeholder",
            activeLineIndex = -1
        )

        PlayerLyricUiState.Loading -> PlayerLyricPresentation(
            summaryText = "歌词加载中...",
            summaryColor = Color.White.copy(alpha = 0.64f),
            summaryTag = "player_screen_lyric_summary",
            activeLineIndex = -1
        )

        is PlayerLyricUiState.Empty -> PlayerLyricPresentation(
            summaryText = lyricUiState.message,
            summaryColor = Color.White.copy(alpha = 0.56f),
            summaryTag = "player_screen_lyric_summary",
            activeLineIndex = -1
        )

        is PlayerLyricUiState.Error -> PlayerLyricPresentation(
            summaryText = lyricUiState.message,
            summaryColor = Color.White.copy(alpha = 0.72f),
            summaryTag = "player_screen_lyric_summary",
            activeLineIndex = -1
        )

        is PlayerLyricUiState.Content -> {
            val activeLyric = resolveActiveLyricLineProjection(
                lyricUiState = lyricUiState,
                currentPositionMs = currentPositionMs
            )
            PlayerLyricPresentation(
                summaryText = activeLyric.activeLineText ?: "暂无歌词",
                summaryColor = Color.White.copy(alpha = 0.82f),
                summaryTag = "player_screen_lyric_summary",
                activeLineIndex = activeLyric.activeLineIndex
            )
        }
    }
}

private fun formatLyricSummaryLine(text: String): String {
    val normalized = text.trim()
    if (normalized.isBlank()) return text
    if (
        normalized.contains("歌词") ||
        normalized.contains("暂无") ||
        normalized.contains("失败")
    ) {
        return normalized
    }
    return if (normalized.startsWith("“") || normalized.startsWith("\"")) {
        normalized
    } else {
        "“$normalized”"
    }
}

@Composable
private fun PlayerProgressBarSlider(
    value: Float,
    max: Float,
    cacheProgressStart: Float?,
    cacheProgress: Float?,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visualTokens = PlayerLiteVisualTheme.colors
    val boundedMax = max.coerceAtLeast(1f)
    val boundedValue = value.coerceIn(0f, boundedMax)
    val progressFraction = (boundedValue / boundedMax).coerceIn(0f, 1f)
    val cacheProgressStartFraction = cacheProgressStart
        ?.coerceIn(0f, 1f)
        ?: 0f
    val cacheProgressFraction = cacheProgress
        ?.coerceIn(0f, 1f)
    val thumbSize = 8.dp
    val trackHeight = 3.dp

    BoxWithConstraints(
        modifier = modifier
            .height(20.dp)
    ) {
        val trackMaxWidth = maxWidth
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    visualTokens.miniPlayerProgressTrack.copy(alpha = if (enabled) 0.30f else 0.18f)
                )
                .testTag("player_screen_slider_track")
        ) {
            val cacheWidthFraction = cacheProgressFraction
                ?.let { (it - cacheProgressStartFraction).coerceIn(0f, 1f) }
                ?: 0f
            if (cacheWidthFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = trackMaxWidth * cacheProgressStartFraction)
                        .width(trackMaxWidth * cacheWidthFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            visualTokens.miniPlayerProgressFill.copy(
                                alpha = if (enabled) 0.36f else 0.22f
                            )
                        )
                        .testTag("player_screen_slider_cached_track")
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        visualTokens.miniPlayerProgressFill.copy(alpha = if (enabled) 0.96f else 0.42f)
                    )
                    .testTag("player_screen_slider_active_track")
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = (maxWidth - thumbSize) * progressFraction)
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (enabled) 0.96f else 0.44f))
                .testTag("player_screen_slider_thumb")
        )

        Slider(
            value = boundedValue,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..boundedMax,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .testTag("player_screen_slider")
        )
    }
}

data class PlayerTrackText(
    val title: String,
    val artist: String
)

fun resolvePlayerTrackText(fileName: String): PlayerTrackText {
    val normalizedName = fileName
        .trim()
        .substringBeforeLast('.', missingDelimiterValue = fileName.trim())
        .ifBlank { "未命名音频" }

    val separatorIndex = listOf(" - ", " — ", " – ")
        .map { normalizedName.indexOf(it) }
        .firstOrNull { it > 0 }

    if (separatorIndex == null) {
        return PlayerTrackText(
            title = normalizedName,
            artist = "本地音频"
        )
    }

    val separator = listOf(" - ", " — ", " – ")
        .first { normalizedName.indexOf(it) == separatorIndex }
    val artist = normalizedName.substring(0, separatorIndex).trim()
    val title = normalizedName.substring(separatorIndex + separator.length).trim()

    return PlayerTrackText(
        title = title.ifBlank { normalizedName },
        artist = artist.ifBlank { "本地音频" }
    )
}
