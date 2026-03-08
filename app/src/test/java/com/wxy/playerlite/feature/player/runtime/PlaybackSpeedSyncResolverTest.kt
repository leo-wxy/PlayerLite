package com.wxy.playerlite.feature.player.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSpeedSyncResolverTest {
    @Test
    fun onLocalRequest_keepsRequestedSpeedPendingUntilRemoteConfirms() {
        val result = PlaybackSpeedSyncResolver.onLocalRequest(requestedSpeed = 1.96f)

        assertEquals(2.0f, result.resolvedSpeed, 0f)
        assertEquals(2.0f, result.pendingSpeed ?: 0f, 0f)
    }

    @Test
    fun onRemoteUpdate_keepsPendingSpeedWhileRemoteSnapshotIsStillStale() {
        val result = PlaybackSpeedSyncResolver.onRemoteUpdate(
            remoteSpeed = 1.0f,
            pendingSpeed = 2.0f
        )

        assertEquals(2.0f, result.resolvedSpeed, 0f)
        assertEquals(2.0f, result.pendingSpeed ?: 0f, 0f)
    }

    @Test
    fun onRemoteUpdate_clearsPendingSpeedWhenRemoteMatches() {
        val result = PlaybackSpeedSyncResolver.onRemoteUpdate(
            remoteSpeed = 2.0f,
            pendingSpeed = 2.0f
        )

        assertEquals(2.0f, result.resolvedSpeed, 0f)
        assertNull(result.pendingSpeed)
    }

    @Test
    fun onCommandRejected_revertsToFallbackAndClearsPending() {
        val result = PlaybackSpeedSyncResolver.onCommandRejected(fallbackSpeed = 1.0f)

        assertEquals(1.0f, result.resolvedSpeed, 0f)
        assertNull(result.pendingSpeed)
    }
}
