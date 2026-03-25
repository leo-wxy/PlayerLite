package com.wxy.playerlite.feature.detail

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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wxy.playerlite.designsystem.theme.PlayerLiteVisualTheme
import com.wxy.playerlite.feature.main.HomeChromeLayoutSpec
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.resolvePlayerDisplayContentProjection
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.launch

private const val DetailMiniPlayerSwipeThresholdFraction = 0.3f
private const val DetailMiniPlayerSwipeVisualLimitFraction = 0.42f

internal val DetailMiniPlayerBottomPadding = 0.dp
internal val DetailMiniPlayerContentPadding = HomeChromeLayoutSpec.miniPlayerMinHeight

private data class DetailMiniPlayerState(
    val contentLine: String,
    val progress: Float,
    val isPlaying: Boolean,
    val artworkUrl: String?
)

@Composable
internal fun BoxScope.DetailMiniPlayerHost(
    bottomPadding: Dp,
    content: @Composable (Modifier) -> Unit
) {
    content(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 20.dp, end = 20.dp, bottom = bottomPadding)
    )
}

@Composable
internal fun DetailMiniPlayerBar(
    playerState: PlayerUiState,
    onOpenPlayer: () -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val miniPlayerState = resolveDetailMiniPlayerState(playerState) ?: return
    val swipeOffsetPx = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val colors = PlayerLiteVisualTheme.colors
    val canSkipPrevious = playerState.canSkipPrevious
    val canSkipNext = playerState.canSkipNext
    val miniPlayerShape = RoundedCornerShape(HomeChromeLayoutSpec.miniPlayerCornerRadius)
    Surface(
        modifier = modifier
            .fillMaxWidth(HomeChromeLayoutSpec.miniPlayerWidthFraction)
            .widthIn(max = HomeChromeLayoutSpec.miniPlayerMaxWidth)
            .heightIn(min = HomeChromeLayoutSpec.miniPlayerMinHeight)
            .testTag("detail_mini_player_card"),
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
                .clickable(onClick = onOpenPlayer),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
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
                    .testTag("detail_mini_player_progress_line")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(miniPlayerState.progress.coerceIn(0f, 1f))
                        .fillMaxSize()
                        .background(
                            color = colors.miniPlayerProgressFill,
                            shape = RoundedCornerShape(999.dp)
                        )
                        .testTag("detail_mini_player_progress_fill")
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = HomeChromeLayoutSpec.miniPlayerMinHeight)
                    .testTag("detail_mini_player_bar"),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_mini_player_body"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(HomeChromeLayoutSpec.miniPlayerArtworkSize)
                                .testTag("detail_mini_player_artwork"),
                            shape = RoundedCornerShape(14.dp),
                            color = colors.textSecondary.copy(alpha = 0.08f)
                        ) {
                            if (!miniPlayerState.artworkUrl.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("detail_mini_player_artwork_image")
                                ) {
                                    AsyncImage(
                                        model = miniPlayerState.artworkUrl,
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
                                        .testTag("detail_mini_player_artwork_placeholder"),
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

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 2.dp)
                                .testTag("detail_mini_player_song_area")
                                .graphicsLayer {
                                    translationX = swipeOffsetPx.value
                                }
                                .pointerInput(canSkipPrevious, canSkipNext) {
                                    val visualLimitPx = max(
                                        size.width * DetailMiniPlayerSwipeVisualLimitFraction,
                                        72.dp.toPx()
                                    )
                                    val swipeThresholdPx = max(
                                        size.width * DetailMiniPlayerSwipeThresholdFraction,
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
                                text = miniPlayerState.contentLine,
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
                                    .testTag("detail_mini_player_title")
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DetailMiniPlayerPrimaryButton(
                            isPlaying = miniPlayerState.isPlaying,
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
                                .testTag("detail_mini_player_playlist_button")
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

private fun resolveDetailMiniPlayerState(playerState: PlayerUiState): DetailMiniPlayerState? {
    if (!playerState.hasSelection) {
        return null
    }
    val displayProjection = resolvePlayerDisplayContentProjection(playerState)
    val progress = if (playerState.durationMs > 0L) {
        (playerState.displayedSeekMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    return DetailMiniPlayerState(
        contentLine = displayProjection.miniPlayerContentLine,
        progress = progress,
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
private fun DetailMiniPlayerPrimaryButton(
    isPlaying: Boolean,
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
                .testTag("detail_mini_player_play_pause_button")
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(HomeChromeLayoutSpec.miniPlayerPrimaryIconSize)
            )
        }
    }
}
