package com.wxy.playerlite.feature.player.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED

@Composable
internal fun LoginEntryButton(
    enabled: Boolean,
    isLoggedIn: Boolean,
    avatarUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.94f,
        animationSpec = tween(durationMillis = 200),
        label = "login_entry_button_scale"
    )

    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = buttonScale
            scaleY = buttonScale
        }.testTag("login_entry_button_root"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
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
            if (isLoggedIn && !avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "账户头像入口",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                            shape = CircleShape
                        )
                        .testTag("login_entry_avatar"),
                    contentScale = ContentScale.Crop,
                    error = rememberVectorPainter(Icons.Rounded.AccountCircle),
                    fallback = rememberVectorPainter(Icons.Rounded.AccountCircle),
                    placeholder = rememberVectorPainter(Icons.Rounded.AccountCircle)
                )
            } else {
                Icon(
                    imageVector = if (isLoggedIn) Icons.Rounded.AccountCircle else Icons.AutoMirrored.Rounded.Login,
                    contentDescription = if (isLoggedIn) "账户中心" else "登录入口",
                    modifier = Modifier
                        .size(20.dp)
                        .testTag(
                            if (isLoggedIn) {
                                "login_entry_account_icon"
                            } else {
                                "login_entry_login_icon"
                            }
                        )
                )
            }
        }
    }
}

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
internal fun ClearCacheButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.94f,
        animationSpec = tween(durationMillis = 200),
        label = "clear_cache_button_scale"
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
                imageVector = Icons.Rounded.Delete,
                contentDescription = "清理缓存",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun PlayerCoverCard(
    isPlaying: Boolean,
    isPaused: Boolean,
    coverUrl: String? = null,
    onBackdropColorExtracted: (Color) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val active = isPlaying && !isPaused
    val context = LocalContext.current
    val transition = rememberInfiniteTransition(label = "player_cover_motion")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "player_cover_pulse"
    )
    val coverScale = if (active) 1f + pulse * 0.01f else 1f
    val coverTranslationY = if (active) -2.5f * pulse else 0f
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(coverUrl)
            .allowHardware(false)
            .build()
    )

    LaunchedEffect(painter.state, coverUrl) {
        val successState = painter.state as? AsyncImagePainter.State.Success ?: return@LaunchedEffect
        val bitmap = (successState.result.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val color = withContext(Dispatchers.Default) {
            extractBackdropColorSafely(bitmap)
        }
        color?.let(onBackdropColorExtracted)
    }

    Box(
        modifier = modifier
            .testTag("player_screen_cover_card")
            .graphicsLayer {
                scaleX = coverScale
                scaleY = coverScale
                translationY = coverTranslationY
            }
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.03f)),
        contentAlignment = Alignment.Center
    ) {
        if (!coverUrl.isNullOrBlank()) {
            Image(
                painter = painter,
                contentDescription = "当前歌曲封面",
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("player_screen_cover_art"),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x18000000),
                                Color(0x52000000)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF5E6677),
                                Color(0xFF353842),
                                Color(0xFF1A1C23)
                            )
                        )
                    )
            )
            Text(
                text = "AUDIO",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
internal fun DeckDisc(
    isPlaying: Boolean,
    isPaused: Boolean,
    coverUrl: String? = null,
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
    val pausedAngle = remember { mutableFloatStateOf(14f) }
    val rotationOffset = remember { mutableFloatStateOf(14f) }
    LaunchedEffect(active) {
        if (active) {
            rotationOffset.floatValue = pausedAngle.floatValue - spinAngle
        }
    }
    val displayedRotation = if (active) {
        normalizeDeckDiscRotation(rotationOffset.floatValue + spinAngle)
    } else {
        pausedAngle.floatValue
    }
    LaunchedEffect(active, displayedRotation) {
        if (active) {
            pausedAngle.floatValue = displayedRotation
        }
    }

    Box(
        modifier = modifier
            .testTag("player_screen_disc_surface")
            .semantics {
                deckDiscRotationDegrees = displayedRotation
            }
            .graphicsLayer {
                rotationZ = displayedRotation
            },
        contentAlignment = Alignment.Center
    ) {
        if (!coverUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .testTag("player_screen_cover_art")
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "当前歌曲封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x330F161B),
                                    Color(0x800F161B)
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC0F161B))
                        .border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.22f),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFDF6EA))
                )
            }
        } else {
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
}

internal val DeckDiscRotationDegreesKey = SemanticsPropertyKey<Float>("DeckDiscRotationDegrees")

internal var SemanticsPropertyReceiver.deckDiscRotationDegrees by DeckDiscRotationDegreesKey

internal fun normalizeDeckDiscRotation(value: Float): Float {
    return ((value % 360f) + 360f) % 360f
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
