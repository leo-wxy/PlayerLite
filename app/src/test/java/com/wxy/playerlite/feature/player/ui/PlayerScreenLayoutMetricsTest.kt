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
        assertEquals(0.dp, compact.bottomSectionSideInset)
    }

    @Test
    fun resolvedMetrics_shouldClampArtworkAndTypographyToAvoidCrowdedLargePhoneLayout() {
        val metrics = resolvePlayerScreenLayoutMetrics(
            viewportWidthDp = 412f,
            viewportHeightDp = 915f
        )

        assertTrue(metrics.coverSize >= 292.dp)
        assertTrue(metrics.coverSize <= 304.dp)
        assertTrue(metrics.coverTopSpacing <= 28.dp)
        assertTrue(metrics.coverHostTopSpacing > metrics.coverTopSpacing)
        assertTrue(metrics.titleFontSizeSp <= 25f)
        assertTrue(metrics.artistFontSizeSp <= 16f)
        assertTrue(metrics.topBarActionButtonSize <= 40.dp)
        assertTrue(metrics.toolButtonSize <= 54.dp)
        assertTrue(metrics.toolIconSize >= 25.dp)
        assertTrue(metrics.bottomSectionSideInset in 6.dp..10.dp)
        assertTrue(metrics.bottomControlsSafeGap in 32.dp..34.dp)
        assertTrue(metrics.statusFontSizeSp in 12f..14f)
    }

    @Test
    fun resolvedMetrics_shouldKeepLyricsPageInsetsAlignedWithSongPageRhythm() {
        val metrics = resolvePlayerScreenLayoutMetrics(
            viewportWidthDp = 411f,
            viewportHeightDp = 891f
        )

        assertEquals(metrics.titleTopSpacing, metrics.lyricsTopInset)
        assertEquals(metrics.verticalPadding, metrics.lyricsBottomInset)
    }

    @Test
    fun resolvedMetrics_shouldKeepLargePhoneControlsGroupedBelowLyrics() {
        val metrics = resolvePlayerScreenLayoutMetrics(
            viewportWidthDp = 412f,
            viewportHeightDp = 915f
        )

        assertTrue(metrics.controlsGroupSpacing in 22.dp..30.dp)
        assertTrue(metrics.toolsTopSpacing in 26.dp..34.dp)
        assertTrue(metrics.qualityBottomSpacing in 14.dp..16.dp)
    }
}
