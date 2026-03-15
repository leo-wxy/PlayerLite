package com.wxy.playerlite.playback.process

import com.wxy.playerlite.playback.model.PlaybackPreviewClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolvedOnlineUrlMemoryCacheTest {
    @Test
    fun putEvictsLeastRecentlyUsedEntryWhenCapacityExceeded() {
        val cache = ResolvedOnlineUrlMemoryCache(maxEntries = 2)
        cache.put(
            OnlinePlaybackCacheKey(songId = "1", level = "exhigh", clipMode = OnlineClipMode.FULL),
            resolvedStream(songId = "1")
        )
        cache.put(
            OnlinePlaybackCacheKey(songId = "2", level = "exhigh", clipMode = OnlineClipMode.FULL),
            resolvedStream(songId = "2")
        )

        cache.getIfFresh(
            OnlinePlaybackCacheKey(songId = "1", level = "exhigh", clipMode = OnlineClipMode.FULL),
            nowMs = 1_000L
        )
        cache.put(
            OnlinePlaybackCacheKey(songId = "3", level = "exhigh", clipMode = OnlineClipMode.FULL),
            resolvedStream(songId = "3")
        )

        assertEquals(
            "1",
            cache.getIfFresh(
                OnlinePlaybackCacheKey(songId = "1", level = "exhigh", clipMode = OnlineClipMode.FULL),
                nowMs = 1_000L
            )?.cacheIdentity
        )
        assertNull(
            cache.getIfFresh(
                OnlinePlaybackCacheKey(songId = "2", level = "exhigh", clipMode = OnlineClipMode.FULL),
                nowMs = 1_000L
            )
        )
    }

    @Test
    fun getIfFreshDropsExpiredEntries() {
        val cache = ResolvedOnlineUrlMemoryCache(maxEntries = 2)
        val key = OnlinePlaybackCacheKey(songId = "1", level = "exhigh", clipMode = OnlineClipMode.FULL)
        cache.put(
            key,
            resolvedStream(songId = "1", expiresAtMs = 1_500L)
        )

        assertEquals("1", cache.getIfFresh(key, nowMs = 1_400L)?.cacheIdentity)
        assertNull(cache.getIfFresh(key, nowMs = 1_500L))
    }

    @Test
    fun putPreviewEntry_shouldNotOccupyCapacityOrEvictFullEntry() {
        val cache = ResolvedOnlineUrlMemoryCache(maxEntries = 1)
        val fullKey = OnlinePlaybackCacheKey(songId = "1", level = "exhigh", clipMode = OnlineClipMode.FULL)
        val previewKey = OnlinePlaybackCacheKey(songId = "2", level = "exhigh", clipMode = OnlineClipMode.PREVIEW)

        cache.put(fullKey, resolvedStream(songId = "1"))
        cache.put(previewKey, resolvedStream(songId = "2", preview = true))

        assertEquals("1", cache.getIfFresh(fullKey, nowMs = 1_000L)?.cacheIdentity)
        assertNull(cache.getIfFresh(previewKey, nowMs = 1_000L))
    }

    private fun resolvedStream(
        songId: String,
        expiresAtMs: Long = 5_000L,
        preview: Boolean = false
    ): ResolvedOnlineStream {
        return ResolvedOnlineStream(
            playbackUrl = "https://example.com/$songId.mp3",
            requestHeaders = emptyMap(),
            contentLengthBytes = 1024L,
            durationMs = 12_345L,
            expiresAtMs = expiresAtMs,
            previewClip = if (preview) PlaybackPreviewClip(10L, 20L) else null,
            cacheIdentity = songId
        )
    }
}
