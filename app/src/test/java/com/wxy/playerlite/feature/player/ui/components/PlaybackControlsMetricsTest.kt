package com.wxy.playerlite.feature.player.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControlsMetricsTest {
    @Test
    fun resolvedMetrics_shouldKeepCenterHaloInsideControlStripAcrossModes() {
        listOf(
            resolvePlaybackControlsMetrics(narrowWidthMode = false, compactMode = false),
            resolvePlaybackControlsMetrics(narrowWidthMode = false, compactMode = true),
            resolvePlaybackControlsMetrics(narrowWidthMode = true, compactMode = false),
            resolvePlaybackControlsMetrics(narrowWidthMode = true, compactMode = true)
        ).forEach { metrics ->
            assertTrue(
                "Expected strip height ${metrics.stripHeight} to be at least center halo ${metrics.centerHaloSize}",
                metrics.stripHeight >= metrics.centerHaloSize
            )
            assertTrue(
                "Expected center button ${metrics.centerButtonSize} to stay larger than side button ${metrics.sideButtonInnerSize}",
                metrics.centerButtonSize > metrics.sideButtonInnerSize
            )
        }
    }

    @Test
    fun resolvedMetrics_shouldKeepCenterAndSideButtonsNearGoldenRatio() {
        listOf(
            resolvePlaybackControlsMetrics(narrowWidthMode = false, compactMode = false),
            resolvePlaybackControlsMetrics(narrowWidthMode = false, compactMode = true),
            resolvePlaybackControlsMetrics(narrowWidthMode = true, compactMode = false),
            resolvePlaybackControlsMetrics(narrowWidthMode = true, compactMode = true)
        ).forEach { metrics ->
            val ratio = metrics.centerButtonSize.value / metrics.sideButtonSize.value
            assertTrue(
                "Expected center-to-side ratio to stay clearly larger but not overpower the side controls, but ratio was $ratio for $metrics",
                ratio in 1.30f..1.38f
            )
        }
    }
}
