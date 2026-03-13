package com.wxy.playerlite.feature.player.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    onSeekFinished: () -> Unit
) {
    val isPlaying = playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING
    val isPaused = playbackState == AUDIO_TRACK_PLAYSTATE_PAUSED
    val hasPreviousTrack = activePlaylistIndex > 0
    val hasNextTrack = activePlaylistIndex >= 0 && activePlaylistIndex < playlistItems.lastIndex
    val hasKnownDuration = durationMs > 0L
    val sliderMax = if (hasKnownDuration) {
        durationMs.toFloat()
    } else {
        maxOf(300_000L, seekValueMs + 30_000L).toFloat()
    }
    val sliderValue = seekValueMs.coerceIn(0L, sliderMax.toLong()).toFloat()
    val seekEnabled = isSeekSupported && (isPlaying || isPaused)
    val currentSongId = playlistItems
        .getOrNull(activePlaylistIndex)
        ?.songId
        ?.takeIf { it.isNotBlank() }

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
            if (showSongWikiInlineButton && currentSongId != null) {
                PlayerSongWikiButton(
                    onClick = onShowSongWiki,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                )
            }

            PlayerScreenContent(
                fileName = fileName,
                status = status,
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
                onSeekValueChange = onSeekValueChange,
                onSeekFinished = onSeekFinished,
                onPlay = onPlay,
                onPrevious = onPrevious,
                onNext = onNext,
                onPause = onPause,
                onResume = onResume,
                onCyclePlaybackMode = onCyclePlaybackMode,
                onTogglePlaylistSheet = onTogglePlaylistSheet,
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
                imageVector = Icons.Rounded.MenuBook,
                contentDescription = "歌曲百科"
            )
        }
    }
}

@Composable
private fun PlayerScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFF4EA),
                    Color(0xFFFDEBDF),
                    Color(0xFFF8F0EC)
                )
            )
        ),
        content = content
    )
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
    status: String,
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
    onSeekValueChange: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onPlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onTogglePlaylistSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trackText = resolvePlayerTrackText(fileName)

    Column(
        modifier = modifier.padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x29D44B4B),
                            Color(0x12FFFFFF)
                        )
                    )
                )
                .testTag("player_screen_visual_section"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PlaybackBadge(
                    isPreparing = isPreparing,
                    playbackState = when {
                        isPlaying -> AUDIO_TRACK_PLAYSTATE_PLAYING
                        isPaused -> AUDIO_TRACK_PLAYSTATE_PAUSED
                        else -> AUDIO_TRACK_PLAYSTATE_STOPPED
                    }
                )
                DeckDisc(
                    isPlaying = isPlaying,
                    isPaused = isPaused,
                    modifier = Modifier.size(220.dp)
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("player_screen_progress_section"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = trackText.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("player_screen_title")
                )
                Text(
                    text = trackText.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("player_screen_artist")
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { value -> onSeekValueChange(value.toLong()) },
                    onValueChangeFinished = onSeekFinished,
                    valueRange = 0f..sliderMax,
                    enabled = seekEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                    ),
                    modifier = Modifier.testTag("player_screen_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentDurationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = totalDurationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("player_screen_controls_section"),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
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
