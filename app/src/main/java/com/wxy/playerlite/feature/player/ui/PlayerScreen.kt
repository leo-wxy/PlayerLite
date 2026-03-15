package com.wxy.playerlite.feature.player.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.player.LyricLine
import com.wxy.playerlite.feature.player.resolveActiveLyricLineProjection
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.feature.player.model.PlayerLyricUiState
import com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState
import com.wxy.playerlite.feature.player.model.PlayerTopTab
import com.wxy.playerlite.feature.player.model.SongWikiSummary
import com.wxy.playerlite.feature.player.ui.components.PlaybackControls
import com.wxy.playerlite.feature.player.ui.components.PlaylistBottomSheet
import com.wxy.playerlite.playback.model.PlaybackMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private val PlayerTabAccentColor = Color(0xFFD48CFF)
private val PlayerLyricsGlowColor = Color(0xFFC678FF)

@Composable
internal fun PlayerScreen(
    fileName: String,
    artistText: String? = null,
    status: String,
    hasSelection: Boolean,
    playlistItems: List<PlaylistItem>,
    activePlaylistIndex: Int,
    showPlaylistSheet: Boolean,
    showSongWikiSheet: Boolean,
    songWikiUiState: PlayerSongWikiUiState,
    lyricUiState: PlayerLyricUiState = PlayerLyricUiState.Placeholder,
    selectedTopTab: PlayerTopTab = PlayerTopTab.SONG,
    isPreparing: Boolean,
    playbackState: Int,
    isSeekSupported: Boolean,
    playbackMode: PlaybackMode,
    showOriginalOrderInShuffle: Boolean,
    canReorderPlaylist: Boolean,
    seekValueMs: Long,
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
    showSongWikiInlineButton: Boolean = true,
    enableEnterMotion: Boolean = true,
    modifier: Modifier = Modifier,
    onPickAudio: () -> Unit,
    onTogglePlaylistSheet: () -> Unit,
    onDismissPlaylistSheet: () -> Unit,
    onShowSongWiki: () -> Unit,
    onDismissSongWiki: () -> Unit,
    onRetrySongWiki: () -> Unit,
    onRetryLyrics: () -> Unit = {},
    onSelectTopTab: ((PlayerTopTab) -> Unit)? = null,
    onSelectPlaylistItem: (Int) -> Unit,
    onRemovePlaylistItem: (Int) -> Unit,
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
    onBackClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onArtistClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onMoreClick: () -> Unit = {}
) {
    val isPlaying = playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING
    val isPaused = playbackState == AUDIO_TRACK_PLAYSTATE_PAUSED
    val hasPreviousTrack = activePlaylistIndex > 0
    val hasNextTrack = activePlaylistIndex >= 0 && activePlaylistIndex < playlistItems.lastIndex
    val hasKnownDuration = durationMs > 0L
    val resolvedCoverUrl = currentCoverUrl?.takeIf { it.isNotBlank() }
    val resolvedArtistId = currentArtistId?.takeIf { it.isNotBlank() }
    val sliderMax = if (hasKnownDuration) {
        durationMs.toFloat()
    } else {
        maxOf(300_000L, seekValueMs + 30_000L).toFloat()
    }
    val sliderValue = seekValueMs.coerceIn(0L, sliderMax.toLong()).toFloat()
    val seekEnabled = isSeekSupported && (isPlaying || isPaused)
    var backdropColor by remember(resolvedCoverUrl) {
        mutableStateOf(Color(0xFF171A21))
    }
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

    PlayerScreenBackground(
        backdropColor = backdropColor,
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
                showSongWikiAction = showSongWikiInlineButton && currentSongId != null,
                lyricUiState = lyricUiState,
                selectedTopTab = effectiveSelectedTopTab,
                isPreparing = isPreparing,
                isPlaying = isPlaying,
                isPaused = isPaused,
                hasPreviousTrack = hasPreviousTrack,
                hasNextTrack = hasNextTrack,
                playlistItemCount = playlistItems.size,
                playbackMode = playbackMode,
                sliderValue = sliderValue,
                sliderMax = sliderMax,
                seekEnabled = seekEnabled,
                currentPositionMs = seekValueMs,
                currentDurationText = currentDurationText,
                totalDurationText = totalDurationText,
                currentArtistId = resolvedArtistId,
                onSeekValueChange = onSeekValueChange,
                onSeekFinished = onSeekFinished,
                onBackClick = onBackClick,
                onShareClick = onShareClick,
                onArtistClick = onArtistClick,
                onPlay = onPlay,
                onPrevious = onPrevious,
                onNext = onNext,
                onPause = onPause,
                onResume = onResume,
                onCyclePlaybackMode = onCyclePlaybackMode,
                onTogglePlaylistSheet = onTogglePlaylistSheet,
                onShowSongWiki = onShowSongWiki,
                onRetryLyrics = onRetryLyrics,
                onSelectTopTab = handleSelectTopTab,
                onBackdropColorChange = { backdropColor = it },
                onFavoriteClick = onFavoriteClick,
                onMoreClick = onMoreClick,
                modifier = Modifier.fillMaxSize()
            )

            PlayerSongWikiSheet(
                visible = showSongWikiSheet,
                songId = currentSongId,
                state = songWikiUiState,
                onDismiss = onDismissSongWiki,
                onRetry = onRetrySongWiki,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            PlaylistBottomSheet(
                visible = showPlaylistSheet,
                items = playlistItems,
                activeIndex = activePlaylistIndex,
                playbackMode = playbackMode,
                showOriginalOrderInShuffle = showOriginalOrderInShuffle,
                canReorder = canReorderPlaylist,
                onDismiss = onDismissPlaylistSheet,
                onShowOriginalOrderInShuffleChange = onShowOriginalOrderInShuffleChange,
                onSelect = onSelectPlaylistItem,
                onRemove = onRemovePlaylistItem,
                onMove = onMovePlaylistItem,
                modifier = Modifier.testTag("player_screen_playlist_sheet")
            )
        }
    }
}

@Composable
internal fun PlayerSongWikiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("player_screen_song_wiki_button"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 4.dp,
        shadowElevation = 10.dp
    ) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = "歌曲百科"
            )
        }
    }
}

@Composable
private fun PlayerScreenBackground(
    backdropColor: Color,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
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
                model = coverUrl,
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

internal fun extractBackdropColor(bitmap: Bitmap): Color {
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

internal fun extractBackdropColorSafely(bitmap: Bitmap?): Color? {
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

@Composable
private fun PlayerScreenTopBar(
    selectedTopTab: PlayerTopTab,
    onSelectTopTab: (PlayerTopTab) -> Unit,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    layoutMetrics: PlayerScreenLayoutMetrics,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(layoutMetrics.topBarHeight)
            .testTag("player_screen_top_bar"),
    ) {
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
                .align(Alignment.Center)
                .padding(horizontal = layoutMetrics.topBarActionButtonSize + 16.dp)
                .testTag("player_screen_top_tabs"),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerTopBarTab(
                text = "歌曲",
                selected = selectedTopTab == PlayerTopTab.SONG,
                tag = "player_screen_top_tab_song",
                indicatorTag = "player_screen_top_tab_indicator_song",
                textSizeSp = layoutMetrics.topTabTextSizeSp,
                horizontalPadding = layoutMetrics.topTabHorizontalPadding,
                onClick = { onSelectTopTab(PlayerTopTab.SONG) }
            )
            PlayerTopBarTab(
                text = "歌词",
                selected = selectedTopTab == PlayerTopTab.LYRICS,
                tag = "player_screen_top_tab_lyrics",
                indicatorTag = "player_screen_top_tab_indicator_lyrics",
                textSizeSp = layoutMetrics.topTabTextSizeSp,
                horizontalPadding = layoutMetrics.topTabHorizontalPadding,
                onClick = { onSelectTopTab(PlayerTopTab.LYRICS) }
            )
        }
        Box(
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
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

@Composable
private fun PlayerTopBarTab(
    text: String,
    selected: Boolean,
    tag: String,
    indicatorTag: String,
    textSizeSp: Float,
    horizontalPadding: Dp,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .testTag(tag)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = textSizeSp.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = Color.White.copy(alpha = if (selected) 0.96f else 0.46f),
            textAlign = TextAlign.Center
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(PlayerTabAccentColor.copy(alpha = 0.92f), PlayerTabAccentColor)
                        )
                    )
                    .testTag(indicatorTag)
            )
        }
    }
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
    showSongWikiAction: Boolean,
    onShowSongWiki: () -> Unit,
    onMoreClick: () -> Unit,
    layoutMetrics: PlayerScreenLayoutMetrics,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("player_screen_tool_row"),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showSongWikiAction) {
            PlayerToolActionButton(
                tag = "player_screen_song_wiki_tool_button",
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = "打开歌曲百科",
                buttonSize = layoutMetrics.toolButtonSize,
                iconSize = layoutMetrics.toolIconSize,
                onClick = onShowSongWiki
            )
        }

        PlayerToolActionButton(
            tag = "player_screen_more_button",
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = "更多操作",
            buttonSize = layoutMetrics.toolButtonSize,
            iconSize = layoutMetrics.toolIconSize,
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
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.testTag(tag),
        shape = RoundedCornerShape(buttonSize / 2),
        color = Color.White.copy(alpha = 0.04f)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(buttonSize),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White.copy(alpha = 0.86f)
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
    Surface(
        modifier = modifier.testTag("player_screen_buffering_indicator"),
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
            Text(
                text = "缓冲中...",
                color = Color.White,
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
    Surface(
        modifier = modifier.testTag("player_screen_status_chip"),
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.10f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color.White.copy(alpha = 0.82f),
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

@Composable
private fun PlayerSongWikiSheet(
    visible: Boolean,
    songId: String?,
    state: PlayerSongWikiUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible || songId.isNullOrBlank()) {
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("player_screen_song_wiki_sheet")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
                .clickable(onClick = onDismiss)
                .testTag("player_screen_song_wiki_scrim")
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "歌曲百科",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "基于当前播放歌曲的简要百科信息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("player_screen_song_wiki_close")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭歌曲百科"
                        )
                    }
                }

                when (state) {
                    PlayerSongWikiUiState.Placeholder -> {
                        Text(
                            text = "轻触右上角即可查看歌曲百科。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    PlayerSongWikiUiState.Loading -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "正在加载歌曲百科…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    is PlayerSongWikiUiState.Content -> {
                        SongWikiSummaryContent(summary = state.summary)
                    }

                    is PlayerSongWikiUiState.Empty -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is PlayerSongWikiUiState.Error -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(
                                onClick = onRetry,
                                modifier = Modifier.testTag("player_screen_song_wiki_retry")
                            ) {
                                Text(text = "重试")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongWikiSummaryContent(
    summary: SongWikiSummary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        summary.contributionText?.takeIf { it.isNotBlank() }?.let { contributionText ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp)
                )
            ) {
                Text(
                    text = contributionText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (summary.coverUrl != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = summary.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "为当前歌曲整理的基础信息摘要",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        summary.sections.forEach { section ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = section.values.joinToString(separator = " · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

    }
}

@Composable
private fun PlayerScreenContent(
    fileName: String,
    artistText: String?,
    status: String,
    coverUrl: String?,
    showSongWikiAction: Boolean,
    lyricUiState: PlayerLyricUiState,
    selectedTopTab: PlayerTopTab,
    isPreparing: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    hasPreviousTrack: Boolean,
    hasNextTrack: Boolean,
    playlistItemCount: Int,
    playbackMode: PlaybackMode,
    sliderValue: Float,
    sliderMax: Float,
    seekEnabled: Boolean,
    currentPositionMs: Long,
    currentDurationText: String,
    totalDurationText: String,
    currentArtistId: String?,
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
    onShowSongWiki: () -> Unit,
    onRetryLyrics: () -> Unit,
    onSelectTopTab: (PlayerTopTab) -> Unit,
    onBackdropColorChange: (Color) -> Unit,
    onFavoriteClick: () -> Unit,
    onMoreClick: () -> Unit,
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
        val compactControls = maxHeight.value < 760f || maxWidth.value < 360f
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

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = layoutMetrics.horizontalPadding)
            ) {
                PlayerScreenTopBar(
                    selectedTopTab = selectedTopTab,
                    onSelectTopTab = handleTopTabSelection,
                    onBackClick = onBackClick,
                    onShareClick = onShareClick,
                    layoutMetrics = layoutMetrics
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("player_screen_content_pager"),
                beyondViewportPageCount = 1
            ) { page ->
                when (page.toPlayerTopTab()) {
                    PlayerTopTab.SONG -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxSize()
                                    .padding(
                                        start = layoutMetrics.horizontalPadding,
                                        top = 0.dp,
                                        end = layoutMetrics.horizontalPadding,
                                        bottom = layoutMetrics.bottomSectionReservedHeight
                                    ),
                                verticalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacing)
                            ) {
                                Spacer(modifier = Modifier.height(layoutMetrics.coverTopSpacing))
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .testTag("player_screen_song_content_top_anchor")
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = true),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(layoutMetrics.coverSize)
                                            .testTag("player_screen_song_page"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .testTag("player_screen_visual_section"),
                                            shape = RoundedCornerShape(28.dp),
                                            color = Color.White.copy(alpha = 0.08f),
                                            tonalElevation = 0.dp,
                                            shadowElevation = 0.dp
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(24.dp),
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
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = layoutMetrics.horizontalPadding)
                                    .navigationBarsPadding()
                                    .padding(bottom = layoutMetrics.verticalPadding)
                                    .testTag("player_screen_bottom_section"),
                                verticalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacing)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("player_screen_info_section")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = trackText.title,
                                                style = titleTextStyle,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = layoutMetrics.titleMaxLines,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.testTag("player_screen_title")
                                            )
                                            Text(
                                                text = trackText.artist,
                                                style = artistTextStyle,
                                                color = Color.White.copy(alpha = if (currentArtistId.isNullOrBlank()) 0.66f else 0.78f),
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

                                        PlayerToolActionButton(
                                            tag = "player_screen_favorite_button",
                                            icon = Icons.Rounded.FavoriteBorder,
                                            contentDescription = "收藏当前歌曲",
                                            buttonSize = layoutMetrics.toolButtonSize,
                                            iconSize = layoutMetrics.toolIconSize,
                                            onClick = onFavoriteClick
                                        )
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
                                            .padding(top = layoutMetrics.sectionSpacing)
                                            .testTag(lyricPresentation.summaryTag)
                                    )
                                }

                                PlayerToolActionRow(
                                    showSongWikiAction = showSongWikiAction,
                                    onShowSongWiki = onShowSongWiki,
                                    onMoreClick = onMoreClick,
                                    layoutMetrics = layoutMetrics
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("player_screen_progress_section"),
                                    verticalArrangement = Arrangement.spacedBy(layoutMetrics.progressSectionSpacing)
                                ) {
                                    PlayerProgressBarSlider(
                                        value = sliderValue,
                                        max = sliderMax,
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
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontSize = layoutMetrics.progressTimeFontSizeSp.sp
                                            ),
                                            color = Color.White.copy(alpha = 0.62f)
                                        )
                                        Text(
                                            text = totalDurationText,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontSize = layoutMetrics.progressTimeFontSizeSp.sp
                                            ),
                                            color = Color.White.copy(alpha = 0.62f)
                                        )
                                    }
                                }

                                PlaybackControls(
                                    hasSelection = true,
                                    hasPreviousTrack = hasPreviousTrack,
                                    hasNextTrack = hasNextTrack,
                                    playlistItemCount = playlistItemCount,
                                    isPreparing = isPreparing,
                                    isPlaying = isPlaying,
                                    isPaused = isPaused,
                                    playbackMode = playbackMode,
                                    onPlay = onPlay,
                                    onPlaylistClick = onTogglePlaylistSheet,
                                    onPlaybackModeClick = onCyclePlaybackMode,
                                    onPrevious = onPrevious,
                                    onNext = onNext,
                                    onPause = onPause,
                                    onResume = onResume,
                                    compactMode = compactControls,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("player_screen_controls_section")
                                )
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .testTag("player_screen_song_controls_bottom_anchor")
                                )
                            }
                        }
                    }

                    PlayerTopTab.LYRICS -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = layoutMetrics.horizontalPadding)
                                .testTag("player_screen_lyrics_page"),
                            verticalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacing)
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
private fun PlayerLyricsPage(
    lyricUiState: PlayerLyricUiState,
    activeLineIndex: Int,
    isVisible: Boolean,
    onRetryLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            LaunchedEffect(activeLineIndex, lyricUiState.lyrics.songId, isVisible) {
                if (isVisible && activeLineIndex >= 0) {
                    listState.animateScrollToItem((activeLineIndex - 3).coerceAtLeast(0))
                }
            }
            LazyColumn(
                state = listState,
                modifier = modifier
                    .fillMaxSize()
                    .testTag("player_screen_lyrics_list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp,
                    vertical = 56.dp
                ),
                verticalArrangement = Arrangement.spacedBy(22.dp)
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
                            lineHeight = 38.sp,
                            shadow = if (isActiveLine) {
                                Shadow(
                                    color = PlayerLyricsGlowColor.copy(alpha = visuals.glowAlpha),
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
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val boundedMax = max.coerceAtLeast(1f)
    val boundedValue = value.coerceIn(0f, boundedMax)
    val progressFraction = (boundedValue / boundedMax).coerceIn(0f, 1f)
    val thumbSize = 8.dp
    val trackHeight = 3.dp

    BoxWithConstraints(
        modifier = modifier
            .height(20.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = if (enabled) 0.18f else 0.10f))
                .testTag("player_screen_slider_track")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = if (enabled) 0.94f else 0.36f))
                    .testTag("player_screen_slider_active_track")
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = (maxWidth - thumbSize) * progressFraction)
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (enabled) 1f else 0.42f))
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

internal data class PlayerTrackText(
    val title: String,
    val artist: String
)

internal fun resolvePlayerTrackText(fileName: String): PlayerTrackText {
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
