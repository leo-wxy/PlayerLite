package com.wxy.playerlite.feature.player.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

internal data class PlayerScreenLayoutMetrics(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val sectionSpacing: Dp,
    val coverSideInset: Dp,
    val coverTopSpacing: Dp,
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
    val progressTimeFontSizeSp: Float,
    val lyricsTopInset: Dp,
    val lyricsBottomInset: Dp,
    val summaryTopPadding: Dp
)

internal fun resolvePlayerScreenLayoutMetrics(
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
        value = viewportHeightDp * 0.018f,
        min = 10f,
        max = 18f
    )
    val coverSideInset = clampDp(
        value = viewportWidthDp * 0.014f,
        min = 0f,
        max = 10f
    )
    val coverTopSpacing = clampDp(
        value = viewportHeightDp * 0.012f,
        min = 6f,
        max = 12f
    )
    val bottomSectionReservedHeight = clampDp(
        value = viewportHeightDp * 0.36f,
        min = 228f,
        max = 320f
    )
    val topBarHeight = clampDp(
        value = viewportHeightDp * 0.06f,
        min = 48f,
        max = 56f
    )
    val maxCoverWidth = viewportWidthDp - ((horizontalPadding.value + coverSideInset.value) * 2f)
    val safeCoverHeight = (
        viewportHeightDp -
            topBarHeight.value -
            coverTopSpacing.value -
            bottomSectionReservedHeight.value -
            24f
        ).coerceAtLeast(148f)
    val coverSize = min(
        viewportWidthDp * 0.82f,
        min(viewportHeightDp * 0.34f, min(maxCoverWidth, safeCoverHeight))
    ).coerceAtMost(352f).dp
    val titleFontSizeSp = clampSp(
        value = viewportWidthDp * 0.082f,
        min = 28f,
        max = 40f
    )
    val artistFontSizeSp = clampSp(
        value = viewportWidthDp * 0.053f,
        min = 18f,
        max = 24f
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
        value = shortestSide * 0.118f,
        min = 42f,
        max = 52f
    )
    val toolIconSize = clampDp(
        value = toolButtonSize.value * 0.48f,
        min = 20f,
        max = 24f
    )
    val progressSectionSpacing = clampDp(
        value = viewportHeightDp * 0.0065f,
        min = 4f,
        max = 8f
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
        progressTimeFontSizeSp = progressTimeFontSizeSp,
        lyricsTopInset = coverTopSpacing,
        lyricsBottomInset = verticalPadding,
        summaryTopPadding = clampDp(
            value = viewportHeightDp * 0.006f,
            min = 4f,
            max = 8f
        )
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
