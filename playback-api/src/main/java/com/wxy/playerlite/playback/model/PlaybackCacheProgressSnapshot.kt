package com.wxy.playerlite.playback.model

data class PlaybackCacheProgressSnapshot(
    val cachedBytes: Long,
    val totalBytes: Long? = null,
    val displayRatio: Float,
    val displayStartRatio: Float = 0f,
    val isFullyCached: Boolean,
    val isEstimated: Boolean
) {
    val normalizedDisplayStartRatio: Float
        get() = if (isFullyCached) {
            0f
        } else {
            displayStartRatio.coerceIn(0f, normalizedDisplayRatio)
        }

    val normalizedDisplayRatio: Float
        get() = if (isFullyCached) {
            1f
        } else {
            displayRatio.coerceIn(0f, 1f)
        }
}
