package com.wxy.playerlite.feature.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

data class PlaybackControlsMetrics(
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

fun resolvePlaybackControlsMetrics(
    viewportWidthDp: Float,
    viewportHeightDp: Float,
    compactMode: Boolean = false,
    denseMode: Boolean = false
): PlaybackControlsMetrics {
    val shortestSide = minOf(viewportWidthDp, viewportHeightDp)
    val sideButtonScale = if (denseMode) 0.142f else 0.15f
    val sideButtonMin = if (denseMode) 48f else 50f
    val sideButtonMax = if (denseMode) 50f else 54f
    val sideButtonInnerMin = if (denseMode) 40f else 44f
    val sideButtonInnerMax = if (denseMode) 46f else 52f
    val sideIconMin = if (denseMode) 23f else 27f
    val sideIconMax = if (denseMode) 27f else 30f
    val centerButtonScale = if (denseMode) 1.34f else 1.36f
    val centerButtonMin = if (denseMode) 60f else 68f
    val centerButtonMax = if (denseMode) 66f else 72f
    val centerHaloPadding = if (denseMode) 6f else 6f
    val centerHaloMin = if (denseMode) 66f else 74f
    val centerHaloMax = if (denseMode) 72f else 78f
    val centerIconMin = if (denseMode) 23f else 26f
    val centerIconMax = if (denseMode) 28f else 31f
    val stripMaxHeight = if (denseMode) 76f else 86f
    val stripHeightFactor = if (denseMode) 0.004f else 0.006f
    val stripPaddingMin = if (denseMode) 0f else 2f
    val stripPaddingMax = if (denseMode) 6f else 8f
    val sideButtonSize = clampDp(
        value = shortestSide * sideButtonScale,
        min = sideButtonMin,
        max = sideButtonMax
    )
    val sideButtonInnerSize = clampDp(
        value = sideButtonSize.value * 0.88f,
        min = sideButtonInnerMin,
        max = sideButtonInnerMax
    )
    val sideIconSize = clampDp(
        value = sideButtonInnerSize.value * 0.58f,
        min = sideIconMin,
        max = sideIconMax
    )
    val centerButtonSize = clampDp(
        value = sideButtonSize.value * centerButtonScale,
        min = centerButtonMin,
        max = centerButtonMax
    )
    val centerHaloSize = clampDp(
        value = centerButtonSize.value + centerHaloPadding,
        min = centerHaloMin,
        max = centerHaloMax
    )
    val centerIconSize = clampDp(
        value = centerButtonSize.value * 0.44f,
        min = centerIconMin,
        max = centerIconMax
    )
    val baseStripHeight = clampDp(
        value = centerHaloSize.value + (viewportHeightDp * stripHeightFactor),
        min = centerHaloSize.value,
        max = stripMaxHeight
    )
    val controlsOffsetY = clampDp(
        value = viewportHeightDp * 0.003f,
        min = 0f,
        max = 3f
    )
    val stripHorizontalPadding = clampDp(
        value = viewportWidthDp * 0.012f,
        min = stripPaddingMin,
        max = stripPaddingMax
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
    isPreparing: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    onPlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    compactMode: Boolean = false,
    denseMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val toggleEnabled = hasSelection || isPlaying || isPaused || isPreparing
    val previousEnabled = hasPreviousTrack
    val nextEnabled = hasNextTrack
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
            viewportHeightDp = if (compactMode) maxHeight.value.coerceAtMost(700f) else maxHeight.value,
            compactMode = compactMode,
            denseMode = denseMode
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.stripHeight)
                .padding(horizontal = metrics.stripHorizontalPadding)
                .offset(y = metrics.controlsOffsetY),
            horizontalArrangement = Arrangement.spacedBy(
                space = if (denseMode) 28.dp else 38.dp,
                alignment = Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(metrics.sideButtonSize)
                    .testTag("player_screen_previous_button"),
                onClick = onPrevious,
                enabled = previousEnabled,
                shape = CircleShape,
                color = Color.Transparent,
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
                color = Color.Transparent,
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
