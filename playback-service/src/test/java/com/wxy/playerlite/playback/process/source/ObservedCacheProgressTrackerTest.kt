package com.wxy.playerlite.playback.process.source

import com.wxy.playerlite.cache.core.session.CacheProgressChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ObservedCacheProgressTrackerTest {
    @Test
    fun playbackReadsShouldNotAdvanceDisplayProgressBeyondHeadCoverage() {
        val tracker = ObservedCacheProgressTracker()

        tracker.onCacheProgressChunk(CacheProgressChunk(offset = 0L, length = 8))
        assertEquals(0.08f, requireNotNull(tracker.snapshot(totalBytesHint = 100L)).displayRatio, 0.0001f)

        tracker.onCacheProgressChunk(CacheProgressChunk(offset = 96L, length = 4))
        assertEquals(0.08f, requireNotNull(tracker.snapshot(totalBytesHint = 100L)).displayRatio, 0.0001f)

        tracker.onCacheProgressChunk(CacheProgressChunk(offset = 40L, length = 8))
        tracker.onCacheProgressChunk(CacheProgressChunk(offset = 48L, length = 8))

        val snapshot = requireNotNull(tracker.snapshot(totalBytesHint = 100L))
        assertEquals(28L, snapshot.cachedBytes)
        assertEquals(0.08f, snapshot.displayRatio, 0.0001f)
    }

    @Test
    fun sparseRangesWithoutPlaybackLikeAnchorShouldNotJumpToTailProbe() {
        val tracker = ObservedCacheProgressTracker()

        tracker.onCacheProgressChunk(CacheProgressChunk(offset = 0L, length = 8))
        tracker.onCacheProgressChunk(CacheProgressChunk(offset = 96L, length = 4))

        val snapshot = tracker.snapshot(totalBytesHint = 100L)
        assertNotNull(snapshot)
        assertEquals(0.08f, snapshot?.displayRatio ?: 0f, 0.0001f)
    }

    @Test
    fun seekAcceptedShouldReanchorDisplayProgressImmediately() {
        val tracker = ObservedCacheProgressTracker()

        tracker.onCacheProgressChunk(CacheProgressChunk(offset = 0L, length = 8))
        tracker.onCacheProgressChunk(CacheProgressChunk(offset = 40L, length = 8))
        tracker.onCacheProgressChunk(CacheProgressChunk(offset = 48L, length = 8))

        assertEquals(0.08f, requireNotNull(tracker.snapshot(totalBytesHint = 100L)).displayRatio, 0.0001f)

        tracker.onPlaybackSeekAccepted(offset = 40L)

        val snapshot = requireNotNull(tracker.snapshot(totalBytesHint = 100L))
        assertEquals(0.4f, snapshot.displayStartRatio, 0.0001f)
        assertEquals(0.56f, snapshot.displayRatio, 0.0001f)
    }
}
