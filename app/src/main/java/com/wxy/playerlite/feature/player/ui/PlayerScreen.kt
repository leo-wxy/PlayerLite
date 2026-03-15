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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState
import com.wxy.playerlite.feature.player.model.SongWikiSummary
import com.wxy.playerlite.feature.player.ui.components.PlaybackControls
import com.wxy.playerlite.feature.player.ui.components.PlaylistBottomSheet
import com.wxy.playerlite.playback.model.PlaybackMode

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
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .testTag("player_screen_top_bar"),
    ) {
        Box(
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            PlayerTopBarActionButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回首页",
                tag = "player_screen_top_back_button",
                onClick = onBackClick
            )
        }
        Text(
            text = "歌曲",
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp)
                .testTag("player_screen_top_title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            PlayerTopBarActionButton(
                icon = Icons.Rounded.Share,
                contentDescription = "分享当前歌曲",
                tag = "player_screen_top_share_button",
                onClick = onShareClick
            )
        }
    }
}

@Composable
private fun PlayerTopBarActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag(tag),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.08f),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(42.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription
            )
        }
    }
}

@Composable
private fun PlayerToolActionRow(
    showSongWikiAction: Boolean,
    onFavoriteClick: () -> Unit,
    onShowSongWiki: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("player_screen_tool_row"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerToolActionButton(
            tag = "player_screen_favorite_button",
            icon = Icons.Rounded.FavoriteBorder,
            contentDescription = "收藏当前歌曲",
            onClick = onFavoriteClick
        )

        if (showSongWikiAction) {
            PlayerToolActionButton(
                tag = "player_screen_song_wiki_tool_button",
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = "打开歌曲百科",
                onClick = onShowSongWiki
            )
        } else {
            Spacer(modifier = Modifier.size(46.dp))
        }

        PlayerToolActionButton(
            tag = "player_screen_more_button",
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = "更多操作",
            onClick = onMoreClick
        )
    }
}

@Composable
private fun PlayerToolActionButton(
    tag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.testTag(tag),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.04f)
    ) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White.copy(alpha = 0.86f)
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription
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
        val narrowWidth = maxWidth < 340.dp
        val compactHeight = maxHeight < 720.dp
        val veryCompactHeight = maxHeight < 640.dp
        val ultraCompactHeight = maxHeight < 580.dp
        val horizontalPadding = when {
            ultraCompactHeight -> 12.dp
            narrowWidth -> 14.dp
            compactHeight -> 18.dp
            else -> 20.dp
        }
        val verticalPadding = when {
            ultraCompactHeight -> 8.dp
            veryCompactHeight -> 10.dp
            compactHeight -> 12.dp
            else -> 14.dp
        }
        val sectionSpacing = when {
            ultraCompactHeight -> 8.dp
            veryCompactHeight -> 10.dp
            compactHeight -> 12.dp
            else -> 14.dp
        }
        val coverSideInset = when {
            ultraCompactHeight -> 0.dp
            narrowWidth -> 2.dp
            compactHeight -> 4.dp
            else -> 6.dp
        }
        val coverTopSpacing = when {
            ultraCompactHeight -> 4.dp
            veryCompactHeight -> 6.dp
            compactHeight -> 8.dp
            else -> 10.dp
        }
        val titleTextStyle = when {
            ultraCompactHeight -> MaterialTheme.typography.headlineSmall
            veryCompactHeight -> MaterialTheme.typography.headlineMedium
            compactHeight -> MaterialTheme.typography.headlineLarge
            else -> MaterialTheme.typography.displaySmall
        }
        val artistTextStyle = when {
            ultraCompactHeight -> MaterialTheme.typography.bodyMedium
            veryCompactHeight -> MaterialTheme.typography.bodyLarge
            compactHeight -> MaterialTheme.typography.titleSmall
            else -> MaterialTheme.typography.titleMedium
        }
        val lyricTextStyle = when {
            ultraCompactHeight -> MaterialTheme.typography.bodySmall
            veryCompactHeight -> MaterialTheme.typography.bodyMedium
            else -> MaterialTheme.typography.bodyLarge
        }
        val coverBottomSpacerWeight = when {
            ultraCompactHeight -> 0.02f
            veryCompactHeight -> 0.06f
            compactHeight -> 0.10f
            else -> 0.16f
        }
        val bottomSectionReservedHeight = when {
            ultraCompactHeight -> 212.dp
            veryCompactHeight -> 238.dp
            compactHeight -> 264.dp
            else -> 292.dp
        }
        val shouldShowStatusHint = !isPreparing &&
            status.isNotBlank() &&
            status !in setOf("正在播放", "Playing", "Paused", "Preparing", "Stopped")

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .padding(
                        start = horizontalPadding,
                        top = 0.dp,
                        end = horizontalPadding,
                        bottom = bottomSectionReservedHeight
                    ),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
            ) {
                PlayerScreenTopBar(
                    onBackClick = onBackClick,
                    onShareClick = onShareClick
                )
                Spacer(modifier = Modifier.height(coverTopSpacing))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_screen_visual_section"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = coverSideInset)
                            .aspectRatio(1f)
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

                Spacer(modifier = Modifier.weight(coverBottomSpacerWeight, fill = true))
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .navigationBarsPadding()
                    .padding(bottom = verticalPadding)
                    .testTag("player_screen_bottom_section"),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_screen_info_section")
                ) {
                    Text(
                        text = trackText.title,
                        style = titleTextStyle,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = if (veryCompactHeight) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("player_screen_title")
                    )
                    Text(
                        text = trackText.artist,
                        style = artistTextStyle,
                        color = Color.White.copy(alpha = if (currentArtistId.isNullOrBlank()) 0.72f else 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 6.dp)
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
                    Text(
                        text = "歌词待补充",
                        style = lyricTextStyle,
                        color = Color.White.copy(alpha = 0.48f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .testTag("player_screen_lyric_placeholder")
                    )
                }

                PlayerToolActionRow(
                    showSongWikiAction = showSongWikiAction,
                    onFavoriteClick = onFavoriteClick,
                    onShowSongWiki = onShowSongWiki,
                    onMoreClick = onMoreClick
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_screen_progress_section"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.62f)
                        )
                        Text(
                            text = totalDurationText,
                            style = MaterialTheme.typography.labelMedium,
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
                    compactMode = veryCompactHeight || narrowWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_screen_controls_section")
                )
            }
        }
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
