package com.wxy.playerlite.feature.player.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerScreenLayoutMetricsTest {
    @Test
    fun resolvedMetrics_shouldScaleWithViewportInsteadOfUsingFixedBuckets() {
        val compact = resolvePlayerScreenLayoutMetrics(
            viewportWidthDp = 320f,
            viewportHeightDp = 640f
        )
        val regular = resolvePlayerScreenLayoutMetrics(
            viewportWidthDp = 411f,
            viewportHeightDp = 891f
        )

        assertTrue(regular.coverSize > compact.coverSize)
        assertTrue(regular.titleFontSizeSp > compact.titleFontSizeSp)
        assertTrue(regular.bottomSectionReservedHeight > compact.bottomSectionReservedHeight)
        assertTrue(regular.toolButtonSize > compact.toolButtonSize)
    }

    @Test
    fun resolvedMetrics_shouldClampArtworkAndTypographyToAvoidCrowdedLargePhoneLayout() {
        val metrics = resolvePlayerScreenLayoutMetrics(
            viewportWidthDp = 412f,
            viewportHeightDp = 915f
        )

        assertTrue(metrics.coverSize <= 352.dp)
        assertTrue(metrics.titleFontSizeSp <= 40f)
        assertTrue(metrics.topBarActionButtonSize <= 40.dp)
        assertTrue(metrics.toolButtonSize <= 52.dp)
    }

    @Test
    fun resolvedMetrics_shouldKeepLyricsPageInsetsAlignedWithSongPageRhythm() {
        val metrics = resolvePlayerScreenLayoutMetrics(
            viewportWidthDp = 411f,
            viewportHeightDp = 891f
        )

        assertEquals(metrics.coverTopSpacing, metrics.lyricsTopInset)
        assertEquals(metrics.verticalPadding, metrics.lyricsBottomInset)
    }
}
