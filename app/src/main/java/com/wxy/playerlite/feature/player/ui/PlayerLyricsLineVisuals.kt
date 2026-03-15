package com.wxy.playerlite.feature.player.ui

import androidx.compose.ui.text.font.FontWeight

internal data class PlayerLyricsLineVisuals(
    val scale: Float,
    val fontWeight: FontWeight,
    val inactiveAlphaFloor: Float,
    val glowAlpha: Float
)

internal fun resolvePlayerLyricsLineVisuals(isActiveLine: Boolean): PlayerLyricsLineVisuals {
    val inactiveAlphaFloor = 0.28f
    return if (isActiveLine) {
        PlayerLyricsLineVisuals(
            scale = 1.018f,
            fontWeight = FontWeight.SemiBold,
            inactiveAlphaFloor = inactiveAlphaFloor,
            glowAlpha = 0.26f
        )
    } else {
        PlayerLyricsLineVisuals(
            scale = 1.0f,
            fontWeight = FontWeight.Normal,
            inactiveAlphaFloor = inactiveAlphaFloor,
            glowAlpha = 0f
        )
    }
}
