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
            containerColor = Color(0x1544B3A2),
            contentColor = Color(0xFF0F766E)
        )
        PlaybackMode.SINGLE_LOOP -> SideControlPalette(
            containerColor = Color(0x15F59E0B),
            contentColor = Color(0xFFB45309)
        )
        PlaybackMode.SHUFFLE -> SideControlPalette(
            containerColor = Color(0x156366F1),
            contentColor = Color(0xFF4F46E5)
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
            maxScale = 1.08f,
            minIconRotationDeg = -10f,
            maxIconRotationDeg = 10f,
            minHaloAlpha = 0.06f,
            maxHaloAlpha = 0.18f
        )
    }
}

internal fun defaultSideControlPalette(): SideControlPalette {
    return SideControlPalette(
        containerColor = Color(0x1544B3A2),
        contentColor = Color(0xFF0F766E)
    )
}
