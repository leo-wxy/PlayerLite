package com.wxy.playerlite.feature.player.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.wxy.playerlite.playback.model.PlaybackMode

internal data class SideControlPalette(
    val containerColor: Color,
    val contentColor: Color
) {
    val disabledContainerColor: Color
        get() = containerColor.copy(alpha = 0.68f)

    val disabledContentColor: Color
        get() = contentColor.copy(alpha = 0.42f)
}

internal data class SideControlMotionSpec(
    val animated: Boolean = false,
    val repeat: Boolean = false,
    val durationMs: Int = 700,
    val minScale: Float = 1f,
    val maxScale: Float = 1f,
    val minIconRotationDeg: Float = 0f,
    val maxIconRotationDeg: Float = 0f,
    val minHaloAlpha: Float = 0f,
    val maxHaloAlpha: Float = 0f
)

internal fun PlaybackMode.modeIcon(): ImageVector {
    return when (this) {
        PlaybackMode.LIST_LOOP -> Icons.Rounded.Autorenew
        PlaybackMode.SINGLE_LOOP -> Icons.Rounded.RepeatOne
        PlaybackMode.SHUFFLE -> Icons.Rounded.Shuffle
    }
}

internal fun PlaybackMode.modeContentDescription(): String {
    return when (this) {
        PlaybackMode.LIST_LOOP -> "切换播放模式，当前为列表循环"
        PlaybackMode.SINGLE_LOOP -> "切换播放模式，当前为单曲循环"
        PlaybackMode.SHUFFLE -> "切换播放模式，当前为随机播放"
    }
}

internal fun PlaybackMode.modePalette(): SideControlPalette {
    return when (this) {
        PlaybackMode.LIST_LOOP -> SideControlPalette(
            containerColor = Color(0x1A9EAFCB),
            contentColor = Color.White.copy(alpha = 0.84f)
        )
        PlaybackMode.SINGLE_LOOP -> SideControlPalette(
            containerColor = Color(0x20F5C96E),
            contentColor = Color(0xFFFFE2A8)
        )
        PlaybackMode.SHUFFLE -> SideControlPalette(
            containerColor = Color(0x2095C5AF),
            contentColor = Color(0xFFC7F1DF)
        )
    }
}

internal fun PlaybackMode.modeMotionSpec(): SideControlMotionSpec {
    return when (this) {
        PlaybackMode.LIST_LOOP,
        PlaybackMode.SINGLE_LOOP -> SideControlMotionSpec()
        PlaybackMode.SHUFFLE -> SideControlMotionSpec(
            animated = true,
            repeat = false,
            durationMs = 720,
            minScale = 1f,
            maxScale = 1.04f,
            minIconRotationDeg = -8f,
            maxIconRotationDeg = 8f,
            minHaloAlpha = 0.04f,
            maxHaloAlpha = 0.10f
        )
    }
}

internal fun defaultSideControlPalette(): SideControlPalette {
    return SideControlPalette(
        containerColor = Color.White.copy(alpha = 0.08f),
        contentColor = Color.White.copy(alpha = 0.84f)
    )
}
