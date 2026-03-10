package com.wxy.playerlite.feature.player.ui

internal data class PlayerScreenMotionState(
    val alpha: Float,
    val offsetDp: Int
)

internal object PlayerScreenMotionSpec {
    fun resolve(enableEnterMotion: Boolean, hasRevealed: Boolean): PlayerScreenMotionState {
        if (!enableEnterMotion) {
            return PlayerScreenMotionState(
                alpha = 1f,
                offsetDp = 0
            )
        }

        return if (hasRevealed) {
            PlayerScreenMotionState(
                alpha = 1f,
                offsetDp = 0
            )
        } else {
            PlayerScreenMotionState(
                alpha = 0f,
                offsetDp = 12
            )
        }
    }
}
