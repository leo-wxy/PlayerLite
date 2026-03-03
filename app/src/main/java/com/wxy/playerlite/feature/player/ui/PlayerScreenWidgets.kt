package com.wxy.playerlite.feature.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED

@Composable
internal fun FloatingPickButton(
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
internal fun UiTestEntryButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.94f,
        animationSpec = tween(durationMillis = 200),
        label = "ui_test_button_scale"
    )

    Surface(
        modifier = modifier.graphicsLayer {
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
                imageVector = Icons.Rounded.Science,
                contentDescription = "UI测试入口",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun DeckDisc(
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
internal fun DeckProgressBar(
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
internal fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 0.dp,
    valueMaxLines: Int = Int.MAX_VALUE
) {
    Surface(
        modifier = modifier.defaultMinSize(minHeight = minHeight),
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
                fontWeight = FontWeight.SemiBold,
                maxLines = valueMaxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun formatRateLabel(rateHz: Int): String {
    return if (rateHz > 0) {
        "${rateHz}Hz"
    } else {
        "?Hz"
    }
}

internal fun formatChannelLabel(channels: Int): String {
    return if (channels > 0) {
        "${channels}ch"
    } else {
        "?ch"
    }
}

@Composable
internal fun PlaybackBadge(
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
