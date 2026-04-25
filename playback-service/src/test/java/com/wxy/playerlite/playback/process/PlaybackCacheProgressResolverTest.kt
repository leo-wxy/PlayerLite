package com.wxy.playerlite.playback.process

import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.CacheCompletedRange
import com.wxy.playerlite.cache.core.CacheLookupSnapshot
import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

class PlaybackCacheProgressResolverTest {
    @After
    fun tearDown() {
        CacheCore.shutdown()
    }

    @Test
    fun resolve_whenTotalBytesUnknownAndPlaybackAdvanced_shouldUseStableUnknownTotalIndicator() {
        val snapshot = CacheLookupSnapshot(
            resourceKey = "track-1",
            dataFilePath = "/tmp/track-1.data",
            configFilePath = "/tmp/track-1.json",
            extraFilePath = "/tmp/track-1.extra",
            dataFileSizeBytes = 512_000L,
            blockSizeBytes = 64 * 1024,
            contentLength = 0L,
            durationMs = 180_000L,
            cachedBlocks = emptySet(),
            lastAccessEpochMs = 0L,
            completedRanges = listOf(
                CacheCompletedRange(start = 0L, endExclusive = 512_000L)
            )
        )

        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = snapshot,
            totalBytesHint = null,
            playbackPositionMs = 45_000L,
            durationMs = 180_000L
        )

        requireNotNull(resolved)
        assertTrue(resolved.isEstimated)
        assertEquals(0.04f, resolved.displayRatio, 0.0001f)
    }

    @Test
    fun resolve_whenPlaybackIsAheadOfKnownCachedCoverage_shouldNotRaiseCacheRatioToPlaybackRatio() {
        val snapshot = CacheLookupSnapshot(
            resourceKey = "track-known-total-playback-ahead",
            dataFilePath = "/tmp/track-known-total-playback-ahead.data",
            configFilePath = "/tmp/track-known-total-playback-ahead.json",
            extraFilePath = "/tmp/track-known-total-playback-ahead.extra",
            dataFileSizeBytes = 300L,
            blockSizeBytes = 100,
            contentLength = 1_000L,
            durationMs = 200_000L,
            cachedBlocks = linkedSetOf(0L, 1L, 2L),
            lastAccessEpochMs = 0L,
            completedRanges = emptyList()
        )

        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = snapshot,
            totalBytesHint = 1_000L,
            playbackPositionMs = 100_000L,
            durationMs = 200_000L
        )

        requireNotNull(resolved)
        assertEquals(300L, resolved.cachedBytes)
        assertEquals(1_000L, resolved.totalBytes)
        assertEquals(0.3f, resolved.displayRatio, 0.0001f)
        assertEquals(false, resolved.isFullyCached)
    }

    @Test
    fun resolve_whenFullyCached_shouldClampDisplayRatioToFull() {
        val snapshot = CacheLookupSnapshot(
            resourceKey = "track-2",
            dataFilePath = "/tmp/track-2.data",
            configFilePath = "/tmp/track-2.json",
            extraFilePath = "/tmp/track-2.extra",
            dataFileSizeBytes = 1_024_000L,
            blockSizeBytes = 64 * 1024,
            contentLength = 1_024_000L,
            durationMs = 180_000L,
            cachedBlocks = emptySet(),
            lastAccessEpochMs = 0L,
            completedRanges = listOf(
                CacheCompletedRange(start = 0L, endExclusive = 1_024_000L)
            )
        )

        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = snapshot,
            totalBytesHint = null,
            playbackPositionMs = 60_000L,
            durationMs = 180_000L
        )

        requireNotNull(resolved)
        assertTrue(resolved.isFullyCached)
        assertEquals(1f, resolved.displayRatio, 0f)
    }

    @Test
    fun resolve_whenSnapshotIsFullyCachedFromStart_shouldReturnFullProgressImmediately() {
        val snapshot = CacheLookupSnapshot(
            resourceKey = "track-initial",
            dataFilePath = "/tmp/track-initial.data",
            configFilePath = "/tmp/track-initial.json",
            extraFilePath = "/tmp/track-initial.extra",
            dataFileSizeBytes = 1_024_000L,
            blockSizeBytes = 64 * 1024,
            contentLength = 1_024_000L,
            durationMs = 180_000L,
            cachedBlocks = emptySet(),
            lastAccessEpochMs = 0L,
            completedRanges = listOf(CacheCompletedRange(start = 0L, endExclusive = 1_024_000L))
        )

        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = snapshot,
            totalBytesHint = 1_024_000L,
            playbackPositionMs = 0L,
            durationMs = 180_000L
        )

        requireNotNull(resolved)
        assertTrue(resolved.isFullyCached)
        assertEquals(1f, resolved.displayRatio, 0f)
    }

    @Test
    fun stabilize_whenResolvedTemporarilyMissing_shouldKeepPreviousNonNullProgress() {
        val previous = PlaybackCacheProgressSnapshot(
            cachedBytes = 512_000L,
            totalBytes = 1_024_000L,
            displayRatio = 0.5f,
            isFullyCached = false,
            isEstimated = false
        )

        val stabilized = stabilizePlaybackCacheProgressSnapshot(
            previous = previous,
            resolved = null,
            resourceKey = "track-stable"
        )

        assertSame(previous, stabilized)
    }

    @Test
    fun resolve_whenHintExistsButNoSnapshotAndNoCachedBytes_shouldReturnNull() {
        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = null,
            totalBytesHint = 720_813L,
            playbackPositionMs = 3_400L,
            durationMs = 301_066L
        )

        assertNull(resolved)
    }

    @Test
    fun resolve_whenOnlyFileSizeSuggestsPartialCache_shouldReturnNull() {
        val snapshot = CacheLookupSnapshot(
            resourceKey = "track-file-size-partial",
            dataFilePath = "/tmp/track-file-size-partial.data",
            configFilePath = "/tmp/track-file-size-partial.json",
            extraFilePath = "/tmp/track-file-size-partial.extra",
            dataFileSizeBytes = 128_000L,
            blockSizeBytes = 64 * 1024,
            contentLength = 720_813L,
            durationMs = 301_066L,
            cachedBlocks = emptySet(),
            lastAccessEpochMs = 0L,
            completedRanges = emptyList()
        )

        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = snapshot,
            totalBytesHint = 720_813L,
            playbackPositionMs = 3_400L,
            durationMs = 301_066L
        )

        assertNull(resolved)
    }

    @Test
    fun resolve_whenOnlyFileSizeExceedsHintWithoutRanges_shouldNotReportFullCache() {
        val snapshot = CacheLookupSnapshot(
            resourceKey = "track-file-only",
            dataFilePath = "/tmp/track-file-only.data",
            configFilePath = "/tmp/track-file-only.json",
            extraFilePath = "/tmp/track-file-only.extra",
            dataFileSizeBytes = 65_204_060L,
            blockSizeBytes = 64 * 1024,
            contentLength = -1L,
            durationMs = 301_066L,
            cachedBlocks = emptySet(),
            lastAccessEpochMs = 0L,
            completedRanges = emptyList()
        )

        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = snapshot,
            totalBytesHint = 720_813L,
            playbackPositionMs = 3_400L,
            durationMs = 301_066L
        )

        assertNull(resolved)
    }

    @Test
    fun resolve_whenContiguousCachedBlocksExistWithoutRanges_shouldReportPartialCache() {
        val snapshot = CacheLookupSnapshot(
            resourceKey = "track-blocks-partial",
            dataFilePath = "/tmp/track-blocks-partial.data",
            configFilePath = "/tmp/track-blocks-partial.json",
            extraFilePath = "/tmp/track-blocks-partial.extra",
            dataFileSizeBytes = 340_290L,
            blockSizeBytes = 100,
            contentLength = 1_000L,
            durationMs = 200_000L,
            cachedBlocks = linkedSetOf(0L, 1L, 2L, 7L),
            lastAccessEpochMs = 0L,
            completedRanges = emptyList()
        )

        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = snapshot,
            totalBytesHint = 1_000L,
            playbackPositionMs = 20_000L,
            durationMs = 200_000L
        )

        requireNotNull(resolved)
        assertEquals(300L, resolved.cachedBytes)
        assertEquals(1_000L, resolved.totalBytes)
        assertEquals(0.3f, resolved.displayRatio, 0.0001f)
        assertEquals(false, resolved.isFullyCached)
    }

    @Test
    fun resolve_whenCachedBlocksCoverWholeTrackWithoutRanges_shouldReportFullCache() {
        val snapshot = CacheLookupSnapshot(
            resourceKey = "track-blocks-full",
            dataFilePath = "/tmp/track-blocks-full.data",
            configFilePath = "/tmp/track-blocks-full.json",
            extraFilePath = "/tmp/track-blocks-full.extra",
            dataFileSizeBytes = 1_375_792L,
            blockSizeBytes = 100,
            contentLength = 950L,
            durationMs = 200_000L,
            cachedBlocks = (0L..9L).toSet(),
            lastAccessEpochMs = 0L,
            completedRanges = emptyList()
        )

        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = snapshot,
            totalBytesHint = 950L,
            playbackPositionMs = 20_000L,
            durationMs = 200_000L
        )

        requireNotNull(resolved)
        assertEquals(950L, resolved.cachedBytes)
        assertEquals(950L, resolved.totalBytes)
        assertEquals(1f, resolved.displayRatio, 0f)
        assertTrue(resolved.isFullyCached)
    }

    @Test
    fun resolve_whenCachedBlocksDoNotStartFromZero_shouldReturnNull() {
        val snapshot = CacheLookupSnapshot(
            resourceKey = "track-blocks-gap",
            dataFilePath = "/tmp/track-blocks-gap.data",
            configFilePath = "/tmp/track-blocks-gap.json",
            extraFilePath = "/tmp/track-blocks-gap.extra",
            dataFileSizeBytes = 512_000L,
            blockSizeBytes = 100,
            contentLength = 1_000L,
            durationMs = 200_000L,
            cachedBlocks = linkedSetOf(2L, 3L, 4L),
            lastAccessEpochMs = 0L,
            completedRanges = emptyList()
        )

        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = snapshot,
            totalBytesHint = 1_000L,
            playbackPositionMs = 20_000L,
            durationMs = 200_000L
        )

        assertNull(resolved)
    }

    @Test
    fun resolve_whenCachedRangeCoversPlaybackPositionWithoutStartingFromZero_shouldReportBufferedEnd() {
        val snapshot = CacheLookupSnapshot(
            resourceKey = "track-range-resume",
            dataFilePath = "/tmp/track-range-resume.data",
            configFilePath = "/tmp/track-range-resume.json",
            extraFilePath = "/tmp/track-range-resume.extra",
            dataFileSizeBytes = 512_000L,
            blockSizeBytes = 100,
            contentLength = 1_000L,
            durationMs = 200_000L,
            cachedBlocks = emptySet(),
            lastAccessEpochMs = 0L,
            completedRanges = listOf(CacheCompletedRange(start = 200L, endExclusive = 500L))
        )

        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = snapshot,
            totalBytesHint = 1_000L,
            playbackPositionMs = 50_000L,
            durationMs = 200_000L
        )

        requireNotNull(resolved)
        assertEquals(300L, resolved.cachedBytes)
        assertEquals(1_000L, resolved.totalBytes)
        assertEquals(0.5f, resolved.displayRatio, 0.0001f)
        assertEquals(false, resolved.isFullyCached)
    }

    @Test
    fun resolve_whenCachedBlocksCoverPlaybackPositionWithoutStartingFromZero_shouldReportBufferedEnd() {
        val snapshot = CacheLookupSnapshot(
            resourceKey = "track-blocks-resume",
            dataFilePath = "/tmp/track-blocks-resume.data",
            configFilePath = "/tmp/track-blocks-resume.json",
            extraFilePath = "/tmp/track-blocks-resume.extra",
            dataFileSizeBytes = 512_000L,
            blockSizeBytes = 100,
            contentLength = 1_000L,
            durationMs = 200_000L,
            cachedBlocks = linkedSetOf(2L, 3L, 4L),
            lastAccessEpochMs = 0L,
            completedRanges = emptyList()
        )

        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = snapshot,
            totalBytesHint = 1_000L,
            playbackPositionMs = 50_000L,
            durationMs = 200_000L
        )

        requireNotNull(resolved)
        assertEquals(300L, resolved.cachedBytes)
        assertEquals(1_000L, resolved.totalBytes)
        assertEquals(0.5f, resolved.displayRatio, 0.0001f)
        assertEquals(false, resolved.isFullyCached)
    }

    @Test
    fun resolve_whenLookupMetadataEmptyButConfigSidecarExists_shouldUseConfigMetadata() {
        val configFile = Files.createTempFile("cache-progress-sidecar", ".json").toFile()
        configFile.writeText(
            """
            {
              "version": 1,
              "resourceKey": "track-sidecar",
              "contentLength": 1000,
              "durationMs": 200000,
              "blockSizeBytes": 100,
              "blocks": [0, 1, 2],
              "completedRanges": [{"start":0,"end":300}],
              "lastAccessEpochMs": 1
            }
            """.trimIndent()
        )
        val snapshot = CacheLookupSnapshot(
            resourceKey = "track-sidecar",
            dataFilePath = "/tmp/track-sidecar.data",
            configFilePath = configFile.absolutePath,
            extraFilePath = "/tmp/track-sidecar.extra",
            dataFileSizeBytes = 8_192L,
            blockSizeBytes = 64 * 1024,
            contentLength = -1L,
            durationMs = -1L,
            cachedBlocks = emptySet(),
            lastAccessEpochMs = 0L,
            completedRanges = emptyList()
        )

        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = snapshot,
            totalBytesHint = 1_000L,
            playbackPositionMs = 20_000L,
            durationMs = 200_000L
        )

        requireNotNull(resolved)
        assertEquals(300L, resolved.cachedBytes)
        assertEquals(1_000L, resolved.totalBytes)
        assertEquals(0.3f, resolved.displayRatio, 0.0001f)
        assertEquals(false, resolved.isFullyCached)
    }

    @Test
    fun resolve_whenLookupMetadataEmptyButConfigSidecarIsComplete_shouldReportFullCache() {
        val configFile = Files.createTempFile("cache-progress-sidecar-full", ".json").toFile()
        configFile.writeText(
            """
            {
              "version": 1,
              "resourceKey": "track-sidecar-full",
              "contentLength": 30147193,
              "durationMs": 141810,
              "blockSizeBytes": 65536,
              "blocks": [],
              "completedRanges": [{"start":0,"end":30147193}],
              "lastAccessEpochMs": 1
            }
            """.trimIndent()
        )
        val snapshot = CacheLookupSnapshot(
            resourceKey = "track-sidecar-full",
            dataFilePath = "/tmp/track-sidecar-full.data",
            configFilePath = configFile.absolutePath,
            extraFilePath = "/tmp/track-sidecar-full.extra",
            dataFileSizeBytes = 30_147_193L,
            blockSizeBytes = 64 * 1024,
            contentLength = -1L,
            durationMs = -1L,
            cachedBlocks = emptySet(),
            lastAccessEpochMs = 0L,
            completedRanges = emptyList()
        )

        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = snapshot,
            totalBytesHint = 5_674_605L,
            playbackPositionMs = 76_350L,
            durationMs = 141_810L
        )

        requireNotNull(resolved)
        assertEquals(30_147_193L, resolved.cachedBytes)
        assertEquals(30_147_193L, resolved.totalBytes)
        assertEquals(1f, resolved.displayRatio, 0f)
        assertTrue(resolved.isFullyCached)
    }

    @Test
    fun resolve_whenLookupIsNullButCacheRootSidecarIsComplete_shouldReportFullCache() {
        val root = Files.createTempDirectory("cache-progress-root-sidecar").toFile()
        val resourceKey = "song_1859245776_exhigh_full"
        val contentLength = 30_147_193L
        RandomAccessFile(File(root, "$resourceKey.data"), "rw").use { file ->
            file.setLength(contentLength)
        }
        File(root, "${resourceKey}_config.json").writeText(
            """
            {
              "version": 1,
              "resourceKey": "$resourceKey",
              "contentLength": $contentLength,
              "durationMs": 141810,
              "blockSizeBytes": 65536,
              "blocks": [],
              "completedRanges": [{"start":0,"end":$contentLength}],
              "lastAccessEpochMs": 1
            }
            """.trimIndent()
        )
        File(root, "${resourceKey}_extra.json").writeText("{\n}\n")

        val resolved = resolvePlaybackCacheProgressSnapshot(
            snapshot = null,
            totalBytesHint = 5_674_605L,
            playbackPositionMs = 19_020L,
            durationMs = 141_810L,
            resourceKey = resourceKey,
            cacheRootDirPath = root.absolutePath
        )

        requireNotNull(resolved)
        assertEquals(contentLength, resolved.cachedBytes)
        assertEquals(contentLength, resolved.totalBytes)
        assertEquals(1f, resolved.displayRatio, 0f)
        assertTrue(resolved.isFullyCached)
        root.deleteRecursively()
    }
}
