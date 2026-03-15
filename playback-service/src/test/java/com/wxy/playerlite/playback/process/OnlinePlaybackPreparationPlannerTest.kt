package com.wxy.playerlite.playback.process

import com.wxy.playerlite.cache.core.CacheCompletedRange
import com.wxy.playerlite.cache.core.CacheLookupSnapshot
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.playback.model.PlaybackPreviewClip
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePlaybackPreparationPlannerTest {
    @Test
    fun buildPlanUsesCompleteCacheBeforeResolvingUrl() {
        runBlocking {
            val root = Files.createTempDirectory("online-cache-plan-test-").toFile()
            val snapshot = completeSnapshot(root, "song_1969519579_exhigh_full").also {
                writeTrustedClipMode(it.extraFilePath, OnlineClipMode.FULL)
            }
        val resolver = FakeResolver(
            Result.success(
                ResolvedOnlineStream(
                    playbackUrl = "https://example.com/should-not-be-used.mp3",
                    requestHeaders = emptyMap(),
                    contentLengthBytes = 10L,
                    durationMs = 1_000L,
                    expiresAtMs = 5_000L
                )
            )
        )
        val planner = OnlinePlaybackPreparationPlanner(
            cacheLookup = { Result.success(snapshot.takeIf { key -> key.resourceKey == it }) },
            resolver = resolver
        )

        val plan = planner.buildPlan(
            PlaybackTrack(
                playable = MusicInfo(
                    id = "queue-online-1",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 219_893L,
                    playbackUri = ""
                )
            )
        ).getOrThrow()

            assertTrue(plan.useCacheOnlyProvider)
            assertEquals("song_1969519579_exhigh_full", plan.resourceKey)
            assertEquals(219_893L, plan.durationHintMs)
            assertEquals(8_798_445L, plan.contentLengthHintBytes)
            assertEquals(0, resolver.calls)
            root.deleteRecursively()
        }
    }

    @Test
    fun buildPlanShouldReuseCompleteFullCacheWithoutTrustedMarkerWhenDurationLooksComplete() {
        runBlocking {
            val root = Files.createTempDirectory("online-cache-plan-untrusted-").toFile()
            val snapshot = completeSnapshot(root, "song_1969519579_exhigh_full")
            val resolver = FakeResolver(
                Result.success(
                    ResolvedOnlineStream(
                        playbackUrl = "https://example.com/refreshed-full.mp3",
                        requestHeaders = emptyMap(),
                        contentLengthBytes = 8_798_445L,
                        durationMs = 219_893L,
                        expiresAtMs = 5_000L
                    )
                )
            )
            val planner = OnlinePlaybackPreparationPlanner(
                cacheLookup = { Result.success(snapshot.takeIf { key -> key.resourceKey == it }) },
                resolver = resolver
            )

            val plan = planner.buildPlan(
                PlaybackTrack(
                    playable = MusicInfo(
                        id = "queue-online-untrusted",
                        songId = "1969519579",
                        title = "夜曲",
                        durationMs = 219_893L,
                        playbackUri = ""
                    )
                )
            ).getOrThrow()

            assertTrue(plan.useCacheOnlyProvider)
            assertEquals("song_1969519579_exhigh_full", plan.resourceKey)
            assertEquals(219_893L, plan.durationHintMs)
            assertEquals(8_798_445L, plan.contentLengthHintBytes)
            assertEquals(0, resolver.calls)
            root.deleteRecursively()
        }
    }

    @Test
    fun buildPlanShouldIgnoreCompleteFullCacheWithoutTrustedMarkerWhenDurationLooksRestricted() {
        runBlocking {
            val root = Files.createTempDirectory("online-cache-plan-untrusted-short-").toFile()
            val snapshot = completeSnapshot(root, "song_1477319527_exhigh_full").copy(
                durationMs = 45_024L
            )
            val resolver = FakeResolver(
                Result.success(
                    ResolvedOnlineStream(
                        playbackUrl = "https://example.com/refreshed-full.mp3",
                        requestHeaders = emptyMap(),
                        contentLengthBytes = 8_798_445L,
                        durationMs = 253_080L,
                        expiresAtMs = 5_000L
                    )
                )
            )
            val planner = OnlinePlaybackPreparationPlanner(
                cacheLookup = { Result.success(snapshot.takeIf { key -> key.resourceKey == it }) },
                resolver = resolver
            )

            val plan = planner.buildPlan(
                PlaybackTrack(
                    playable = MusicInfo(
                        id = "queue-online-untrusted-short",
                        songId = "1477319527",
                        title = "讽刺的情书",
                        durationMs = 253_080L,
                        playbackUri = ""
                    )
                )
            ).getOrThrow()

            assertTrue(!plan.useCacheOnlyProvider)
            assertEquals("https://example.com/refreshed-full.mp3", plan.playbackUrl)
            assertEquals(1, resolver.calls)
            root.deleteRecursively()
        }
    }

    @Test
    fun buildPlanUsesResolvedPreviewClipAndDurationWhenCacheIncomplete() = runBlocking {
        val resolver = FakeResolver(
            Result.success(
                ResolvedOnlineStream(
                    playbackUrl = "https://example.com/preview.mp3",
                    requestHeaders = mapOf("Cookie" to "MUSIC_U=demo; os=pc"),
                    contentLengthBytes = 2_048L,
                    durationMs = 90_000L,
                    expiresAtMs = 5_000L,
                    previewClip = PlaybackPreviewClip(15_000L, 90_000L)
                )
            )
        )
        val planner = OnlinePlaybackPreparationPlanner(
            cacheLookup = { Result.success(null) },
            resolver = resolver
        )

        val plan = planner.buildPlan(
            PlaybackTrack(
                playable = MusicInfo(
                    id = "queue-online-2",
                    songId = "1969519580",
                    title = "试听歌曲",
                    durationMs = 0L,
                    playbackUri = ""
                )
            )
        ).getOrThrow()

        assertEquals("song_1969519580_exhigh_preview", plan.resourceKey)
        assertEquals("https://example.com/preview.mp3", plan.playbackUrl)
        assertEquals(90_000L, plan.durationHintMs)
        assertEquals(2_048L, plan.contentLengthHintBytes)
        assertEquals(1, resolver.calls)
    }

    @Test
    fun buildPlanShouldIgnoreSparseCacheEvenWhenDataFileSizeMatchesContentLength() = runBlocking {
        val contentLength = 57_029_114L
        val sparseSnapshot = CacheLookupSnapshot(
            resourceKey = "song_1481929839_exhigh_full",
            dataFilePath = "/tmp/song_1481929839_exhigh_full.data",
            configFilePath = "/tmp/song_1481929839_exhigh_full_config.json",
            extraFilePath = "/tmp/song_1481929839_exhigh_full_extra.json",
            dataFileSizeBytes = contentLength,
            blockSizeBytes = 65_536,
            contentLength = contentLength,
            durationMs = 288_733L,
            cachedBlocks = buildSet {
                addAll(0L..31L)
                addAll(109L..156L)
                add(870L)
            },
            lastAccessEpochMs = 1_000L,
            completedRanges = listOf(
                CacheCompletedRange(start = 0L, endExclusive = 2_097_152L),
                CacheCompletedRange(start = 7_175_852L, endExclusive = 10_238_663L),
                CacheCompletedRange(start = 57_028_089L, endExclusive = contentLength)
            )
        )
        val resolver = FakeResolver(
            Result.success(
                ResolvedOnlineStream(
                    playbackUrl = "https://example.com/fresh-full.mp3",
                    requestHeaders = emptyMap(),
                    contentLengthBytes = contentLength,
                    durationMs = 288_733L,
                    expiresAtMs = 5_000L
                )
            )
        )
        val planner = OnlinePlaybackPreparationPlanner(
            cacheLookup = { Result.success(sparseSnapshot.takeIf { snapshot -> snapshot.resourceKey == it }) },
            resolver = resolver
        )

        val plan = planner.buildPlan(
            PlaybackTrack(
                playable = MusicInfo(
                    id = "queue-online-sparse",
                    songId = "1481929839",
                    title = "倒数",
                    durationMs = 288_733L,
                    playbackUri = ""
                )
            )
        ).getOrThrow()

        assertTrue(!plan.useCacheOnlyProvider)
        assertEquals("https://example.com/fresh-full.mp3", plan.playbackUrl)
        assertEquals(1, resolver.calls)
    }

    private class FakeResolver(
        private val result: Result<ResolvedOnlineStream>
    ) : OnlinePlaybackResolver {
        var calls: Int = 0

        override suspend fun resolve(
            songId: String,
            requestHeaders: Map<String, String>,
            requestedLevel: String,
            preferredClipMode: OnlineClipMode,
            expectedDurationMs: Long
        ): Result<ResolvedOnlineStream> {
            calls += 1
            return result
        }
    }

    private fun completeSnapshot(root: File, resourceKey: String): CacheLookupSnapshot {
        val dataFile = File(root, "$resourceKey.data").apply {
            writeBytes(ByteArray(8_798_445))
        }
        val configFile = File(root, "${resourceKey}_config.json").apply {
            writeText("{}")
        }
        val extraFile = File(root, "${resourceKey}_extra.json").apply {
            writeText("{\n}\n")
        }
        return CacheLookupSnapshot(
            resourceKey = resourceKey,
            dataFilePath = dataFile.absolutePath,
            configFilePath = configFile.absolutePath,
            extraFilePath = extraFile.absolutePath,
            dataFileSizeBytes = 8_798_445L,
            blockSizeBytes = 8_798_445,
            contentLength = 8_798_445L,
            durationMs = 219_893L,
            cachedBlocks = setOf(0L),
            lastAccessEpochMs = 1_000L,
            completedRanges = listOf(
                CacheCompletedRange(start = 0L, endExclusive = 8_798_445L)
            )
        )
    }

    private fun writeTrustedClipMode(
        extraFilePath: String,
        clipMode: OnlineClipMode
    ) {
        File(extraFilePath).writeText(
            """
            {
              "${OnlineCacheMetadata.CLIP_MODE_KEY}": "${clipMode.wireValue}"
            }
            """.trimIndent()
        )
    }
}
