package com.wxy.playerlite.feature.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.feature.player.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.feature.player.ui.components.PlaybackControls
import com.wxy.playerlite.feature.player.ui.components.PlaylistBottomSheet
import com.wxy.playerlite.feature.player.ui.components.PlaylistFloatingButton
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.playlist.PlaylistItem

@Composable
internal fun PlayerScreen(
    fileName: String,
    status: String,
    audioMeta: AudioMetaDisplay,
    hasSelection: Boolean,
    playlistItems: List<PlaylistItem>,
    activePlaylistIndex: Int,
    showPlaylistSheet: Boolean,
    isPreparing: Boolean,
    playbackState: Int,
    seekValueMs: Long,
    currentDurationText: String,
    durationMs: Long,
    totalDurationText: String,
    modifier: Modifier = Modifier,
    onPickAudio: () -> Unit,
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
        val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
        val sliderValue = seekValueMs.coerceIn(0L, durationMs.coerceAtLeast(0L)).toFloat()
        val seekEnabled = durationMs > 0L && isPlaying && !isPaused
        val progressRatio = if (durationMs > 0L) sliderValue / sliderMax else 0f
        val progressPercent = (progressRatio * 100f).toInt().coerceIn(0, 100)

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
                    .verticalScroll(rememberScrollState())
                    .graphicsLayer {
                        alpha = contentAlpha
                        translationY = contentOffset.toPx()
                    },
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
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                label = "Codec",
                                value = audioMeta.codec,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = "Sample Rate",
                                value = audioMeta.sampleRate,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                label = "Channels",
                                value = audioMeta.channels,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = "Bit Rate",
                                value = audioMeta.bitRate,
                                modifier = Modifier.weight(1f)
                            )
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
                    isPreparing = isPreparing,
                    isPlaying = isPlaying,
                    isPaused = isPaused,
                    onPlay = onPlay,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onPause = onPause,
                    onResume = onResume,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            PlaylistFloatingButton(
                itemCount = playlistItems.size,
                onClick = onTogglePlaylistSheet,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
            )

            PlaylistBottomSheet(
                visible = showPlaylistSheet,
                items = playlistItems,
                activeIndex = activePlaylistIndex,
                onDismiss = onDismissPlaylistSheet,
                onSelect = onSelectPlaylistItem,
                onRemove = onRemovePlaylistItem,
                onMove = onMovePlaylistItem,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun FloatingPickButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val floating = rememberInfiniteTransition(label = "pick_float_motion")
    val floatY by floating.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pick_float_y"
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.94f,
        animationSpec = tween(durationMillis = 200),
        label = "pick_button_scale"
    )

    Surface(
        modifier = modifier.graphicsLayer {
            translationY = floatY
            scaleX = buttonScale
            scaleY = buttonScale
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(42.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = "Pick File",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DeckDisc(
    isPlaying: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val active = isPlaying && !isPaused
    val phaseTransition = rememberInfiniteTransition(label = "deck_motion")
    val spinAngle by phaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "deck_spin"
    )

    val discScale by animateFloatAsState(
        targetValue = if (active) 1f else 0.94f,
        animationSpec = tween(durationMillis = 300),
        label = "deck_scale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            rotationZ = if (active) spinAngle else 14f
            scaleX = discScale
            scaleY = discScale
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF29414A),
                        Color(0xFF17242A),
                        Color(0xFF0F161B)
                    )
                ),
                radius = radius
            )
            drawCircle(
                color = Color(0x66FFFFFF),
                radius = radius * 0.72f
            )
            drawCircle(
                color = Color(0x99FFFFFF),
                radius = radius * 0.15f
            )
        }
        Text(
            text = "AUDIO",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFFDF6EA)
        )
    }
}

@Composable
private fun DeckProgressBar(
    progressPercent: Int,
    modifier: Modifier = Modifier
) {
    val progressTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val progressStartColor = MaterialTheme.colorScheme.secondary
    val progressEndColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val ratio = (progressPercent.coerceIn(0, 100) / 100f)
        drawRoundRect(
            color = progressTrackColor,
            cornerRadius = CornerRadius(size.height, size.height)
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    progressStartColor,
                    progressEndColor
                )
            ),
            size = Size(size.width * ratio, size.height),
            cornerRadius = CornerRadius(size.height, size.height)
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PlaybackBadge(
    isPreparing: Boolean,
    playbackState: Int
) {
    val (label, tone) = when {
        isPreparing -> "Preparing" to Color(0xFFD97706)
        playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING -> "Playing" to Color(0xFF0F766E)
        playbackState == AUDIO_TRACK_PLAYSTATE_PAUSED -> "Paused" to Color(0xFF0E7490)
        playbackState == AUDIO_TRACK_PLAYSTATE_STOPPED -> "Stopped" to Color(0xFF475569)
        else -> "Idle" to Color(0xFF64748B)
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tone.copy(alpha = 0.14f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = tone
        )
    }
}
