package com.wxy.playerlite.playback.process

import androidx.media3.common.C

internal object QueueSyncPolicy {
    fun shouldRestorePosition(
        previousMediaId: String?,
        nextMediaId: String?,
        requestedStartPositionMs: Long
    ): Boolean {
        if (requestedStartPositionMs == C.TIME_UNSET) {
            return false
        }
        if (previousMediaId.isNullOrBlank() || nextMediaId.isNullOrBlank()) {
            return true
        }
        return previousMediaId != nextMediaId
    }
}
