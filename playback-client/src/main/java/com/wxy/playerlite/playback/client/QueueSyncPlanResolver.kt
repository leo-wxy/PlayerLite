package com.wxy.playerlite.playback.client

import androidx.media3.common.C
import kotlin.math.abs

internal object QueueSyncPlanResolver {
    fun shouldReplaceQueue(
        currentMediaIds: List<String>,
        currentIndex: Int,
        currentMediaId: String?,
        currentPositionMs: Long,
        requestedMediaIds: List<String>,
        requestedIndex: Int,
        requestedStartPositionMs: Long
    ): Boolean {
        if (currentMediaIds.size != requestedMediaIds.size) {
            return true
        }
        if (currentMediaIds != requestedMediaIds) {
            return true
        }
        val normalizedRequestedIndex = requestedIndex.coerceIn(0, requestedMediaIds.lastIndex)
        val requestedCurrentMediaId = requestedMediaIds.getOrNull(normalizedRequestedIndex)
        if (!currentMediaId.isNullOrBlank()) {
            if (currentMediaId != requestedCurrentMediaId) {
                return true
            }
            if (shouldRealignStartPosition(currentPositionMs, requestedStartPositionMs)) {
                return true
            }
            return false
        }
        if (currentIndex != normalizedRequestedIndex) {
            return true
        }
        return shouldRealignStartPosition(currentPositionMs, requestedStartPositionMs)
    }

    private fun shouldRealignStartPosition(
        currentPositionMs: Long,
        requestedStartPositionMs: Long
    ): Boolean {
        if (requestedStartPositionMs == C.TIME_UNSET) {
            return false
        }
        val normalizedRequestedPositionMs = requestedStartPositionMs.coerceAtLeast(0L)
        if (normalizedRequestedPositionMs == 0L) {
            return false
        }
        if (currentPositionMs == C.TIME_UNSET) {
            return true
        }
        return abs(currentPositionMs - normalizedRequestedPositionMs) >
            EXPLICIT_START_POSITION_TOLERANCE_MS
    }

    private const val EXPLICIT_START_POSITION_TOLERANCE_MS = 1_000L
}
