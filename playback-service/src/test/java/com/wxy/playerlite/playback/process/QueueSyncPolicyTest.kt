package com.wxy.playerlite.playback.process

import androidx.media3.common.C
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueSyncPolicyTest {
    @Test
    fun shouldRestorePosition_whenCurrentItemChanges() {
        assertTrue(
            QueueSyncPolicy.shouldRestorePosition(
                previousMediaId = "a",
                nextMediaId = "b",
                requestedStartPositionMs = 12_345L
            )
        )
    }

    @Test
    fun shouldNotRestorePosition_whenCurrentItemUnchanged() {
        assertFalse(
            QueueSyncPolicy.shouldRestorePosition(
                previousMediaId = "a",
                nextMediaId = "a",
                requestedStartPositionMs = 12_345L
            )
        )
    }

    @Test
    fun shouldNotRestorePosition_whenPositionUnset() {
        assertFalse(
            QueueSyncPolicy.shouldRestorePosition(
                previousMediaId = "a",
                nextMediaId = "b",
                requestedStartPositionMs = C.TIME_UNSET
            )
        )
    }
}
