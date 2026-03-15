package com.wxy.playerlite.playback.client

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
                requestedMediaIds = listOf("track-1", "track-2"),
                requestedIndex = 0
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
                requestedMediaIds = listOf("track-1", "track-2"),
                requestedIndex = 1
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
                requestedMediaIds = listOf("track-1", "track-2"),
                requestedIndex = 0
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
                requestedMediaIds = listOf("track-2", "track-1"),
                requestedIndex = 0
            )
        )
    }
}
