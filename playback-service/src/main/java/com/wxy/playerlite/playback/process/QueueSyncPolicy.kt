package com.wxy.playerlite.playback.process

import androidx.media3.common.C
import kotlin.math.abs

internal object QueueSyncPolicy {
    fun shouldRestorePosition(
        previousMediaId: String?,
        nextMediaId: String?,
        requestedStartPositionMs: Long,
        currentPositionMs: Long
    ): Boolean {
        if (requestedStartPositionMs == C.TIME_UNSET) {
            return false
        }
        val normalizedRequestedPositionMs = requestedStartPositionMs.coerceAtLeast(0L)
        if (normalizedRequestedPositionMs == 0L) {
            return false
        }
        if (previousMediaId.isNullOrBlank() || nextMediaId.isNullOrBlank()) {
            return true
        }
        if (previousMediaId != nextMediaId) {
            return true
        }
        return abs(currentPositionMs - normalizedRequestedPositionMs) >
            SAME_MEDIA_POSITION_RESTORE_TOLERANCE_MS
    }

    private const val SAME_MEDIA_POSITION_RESTORE_TOLERANCE_MS = 1_000L
}
