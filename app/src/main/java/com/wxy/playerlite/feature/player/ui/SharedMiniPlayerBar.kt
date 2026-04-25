package com.wxy.playerlite.feature.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wxy.playerlite.designsystem.theme.PlayerLiteVisualTheme
import com.wxy.playerlite.feature.main.HomeChromeLayoutSpec
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.resolvePlayerDisplayContentProjection
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

private const val SharedMiniPlayerSwipeThresholdFraction = 0.3f
private const val SharedMiniPlayerSwipeVisualLimitFraction = 0.42f
private val SharedMiniPlayerArtworkInset = 7.dp

internal data class SharedMiniPlayerBarState(
    val contentLine: String,
    val progress: Float,
    val cacheProgressStart: Float? = null,
    val cacheProgress: Float? = null,
    val isPlaying: Boolean,
    val artworkUrl: String?
)

internal data class SharedMiniPlayerBarTestTags(
    val cardTag: String,
    val prefix: String
) {
    val progressLineTag: String
        get() = "${prefix}_progress_line"

    val progressFillTag: String
        get() = "${prefix}_progress_fill"

    val progressCacheFillTag: String
        get() = "${prefix}_progress_cache_fill"

    val barTag: String
        get() = "${prefix}_bar"

    val bodyTag: String
        get() = "${prefix}_body"

    val artworkTag: String
        get() = "${prefix}_artwork"

    val artworkImageTag: String
        get() = "${prefix}_artwork_image"

    val artworkPlaceholderTag: String
        get() = "${prefix}_artwork_placeholder"

    val songAreaTag: String
        get() = "${prefix}_song_area"

    val titleTag: String
        get() = "${prefix}_title"

    val playPauseButtonTag: String
        get() = "${prefix}_play_pause_button"

    val playlistButtonTag: String
        get() = "${prefix}_playlist_button"
}

internal enum class SharedMiniPlayerOpenPlayerClickTarget {
    Card,
    Body
}

@Composable
internal fun resolveSharedMiniPlayerBarState(playerState: PlayerUiState): SharedMiniPlayerBarState? {
    if (!playerState.hasSelection) {
        return null
    }
    val displayProjection = resolvePlayerDisplayContentProjection(playerState)
    val progress = if (playerState.durationMs > 0L) {
        (playerState.displayedSeekMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    return SharedMiniPlayerBarState(
        contentLine = displayProjection.miniPlayerContentLine,
        progress = progress,
        cacheProgressStart = playerState.displayedCacheProgressStartRatio,
        cacheProgress = playerState.displayedCacheProgressRatio,
        isPlaying = playerState.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING,
        artworkUrl = playerState.currentCoverUrl
            ?.takeIf { it.isNotBlank() }
            ?: playerState.playlistItems
                .getOrNull(playerState.activePlaylistIndex)
                ?.coverUrl
                ?.takeIf { it.isNotBlank() }
    )
}

@Composable
internal fun SharedMiniPlayerBar(
    state: SharedMiniPlayerBarState,
    testTags: SharedMiniPlayerBarTestTags,
    openPlayerClickTarget: SharedMiniPlayerOpenPlayerClickTarget,
    canSkipPrevious: Boolean = true,
    canSkipNext: Boolean = true,
    onOpenPlayer: () -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swipeOffsetPx = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val colors = PlayerLiteVisualTheme.colors
    val miniPlayerShape = RoundedCornerShape(HomeChromeLayoutSpec.miniPlayerCornerRadius)
    val artworkSize = HomeChromeLayoutSpec.miniPlayerArtworkSize - SharedMiniPlayerArtworkInset * 2

    Surface(
        modifier = modifier
            .fillMaxWidth(HomeChromeLayoutSpec.miniPlayerWidthFraction)
            .widthIn(max = HomeChromeLayoutSpec.miniPlayerMaxWidth)
            .heightIn(min = HomeChromeLayoutSpec.miniPlayerMinHeight),
        shape = miniPlayerShape,
        color = Color.White.copy(alpha = 0.995f),
        tonalElevation = 0.dp,
        shadowElevation = HomeChromeLayoutSpec.miniPlayerShadowElevation,
        border = BorderStroke(
            width = 1.dp,
            color = colors.dividerSubtle.copy(alpha = 0.42f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HomeChromeLayoutSpec.miniPlayerMinHeight)
                .clip(miniPlayerShape)
                .testTag(testTags.cardTag)
                .then(
                    if (openPlayerClickTarget == SharedMiniPlayerOpenPlayerClickTarget.Card) {
                        Modifier.clickable(onClick = onOpenPlayer)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        horizontal = HomeChromeLayoutSpec.miniPlayerProgressTrackHorizontalPadding,
                        vertical = HomeChromeLayoutSpec.miniPlayerProgressTrackVerticalPadding
                    )
                    .height(HomeChromeLayoutSpec.miniPlayerProgressTrackHeight)
                    .background(
                        color = colors.miniPlayerProgressTrack.copy(
                            alpha = HomeChromeLayoutSpec.miniPlayerProgressTrackAlpha
                        ),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .testTag(testTags.progressLineTag)
            ) {
                val progressFraction = state.progress.coerceIn(0f, 1f)
                val cacheStartFraction = state.cacheProgressStart
                    ?.coerceIn(0f, 1f)
                    ?: 0f
                val cacheFraction = state.cacheProgress
                    ?.coerceIn(0f, 1f)
                    ?: 0f
                val cacheWidthFraction = (cacheFraction - cacheStartFraction).coerceIn(0f, 1f)
                if (cacheWidthFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = maxWidth * cacheStartFraction)
                            .width(maxWidth * cacheWidthFraction)
                            .fillMaxSize()
                            .background(
                                color = colors.miniPlayerProgressFill.copy(alpha = 0.38f),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .testTag(testTags.progressCacheFillTag)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxSize()
                        .background(
                            color = colors.miniPlayerProgressFill,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .testTag(testTags.progressFillTag)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = HomeChromeLayoutSpec.miniPlayerMinHeight)
                    .testTag(testTags.barTag),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (openPlayerClickTarget == SharedMiniPlayerOpenPlayerClickTarget.Body) {
                                    Modifier.clickable(onClick = onOpenPlayer)
                                } else {
                                    Modifier
                                }
                            )
                            .testTag(testTags.bodyTag),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Box(modifier = Modifier.padding(SharedMiniPlayerArtworkInset)) {
                            Surface(
                                modifier = Modifier
                                    .size(artworkSize)
                                    .testTag(testTags.artworkTag),
                                shape = miniPlayerShape,
                                color = colors.textSecondary.copy(alpha = 0.08f)
                            ) {
                                if (!state.artworkUrl.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag(testTags.artworkImageTag)
                                    ) {
                                        AsyncImage(
                                            model = state.artworkUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.Transparent,
                                                            Color.Black.copy(alpha = 0.12f)
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag(testTags.artworkPlaceholderTag),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AccountCircle,
                                            contentDescription = null,
                                            tint = colors.textSecondary.copy(alpha = 0.42f),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                                .testTag(testTags.songAreaTag)
                                .graphicsLayer {
                                    translationX = swipeOffsetPx.value
                                }
                                .pointerInput(canSkipPrevious, canSkipNext, onSkipPrevious, onSkipNext) {
                                    val visualLimitPx = max(
                                        size.width * SharedMiniPlayerSwipeVisualLimitFraction,
                                        72.dp.toPx()
                                    )
                                    val swipeThresholdPx = max(
                                        size.width * SharedMiniPlayerSwipeThresholdFraction,
                                        56.dp.toPx()
                                    )
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            coroutineScope.launch {
                                                swipeOffsetPx.stop()
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            val nextOffset = (swipeOffsetPx.value + dragAmount)
                                                .coerceIn(-visualLimitPx, visualLimitPx)
                                            coroutineScope.launch {
                                                swipeOffsetPx.snapTo(nextOffset)
                                            }
                                        },
                                        onDragCancel = {
                                            coroutineScope.launch {
                                                swipeOffsetPx.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                )
                                            }
                                        },
                                        onDragEnd = {
                                            val finalOffset = swipeOffsetPx.value
                                            val triggerOffset = when {
                                                finalOffset <= -swipeThresholdPx && canSkipNext -> {
                                                    onSkipNext()
                                                    -visualLimitPx * 0.75f
                                                }

                                                finalOffset >= swipeThresholdPx && canSkipPrevious -> {
                                                    onSkipPrevious()
                                                    visualLimitPx * 0.75f
                                                }

                                                else -> null
                                            }
                                            coroutineScope.launch {
                                                if (
                                                    triggerOffset != null &&
                                                    abs(finalOffset - triggerOffset) > 1f
                                                ) {
                                                    swipeOffsetPx.animateTo(
                                                        targetValue = triggerOffset,
                                                        animationSpec = tween(durationMillis = 90)
                                                    )
                                                }
                                                swipeOffsetPx.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                )
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = state.contentLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        repeatDelayMillis = 1_000,
                                        spacing = MarqueeSpacing(24.dp)
                                    )
                                    .testTag(testTags.titleTag)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SharedMiniPlayerPrimaryButton(
                            isPlaying = state.isPlaying,
                            testTag = testTags.playPauseButtonTag,
                            onClick = onTogglePlayback
                        )
                        IconButton(
                            onClick = onOpenPlaylist,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = colors.textSecondary
                            ),
                            modifier = Modifier
                                .size(HomeChromeLayoutSpec.miniPlayerPlaylistButtonSize)
                                .testTag(testTags.playlistButtonTag)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "播放列表",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedMiniPlayerPrimaryButton(
    isPlaying: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = PlayerLiteVisualTheme.colors.accentStrong,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White
            ),
            modifier = Modifier
                .size(HomeChromeLayoutSpec.miniPlayerPrimaryButtonSize)
                .testTag(testTag)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(HomeChromeLayoutSpec.miniPlayerPrimaryIconSize)
            )
        }
    }
}
