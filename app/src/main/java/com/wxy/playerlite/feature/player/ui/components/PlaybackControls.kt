package com.wxy.playerlite.feature.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.wxy.playerlite.playback.model.PlaybackMode

internal data class PlaybackControlsMetrics(
    val stripHeight: Dp,
    val sideButtonSize: Dp,
    val sideButtonInnerSize: Dp,
    val sideIconSize: Dp,
    val centerHaloSize: Dp,
    val centerButtonSize: Dp,
    val centerIconSize: Dp,
    val controlsOffsetY: Dp,
    val stripHorizontalPadding: Dp
)

internal fun resolvePlaybackControlsMetrics(
    viewportWidthDp: Float,
    viewportHeightDp: Float
): PlaybackControlsMetrics {
    val shortestSide = minOf(viewportWidthDp, viewportHeightDp)
    val sideButtonSize = clampDp(
        value = shortestSide * 0.16f,
        min = 48f,
        max = 58f
    )
    val sideButtonInnerSize = clampDp(
        value = sideButtonSize.value * 0.88f,
        min = 44f,
        max = 52f
    )
    val sideIconSize = clampDp(
        value = sideButtonInnerSize.value * 0.58f,
        min = 24f,
        max = 30f
    )
    val centerButtonSize = clampDp(
        value = sideButtonSize.value * 1.19f,
        min = 58f,
        max = 69f
    )
    val centerHaloSize = clampDp(
        value = centerButtonSize.value + 12f,
        min = 72f,
        max = 82f
    )
    val centerIconSize = clampDp(
        value = centerButtonSize.value * 0.44f,
        min = 26f,
        max = 31f
    )
    val baseStripHeight = clampDp(
        value = centerHaloSize.value + (viewportHeightDp * 0.012f),
        min = centerHaloSize.value,
        max = 92f
    )
    val controlsOffsetY = clampDp(
        value = viewportHeightDp * 0.003f,
        min = 0f,
        max = 3f
    )
    val stripHorizontalPadding = clampDp(
        value = viewportWidthDp * 0.012f,
        min = 2f,
        max = 8f
    )
    return PlaybackControlsMetrics(
        stripHeight = if (baseStripHeight < centerHaloSize) centerHaloSize else baseStripHeight,
        sideButtonSize = sideButtonSize,
        sideButtonInnerSize = sideButtonInnerSize,
        sideIconSize = sideIconSize,
        centerHaloSize = centerHaloSize,
        centerButtonSize = centerButtonSize,
        centerIconSize = centerIconSize,
        controlsOffsetY = controlsOffsetY,
        stripHorizontalPadding = stripHorizontalPadding
    )
}

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
    compactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val toggleEnabled = hasSelection || isPlaying || isPaused || isPreparing
    val previousEnabled = hasPreviousTrack
    val nextEnabled = hasNextTrack
    val modeEnabled = hasSelection
    val playlistEnabled = hasSelection || playlistItemCount > 0
    val showingPause = isPlaying
    val haloAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.16f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "play_halo_alpha"
    )
    val centerScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.03f else 1f,
        animationSpec = tween(durationMillis = 260),
        label = "play_center_scale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("player_screen_controls_strip")
    ) {
        val metrics = resolvePlaybackControlsMetrics(
            viewportWidthDp = maxWidth.value,
            viewportHeightDp = if (compactMode) maxHeight.value.coerceAtMost(700f) else maxHeight.value
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.stripHeight)
                .padding(horizontal = metrics.stripHorizontalPadding)
                .offset(y = metrics.controlsOffsetY),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
                buttonSize = metrics.sideButtonSize,
                innerButtonSize = metrics.sideButtonInnerSize,
                iconSize = metrics.sideIconSize,
                onClick = onPlaybackModeClick
            )

            Surface(
                modifier = Modifier
                    .size(metrics.sideButtonSize)
                    .testTag("player_screen_previous_button"),
                onClick = onPrevious,
                enabled = previousEnabled,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.10f),
                contentColor = Color.White.copy(alpha = 0.92f)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "上一首",
                        modifier = Modifier.size(metrics.sideIconSize)
                    )
                }
            }

            Box(
                modifier = Modifier.size(metrics.centerHaloSize),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(metrics.centerHaloSize)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = haloAlpha * 0.7f))
                )

                Surface(
                    onClick = {
                        when {
                            isPlaying -> onPause()
                            isPaused -> onResume()
                            else -> onPlay()
                        }
                    },
                    enabled = toggleEnabled,
                    modifier = Modifier
                        .size(metrics.centerButtonSize)
                        .testTag("player_screen_toggle_button")
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (showingPause) "暂停" else "播放"
                        }
                        .graphicsLayer {
                            scaleX = centerScale
                            scaleY = centerScale
                        },
                    shape = CircleShape,
                    color = Color.White,
                    contentColor = Color(0xFF131419)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (showingPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (showingPause) "暂停" else "播放",
                            modifier = Modifier
                                .size(metrics.centerIconSize)
                                .graphicsLayer {
                                    rotationZ = if (showingPause) 0f else -8f
                                }
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .size(metrics.sideButtonSize)
                    .testTag("player_screen_next_button"),
                onClick = onNext,
                enabled = nextEnabled,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.10f),
                contentColor = Color.White.copy(alpha = 0.92f)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(metrics.sideIconSize)
                    )
                }
            }

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
                buttonSize = metrics.sideButtonSize,
                innerButtonSize = metrics.sideButtonInnerSize,
                iconSize = metrics.sideIconSize,
                onClick = onPlaylistClick
            )
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
    buttonSize: androidx.compose.ui.unit.Dp,
    innerButtonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
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
        modifier = modifier
            .size(buttonSize),
        contentAlignment = Alignment.Center
    ) {
        if (animatedHaloAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape)
                    .background(palette.contentColor.copy(alpha = animatedHaloAlpha))
            )
        }

        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(innerButtonSize)
                .testTag(buttonTag)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                },
            shape = CircleShape,
            color = if (enabled) palette.containerColor else palette.disabledContainerColor,
            contentColor = if (enabled) palette.contentColor else palette.disabledContentColor
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            rotationZ = animatedIconRotation
                        }
                )
            }
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

private fun clampDp(
    value: Float,
    min: Float,
    max: Float
): Dp = value.coerceIn(min, max).dp
