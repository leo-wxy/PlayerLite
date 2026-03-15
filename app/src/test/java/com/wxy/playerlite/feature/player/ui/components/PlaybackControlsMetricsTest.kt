package com.wxy.playerlite.feature.player.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControlsMetricsTest {
    @Test
    fun resolvedMetrics_shouldScaleDownOnCompactViewportButKeepTouchTargets() {
        val regular = resolvePlaybackControlsMetrics(
            viewportWidthDp = 411f,
            viewportHeightDp = 891f
        )
        val compact = resolvePlaybackControlsMetrics(
            viewportWidthDp = 320f,
            viewportHeightDp = 640f
        )

        assertTrue(regular.centerButtonSize > compact.centerButtonSize)
        assertTrue(regular.sideButtonSize > compact.sideButtonSize)
        assertTrue(
            "Expected compact side button to keep a safe touch target, but was ${compact.sideButtonSize}",
            compact.sideButtonSize >= 48.dp
        )
        assertTrue(
            "Expected strip height ${compact.stripHeight} to stay above center halo ${compact.centerHaloSize}",
            compact.stripHeight >= compact.centerHaloSize
        )
    }

    @Test
    fun resolvedMetrics_shouldKeepCenterButtonDominantButNotOverweight() {
        val metrics = resolvePlaybackControlsMetrics(
            viewportWidthDp = 411f,
            viewportHeightDp = 891f
        )

        val ratio = metrics.centerButtonSize.value / metrics.sideButtonSize.value
        assertTrue(
            "Expected center-to-side ratio to stay clearly larger but still restrained, but ratio was $ratio for $metrics",
            ratio in 1.14f..1.24f
        )
        assertTrue(
            "Expected regular center button to stay under an oversized look, but was ${metrics.centerButtonSize}",
            metrics.centerButtonSize <= 70.dp
        )
    }
}
