package com.wxy.playerlite.feature.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

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
    modifier: Modifier = Modifier
) {
    val toggleEnabled = !isPreparing && (hasSelection || isPlaying || isPaused)
    val previousEnabled = !isPreparing && hasPreviousTrack
    val nextEnabled = !isPreparing && hasNextTrack
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = previousEnabled,
            modifier = Modifier.size(52.dp),
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
            modifier = Modifier.size(52.dp),
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
