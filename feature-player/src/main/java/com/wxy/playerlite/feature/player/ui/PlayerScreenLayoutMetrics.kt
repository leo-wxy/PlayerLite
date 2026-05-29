package com.wxy.playerlite.feature.player.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

data class PlayerScreenLayoutMetrics(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val sectionSpacing: Dp,
    val coverSideInset: Dp,
    val coverTopSpacing: Dp,
    val songInfoHeight: Dp,
    val titleTopSpacing: Dp,
    val coverHostTopSpacing: Dp,
    val coverSize: Dp,
    val titleFontSizeSp: Float,
    val artistFontSizeSp: Float,
    val lyricFontSizeSp: Float,
    val titleMaxLines: Int,
    val bottomSectionReservedHeight: Dp,
    val topBarHeight: Dp,
    val topBarActionButtonSize: Dp,
    val topTabTextSizeSp: Float,
    val topTabHorizontalPadding: Dp,
    val toolButtonSize: Dp,
    val toolIconSize: Dp,
    val progressSectionSpacing: Dp,
    val lyricBelowCoverSpacing: Dp,
    val qualityBottomSpacing: Dp,
    val progressTimeFontSizeSp: Float,
    val lyricsTopInset: Dp,
    val lyricsBottomInset: Dp,
    val summaryTopPadding: Dp
)

fun resolvePlayerScreenLayoutMetrics(
    viewportWidthDp: Float,
    viewportHeightDp: Float
): PlayerScreenLayoutMetrics {
    val shortestSide = min(viewportWidthDp, viewportHeightDp)
    val horizontalPadding = clampDp(
        value = viewportWidthDp * 0.048f,
        min = 14f,
        max = 24f
    )
    val verticalPadding = clampDp(
        value = viewportHeightDp * 0.022f,
        min = 12f,
        max = 22f
    )
    val sectionSpacing = clampDp(
        value = viewportHeightDp * 0.015f,
        min = 8f,
        max = 16f
    )
    val coverSideInset = clampDp(
        value = viewportWidthDp * 0.01f,
        min = 0f,
        max = 6f
    )
    val coverTopSpacing = clampDp(
        value = viewportHeightDp * 0.021f,
        min = 14f,
        max = 28f
    )
    val topInfoReservedHeight = clampDp(
        value = viewportHeightDp * 0.088f,
        min = 64f,
        max = 88f
    )
    val bottomSectionReservedHeight = clampDp(
        value = viewportHeightDp * 0.33f,
        min = 256f,
        max = 304f
    )
    val topBarHeight = clampDp(
        value = viewportHeightDp * 0.06f,
        min = 48f,
        max = 56f
    )
    val summaryTopPadding = clampDp(
        value = viewportHeightDp * 0.0035f,
        min = 2f,
        max = 6f
    )
    val titleTopSpacing = clampDp(
        value = viewportHeightDp * 0.011f,
        min = 8f,
        max = 12f
    )
    val coverHostTopSpacing = topInfoReservedHeight + coverTopSpacing
    val maxCoverWidth = viewportWidthDp - ((horizontalPadding.value + coverSideInset.value) * 2f)
    val minimumCoverSize = when {
        viewportHeightDp < 600f -> 96f
        viewportHeightDp < 720f -> 132f
        else -> 148f
    }
    val safeCoverHeight = (
        viewportHeightDp -
            topBarHeight.value -
            coverHostTopSpacing.value -
            bottomSectionReservedHeight.value -
            sectionSpacing.value -
            summaryTopPadding.value -
            titleTopSpacing.value -
            8f
        ).coerceAtLeast(minimumCoverSize)
    val coverSize = min(
        viewportWidthDp * 0.76f,
        min(viewportHeightDp * 0.345f, min(maxCoverWidth, safeCoverHeight))
    ).coerceIn(minimumCoverSize, 320f).dp
    val titleFontSizeSp = clampSp(
        value = viewportWidthDp * 0.059f,
        min = 21f,
        max = 25f
    )
    val artistFontSizeSp = clampSp(
        value = viewportWidthDp * 0.037f,
        min = 14f,
        max = 16f
    )
    val lyricFontSizeSp = clampSp(
        value = viewportWidthDp * 0.041f,
        min = 14f,
        max = 18f
    )
    val topBarActionButtonSize = clampDp(
        value = shortestSide * 0.1f,
        min = 36f,
        max = 40f
    )
    val topTabTextSizeSp = clampSp(
        value = viewportWidthDp * 0.057f,
        min = 18f,
        max = 23f
    )
    val topTabHorizontalPadding = clampDp(
        value = viewportWidthDp * 0.026f,
        min = 8f,
        max = 14f
    )
    val toolButtonSize = clampDp(
        value = shortestSide * 0.126f,
        min = 48f,
        max = 54f
    )
    val toolIconSize = clampDp(
        value = toolButtonSize.value * 0.48f,
        min = 23f,
        max = 26f
    )
    val progressSectionSpacing = clampDp(
        value = viewportHeightDp * 0.0065f,
        min = 4f,
        max = 8f
    )
    val lyricBelowCoverSpacing = clampDp(
        value = viewportHeightDp * 0.024f,
        min = if (viewportHeightDp < 600f) 8f else 14f,
        max = 24f
    )
    val qualityBottomSpacing = clampDp(
        value = viewportHeightDp * 0.017f,
        min = 10f,
        max = 16f
    )
    val progressTimeFontSizeSp = clampSp(
        value = viewportWidthDp * 0.034f,
        min = 13f,
        max = 16f
    )
    return PlayerScreenLayoutMetrics(
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
        sectionSpacing = sectionSpacing,
        coverSideInset = coverSideInset,
        coverTopSpacing = coverTopSpacing,
        songInfoHeight = topInfoReservedHeight,
        titleTopSpacing = titleTopSpacing,
        coverHostTopSpacing = coverHostTopSpacing,
        coverSize = coverSize,
        titleFontSizeSp = titleFontSizeSp,
        artistFontSizeSp = artistFontSizeSp,
        lyricFontSizeSp = lyricFontSizeSp,
        titleMaxLines = if (viewportHeightDp < 760f) 1 else 2,
        bottomSectionReservedHeight = bottomSectionReservedHeight,
        topBarHeight = topBarHeight,
        topBarActionButtonSize = topBarActionButtonSize,
        topTabTextSizeSp = topTabTextSizeSp,
        topTabHorizontalPadding = topTabHorizontalPadding,
        toolButtonSize = toolButtonSize,
        toolIconSize = toolIconSize,
        progressSectionSpacing = progressSectionSpacing,
        lyricBelowCoverSpacing = lyricBelowCoverSpacing,
        qualityBottomSpacing = qualityBottomSpacing,
        progressTimeFontSizeSp = progressTimeFontSizeSp,
        lyricsTopInset = titleTopSpacing,
        lyricsBottomInset = verticalPadding,
        summaryTopPadding = summaryTopPadding
    )
}

private fun clampDp(
    value: Float,
    min: Float,
    max: Float
): Dp = value.coerceIn(min, max).dp

private fun clampSp(
    value: Float,
    min: Float,
    max: Float
): Float = value.coerceIn(min, max)
