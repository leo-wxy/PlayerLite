package com.wxy.playerlite.feature.main

internal data class HomeSurfaceMotionTargets(
    val overviewAlpha: Float,
    val overviewScale: Float,
    val overviewOffsetFraction: Float,
    val playerAlpha: Float,
    val playerScale: Float,
    val playerOffsetFraction: Float
)

internal object HomeSurfaceMotionSpec {
    fun targetsFor(mode: HomeSurfaceMode): HomeSurfaceMotionTargets {
        return when (mode) {
            HomeSurfaceMode.OVERVIEW -> HomeSurfaceMotionTargets(
                overviewAlpha = 1f,
                overviewScale = 1f,
                overviewOffsetFraction = 0f,
                playerAlpha = 0f,
                playerScale = 0.985f,
                playerOffsetFraction = 0.08f
            )

            HomeSurfaceMode.PLAYER_EXPANDED -> HomeSurfaceMotionTargets(
                overviewAlpha = 0f,
                overviewScale = 0.972f,
                overviewOffsetFraction = -0.03f,
                playerAlpha = 1f,
                playerScale = 1f,
                playerOffsetFraction = 0f
            )
        }
    }
}
