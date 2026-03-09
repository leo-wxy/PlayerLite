package com.wxy.playerlite.feature.player.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.ui.components.PlaybackControls
import com.wxy.playerlite.feature.player.ui.components.PlaylistBottomSheet
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.PlaybackOutputInfo

@Composable
internal fun PlayerScreen(
    fileName: String,
    status: String,
    audioMeta: AudioMetaDisplay,
    playbackOutputInfo: PlaybackOutputInfo?,
    hasSelection: Boolean,
    playlistItems: List<PlaylistItem>,
    activePlaylistIndex: Int,
    showPlaylistSheet: Boolean,
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
    modifier: Modifier = Modifier,
    onPickAudio: () -> Unit,
    onRunUiTestEntry: () -> Unit,
    onClearCache: () -> Unit,
    onTogglePlaylistSheet: () -> Unit,
    onDismissPlaylistSheet: () -> Unit,
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
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
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
        val seekEnabled = isSeekSupported && isPlaying && !isPaused
        val progressRatio = if (durationMs > 0L) sliderValue / sliderMax else 0f
        val progressPercent = (progressRatio * 100f).toInt().coerceIn(0, 100)
        val fallbackInputText = if (audioMeta.sampleRate != "-" || audioMeta.channels != "-") {
            val channelsText = if (audioMeta.channels == "-") "?ch" else "${audioMeta.channels}ch"
            "${audioMeta.sampleRate}/$channelsText"
        } else {
            "-"
        }
        val routeInputText = playbackOutputInfo?.let { info ->
            "${formatRateLabel(info.inputSampleRateHz)}/${formatChannelLabel(info.inputChannels)}/${info.inputEncoding}"
        } ?: fallbackInputText
        val routeOutputText = playbackOutputInfo?.let { info ->
            "${formatRateLabel(info.outputSampleRateHz)}/${formatChannelLabel(info.outputChannels)}/${info.outputEncoding}"
        } ?: "-"
        val routeModeText = playbackOutputInfo?.let { info ->
            if (info.usesResampler) "重采样" else "直通"
        } ?: "-"

        var reveal by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            reveal = true
        }
        val contentAlpha by animateFloatAsState(
            targetValue = if (reveal) 1f else 0f,
            animationSpec = tween(durationMillis = 550),
            label = "content_alpha"
        )
        val contentOffset by animateDpAsState(
            targetValue = if (reveal) 0.dp else 18.dp,
            animationSpec = tween(durationMillis = 550),
            label = "content_offset"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFF3DE),
                            Color(0xFFFFE6CF),
                            Color(0xFFFDEFD8)
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.65f)
                    .fillMaxHeight(0.24f)
                    .clip(RoundedCornerShape(36.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x22F97316),
                                Color(0x2244B3A2)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.42f)
                    .fillMaxHeight(0.18f)
                    .clip(RoundedCornerShape(42.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x22619B8A),
                                Color(0x00FFFFFF)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .graphicsLayer {
                        alpha = contentAlpha
                        translationY = contentOffset.toPx()
                    },
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            tonalElevation = 6.dp,
                            shadowElevation = 6.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "PLAYER LITE",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Text(
                                            text = "Local Audio Deck",
                                            style = MaterialTheme.typography.headlineSmall
                                        )
                                        Text(
                                            text = fileName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = status,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        PlaybackBadge(
                                            isPreparing = isPreparing,
                                            playbackState = playbackState
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        DeckDisc(
                                            isPlaying = isPlaying,
                                            isPaused = isPaused,
                                            modifier = Modifier.size(108.dp)
                                        )
                                        Text(
                                            text = "$progressPercent%",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Text(
                                    text = if (isPlaying && !isPaused) "Native AudioTrack route active" else "Cue loaded and waiting",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                DeckProgressBar(
                                    progressPercent = progressPercent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                )
                            }
                        }

                        FloatingPickButton(
                            enabled = !isPreparing,
                            onClick = onPickAudio,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = (-8).dp)
                        )

                        UiTestEntryButton(
                            enabled = !isPreparing,
                            onClick = onRunUiTestEntry,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(y = (-8).dp)
                        )

                        ClearCacheButton(
                            enabled = !isPreparing,
                            onClick = onClearCache,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 48.dp, y = (-8).dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        tonalElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Audio Info",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatTile(
                                    label = "Codec",
                                    value = audioMeta.codec,
                                    modifier = Modifier.weight(1f),
                                    minHeight = 74.dp,
                                    valueMaxLines = 1
                                )
                                StatTile(
                                    label = "Bit Rate",
                                    value = audioMeta.bitRate,
                                    modifier = Modifier.weight(1f),
                                    minHeight = 74.dp,
                                    valueMaxLines = 1
                                )
                                StatTile(
                                    label = "Output Mode",
                                    value = routeModeText,
                                    modifier = Modifier.weight(1f),
                                    minHeight = 74.dp,
                                    valueMaxLines = 1
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatTile(
                                    label = "Output Input",
                                    value = routeInputText,
                                    modifier = Modifier.weight(1f)
                                )
                                StatTile(
                                    label = "Output Final",
                                    value = routeOutputText,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Slider(
                            value = sliderValue,
                            onValueChange = { value -> onSeekValueChange(value.toLong()) },
                            onValueChangeFinished = onSeekFinished,
                            valueRange = 0f..sliderMax,
                            enabled = seekEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$currentDurationText / $totalDurationText",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$progressPercent%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                PlaybackControls(
                    hasSelection = hasSelection,
                    hasPreviousTrack = hasPreviousTrack,
                    hasNextTrack = hasNextTrack,
                    playlistItemCount = playlistItems.size,
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
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
