package com.wxy.playerlite.feature.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.playback.model.PlaybackMode

@Composable
internal fun PlaybackControls(
    hasSelection: Boolean,
    hasPreviousTrack: Boolean,
    hasNextTrack: Boolean,
    playlistItemCount: Int,
    isPreparing: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    playbackMode: PlaybackMode,
    onPlay: () -> Unit,
    onPlaylistClick: () -> Unit,
    onPlaybackModeClick: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val toggleEnabled = !isPreparing && (hasSelection || isPlaying || isPaused)
    val previousEnabled = !isPreparing && hasPreviousTrack
    val nextEnabled = !isPreparing && hasNextTrack
    val modeEnabled = !isPreparing && hasSelection
    val playlistEnabled = !isPreparing
    val showingPause = isPlaying
    val haloAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.24f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "play_halo_alpha"
    )
    val centerScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.06f else 1f,
        animationSpec = tween(durationMillis = 260),
        label = "play_center_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .testTag("player_screen_controls_strip")
    ) {
        SideControlButton(
            icon = playbackMode.modeIcon(),
            contentDescription = playbackMode.modeContentDescription(),
            badgeText = null,
            buttonTag = "player_screen_playback_mode_button",
            enabled = modeEnabled,
            palette = playbackMode.modePalette(),
            motionSpec = playbackMode.modeMotionSpec(),
            motionKey = playbackMode,
            onClick = onPlaybackModeClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
        )

        SideControlButton(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = "打开播放列表",
            badgeText = playlistItemCount
                .takeIf { it > 0 }
                ?.coerceAtMost(99)
                ?.toString(),
            buttonTag = "player_screen_playlist_button",
            enabled = playlistEnabled,
            palette = defaultSideControlPalette(),
            motionSpec = SideControlMotionSpec(),
            motionKey = "playlist",
            onClick = onPlaylistClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
        )

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = previousEnabled,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("player_screen_previous_button"),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0x1544B3A2),
                    contentColor = Color(0xFF0F766E),
                    disabledContainerColor = Color(0x1044B3A2),
                    disabledContentColor = Color(0x660F766E)
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier.size(88.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = haloAlpha))
                )

                FilledIconButton(
                    onClick = {
                        when {
                            isPlaying -> onPause()
                            isPaused -> onResume()
                            else -> onPlay()
                        }
                    },
                    enabled = toggleEnabled,
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("player_screen_toggle_button")
                        .graphicsLayer {
                            scaleX = centerScale
                            scaleY = centerScale
                        },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.58f)
                    )
                ) {
                    Icon(
                        imageVector = if (showingPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (showingPause) "暂停" else "播放",
                        modifier = Modifier
                            .size(34.dp)
                            .graphicsLayer {
                                rotationZ = if (showingPause) 0f else -8f
                            }
                    )
                }
            }

            IconButton(
                onClick = onNext,
                enabled = nextEnabled,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("player_screen_next_button"),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0x1544B3A2),
                    contentColor = Color(0xFF0F766E),
                    disabledContainerColor = Color(0x1044B3A2),
                    disabledContentColor = Color(0x660F766E)
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun SideControlButton(
    icon: ImageVector,
    contentDescription: String,
    badgeText: String?,
    buttonTag: String,
    enabled: Boolean,
    palette: SideControlPalette,
    motionSpec: SideControlMotionSpec,
    motionKey: Any,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseProgress = remember(motionKey) { Animatable(0f) }
    LaunchedEffect(motionKey, motionSpec) {
        pulseProgress.snapTo(0f)
        if (motionSpec.animated) {
            pulseProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = motionSpec.durationMs)
            )
            if (!motionSpec.repeat) {
                pulseProgress.snapTo(0f)
            }
        }
    }
    val pulsePhase = when {
        !motionSpec.animated -> 0f
        pulseProgress.value <= 0.5f -> pulseProgress.value * 2f
        else -> (1f - pulseProgress.value) * 2f
    }
    val animatedScale = lerp(
        start = motionSpec.minScale,
        stop = motionSpec.maxScale,
        fraction = pulsePhase
    )
    val animatedIconRotation = lerp(
        start = motionSpec.minIconRotationDeg,
        stop = motionSpec.maxIconRotationDeg,
        fraction = pulsePhase
    )
    val animatedHaloAlpha = lerp(
        start = motionSpec.minHaloAlpha,
        stop = motionSpec.maxHaloAlpha,
        fraction = pulsePhase
    )

    Box(
        modifier = modifier.size(58.dp),
        contentAlignment = Alignment.Center
    ) {
        if (animatedHaloAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(palette.contentColor.copy(alpha = animatedHaloAlpha))
            )
        }

        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(52.dp)
                .testTag(buttonTag)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = palette.containerColor,
                contentColor = palette.contentColor,
                disabledContainerColor = palette.disabledContainerColor,
                disabledContentColor = palette.disabledContentColor
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        rotationZ = animatedIconRotation
                    }
            )
        }

        if (!badgeText.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = badgeText,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction.coerceIn(0f, 1f)
}
