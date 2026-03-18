package com.wxy.playerlite.feature.player.ui

internal data class LyricsAutoScrollRequest(
    val targetIndex: Int,
    val mode: LyricsAutoScrollMode
)

internal enum class LyricsAutoScrollMode {
    Snap,
    Animate
}

private const val MIN_PLAYBACK_FOLLOW_SCROLL_STEP = 2
private const val MAX_ANIMATED_LYRICS_STEP = 2

internal fun resolveLyricsAutoScrollTarget(
    activeLineIndex: Int,
    firstVisibleItemIndex: Int,
    leadingLines: Int = 3
): Int? {
    if (activeLineIndex < 0) {
        return null
    }
    val targetIndex = (activeLineIndex - leadingLines).coerceAtLeast(0)
    return targetIndex.takeIf { it != firstVisibleItemIndex }
}

internal fun resolveLyricsAutoScrollRequest(
    activeLineIndex: Int,
    firstVisibleItemIndex: Int,
    isInitialPlacement: Boolean,
    lastRequestedTargetIndex: Int? = null,
    minPlaybackFollowScrollStep: Int = MIN_PLAYBACK_FOLLOW_SCROLL_STEP,
    maxAnimatedStep: Int = MAX_ANIMATED_LYRICS_STEP,
    leadingLines: Int = 3
): LyricsAutoScrollRequest? {
    val targetIndex = resolveLyricsAutoScrollTarget(
        activeLineIndex = activeLineIndex,
        firstVisibleItemIndex = firstVisibleItemIndex,
        leadingLines = leadingLines
    ) ?: return null

    if (lastRequestedTargetIndex == targetIndex) {
        return null
    }

    val anchorIndex = lastRequestedTargetIndex ?: firstVisibleItemIndex
    val driftFromAnchor = kotlin.math.abs(targetIndex - anchorIndex)
    if (!isInitialPlacement && driftFromAnchor < minPlaybackFollowScrollStep) {
        return null
    }

    val mode = if (isInitialPlacement) {
        LyricsAutoScrollMode.Snap
    } else if (driftFromAnchor > maxAnimatedStep) {
        LyricsAutoScrollMode.Snap
    } else {
        LyricsAutoScrollMode.Animate
    }
    return LyricsAutoScrollRequest(targetIndex = targetIndex, mode = mode)
}
