package com.wxy.playerlite.feature.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.designsystem.theme.PlayerLiteThemeContract
import com.wxy.playerlite.designsystem.theme.PlayerLiteVisualTheme
import com.wxy.playerlite.feature.player.model.PlayerMoreActionsPage
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.PlaybackSpeed
import kotlin.math.roundToInt

@Composable
internal fun PlayerMoreActionsSheet(
    visible: Boolean,
    page: PlayerMoreActionsPage,
    playbackSpeed: Float,
    audioEffectPreset: AudioEffectPreset,
    onDismiss: () -> Unit,
    onShowPlaybackSpeedSettings: () -> Unit,
    onShowAudioEffectSettings: () -> Unit,
    onReturnToRoot: () -> Unit,
    onSelectPlaybackSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val visualTokens = PlayerLiteVisualTheme.colors
    val brandPalette = PlayerLiteThemeContract.DefaultBrandPalettes.light
    val scrimInteraction = remember { MutableInteractionSource() }
    val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val normalizedPlaybackSpeed = PlaybackSpeed.normalizeValue(playbackSpeed)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
            animationSpec = tween(280),
            initialOffsetY = { it }
        ),
        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(
            animationSpec = tween(220),
            targetOffsetY = { it }
        ),
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.26f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = onDismiss
                    )
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.58f)
                    .testTag("player_more_actions_sheet_surface"),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = brandPalette.neutral,
                tonalElevation = 0.dp,
                shadowElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 20.dp,
                            top = 14.dp,
                            end = 20.dp,
                            bottom = 16.dp + navigationBottomPadding
                        ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 42.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(visualTokens.handleMuted)
                    )

                    PlayerMoreActionsSheetHeader(
                        page = page,
                        onDismiss = onDismiss,
                        onReturnToRoot = onReturnToRoot
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                    ) {
                        when (page) {
                            PlayerMoreActionsPage.ROOT -> {
                                PlayerMoreActionsRootPage(
                                    playbackSpeed = normalizedPlaybackSpeed,
                                    audioEffectPreset = audioEffectPreset,
                                    onShowPlaybackSpeedSettings = onShowPlaybackSpeedSettings,
                                    onShowAudioEffectSettings = onShowAudioEffectSettings,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            PlayerMoreActionsPage.SPEED -> {
                                PlayerMoreActionsSpeedPage(
                                    playbackSpeed = normalizedPlaybackSpeed,
                                    onSelectPlaybackSpeed = onSelectPlaybackSpeed,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            PlayerMoreActionsPage.AUDIO_EFFECT -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerMoreActionsSheetHeader(
    page: PlayerMoreActionsPage,
    onDismiss: () -> Unit,
    onReturnToRoot: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (page == PlayerMoreActionsPage.ROOT) {
            Box(modifier = Modifier.size(40.dp))
        } else {
            IconButton(
                onClick = onReturnToRoot,
                modifier = Modifier.testTag("player_more_actions_sheet_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回更多操作"
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (page) {
                    PlayerMoreActionsPage.ROOT -> "更多操作"
                    PlayerMoreActionsPage.SPEED -> "倍速设置"
                    PlayerMoreActionsPage.AUDIO_EFFECT -> "更多操作"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when (page) {
                    PlayerMoreActionsPage.ROOT -> "播放器设置入口"
                    PlayerMoreActionsPage.SPEED -> "当前浮层内滑动切换倍速"
                    PlayerMoreActionsPage.AUDIO_EFFECT -> "播放器设置入口"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("player_more_actions_sheet_close_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "关闭更多操作"
            )
        }
    }
}

@Composable
private fun PlayerMoreActionsRootPage(
    playbackSpeed: Float,
    audioEffectPreset: AudioEffectPreset,
    onShowPlaybackSpeedSettings: () -> Unit,
    onShowAudioEffectSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .testTag("player_more_actions_sheet_root"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlayerMoreActionsEntry(
            title = "音效设置",
            subtitle = "当前 ${audioEffectPreset.displayName}",
            icon = Icons.Rounded.GraphicEq,
            tag = "player_more_actions_entry_audio_effect",
            onClick = onShowAudioEffectSettings
        )
        PlayerMoreActionsEntry(
            title = "倍速设置",
            subtitle = "当前 ${PlaybackSpeed.format(playbackSpeed)}",
            icon = Icons.Rounded.Equalizer,
            tag = "player_more_actions_entry_playback_speed",
            onClick = onShowPlaybackSpeedSettings
        )
    }
}

@Composable
private fun PlayerMoreActionsSpeedPage(
    playbackSpeed: Float,
    onSelectPlaybackSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var speedPreviewIndex by remember(playbackSpeed) {
        mutableFloatStateOf(PlaybackSpeed.indexFromValue(playbackSpeed).toFloat())
    }
    val previewSpeed = PlaybackSpeed.fromIndex(speedPreviewIndex.roundToInt())
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .testTag("player_more_actions_sheet_speed_page"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = previewSpeed.label,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = PlayerLiteVisualTheme.colors.accentStrong,
            modifier = Modifier.testTag("player_more_actions_speed_value")
        )
        Text(
            text = "松手后立即生效",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = speedPreviewIndex,
            onValueChange = { value ->
                speedPreviewIndex = value.roundToInt().toFloat()
            },
            onValueChangeFinished = {
                onSelectPlaybackSpeed(
                    PlaybackSpeed.fromIndex(speedPreviewIndex.roundToInt()).value
                )
            },
            valueRange = 0f..15f,
            steps = 14,
            colors = SliderDefaults.colors(
                thumbColor = PlayerLiteVisualTheme.colors.accentStrong,
                activeTrackColor = PlayerLiteVisualTheme.colors.accentStrong,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            ),
            modifier = Modifier.testTag("player_more_actions_speed_slider")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0.5X",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "2.0X",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerMoreActionsEntry(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit
) {
    PlayerMoreActionsCard(
        tag = tag,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PlayerLiteVisualTheme.colors.accentStrong.copy(alpha = 0.12f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = PlayerLiteVisualTheme.colors.accentStrong
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerMoreActionsChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    PlayerMoreActionsCard(
        tag = tag,
        onClick = onClick,
        selected = selected
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) {
                    PlayerLiteVisualTheme.colors.accentStrong
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerMoreActionsCard(
    tag: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    content: @Composable () -> Unit
) {
    val visualTokens = PlayerLiteVisualTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) {
            visualTokens.accentStrong.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            content()
        }
    }
}
