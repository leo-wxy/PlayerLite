package com.wxy.playerlite.playback.client

import androidx.media3.common.C
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueSyncPlanResolverTest {
    @Test
    fun sameQueueAndCurrentMedia_shouldSkipQueueReplacement() {
        assertFalse(
            QueueSyncPlanResolver.shouldReplaceQueue(
                currentMediaIds = listOf("track-1", "track-2"),
                currentIndex = 0,
                currentMediaId = "track-1",
                currentPositionMs = C.TIME_UNSET,
                requestedMediaIds = listOf("track-1", "track-2"),
                requestedIndex = 0,
                requestedStartPositionMs = C.TIME_UNSET
            )
        )
    }

    @Test
    fun sameQueueAndSameIndexWithoutCurrentMedia_shouldSkipQueueReplacement() {
        assertFalse(
            QueueSyncPlanResolver.shouldReplaceQueue(
                currentMediaIds = listOf("track-1", "track-2"),
                currentIndex = 1,
                currentMediaId = null,
                currentPositionMs = C.TIME_UNSET,
                requestedMediaIds = listOf("track-1", "track-2"),
                requestedIndex = 1,
                requestedStartPositionMs = C.TIME_UNSET
            )
        )
    }

    @Test
    fun differentCurrentMedia_shouldRequireQueueReplacement() {
        assertTrue(
            QueueSyncPlanResolver.shouldReplaceQueue(
                currentMediaIds = listOf("track-1", "track-2"),
                currentIndex = 0,
                currentMediaId = "track-2",
                currentPositionMs = C.TIME_UNSET,
                requestedMediaIds = listOf("track-1", "track-2"),
                requestedIndex = 0,
                requestedStartPositionMs = C.TIME_UNSET
            )
        )
    }

    @Test
    fun differentQueueOrder_shouldRequireQueueReplacement() {
        assertTrue(
            QueueSyncPlanResolver.shouldReplaceQueue(
                currentMediaIds = listOf("track-1", "track-2"),
                currentIndex = 0,
                currentMediaId = "track-1",
                currentPositionMs = C.TIME_UNSET,
                requestedMediaIds = listOf("track-2", "track-1"),
                requestedIndex = 0,
                requestedStartPositionMs = C.TIME_UNSET
            )
        )
    }

    @Test
    fun sameQueueAndCurrentMedia_withRequestedRestorePositionMismatch_shouldRequireQueueReplacement() {
        assertTrue(
            QueueSyncPlanResolver.shouldReplaceQueue(
                currentMediaIds = listOf("track-1", "track-2"),
                currentIndex = 0,
                currentMediaId = "track-1",
                currentPositionMs = 0L,
                requestedMediaIds = listOf("track-1", "track-2"),
                requestedIndex = 0,
                requestedStartPositionMs = 48_000L
            )
        )
    }

    @Test
    fun sameQueueAndCurrentMedia_withAlignedRestorePosition_shouldSkipQueueReplacement() {
        assertFalse(
            QueueSyncPlanResolver.shouldReplaceQueue(
                currentMediaIds = listOf("track-1", "track-2"),
                currentIndex = 0,
                currentMediaId = "track-1",
                currentPositionMs = 48_000L,
                requestedMediaIds = listOf("track-1", "track-2"),
                requestedIndex = 0,
                requestedStartPositionMs = 48_000L
            )
        )
    }
}
