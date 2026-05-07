package com.wxy.playerlite.playback.process

import com.wxy.playerlite.cache.core.CacheCompletedRange
import com.wxy.playerlite.cache.core.CacheLookupSnapshot
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackPreviewClip
import com.wxy.playerlite.playback.model.SongAudioQualityCatalog
import com.wxy.playerlite.playback.model.SongAudioQualityOption
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePlaybackPreparationPlannerTest {
    @Test
    fun buildPlan_shouldConsumeMusicUrlFromCurrentSourceAdapter() = runBlocking {
        val adapter = FakePlanningSourceAdapter(
            Result.success(
                SourceActionResult.MusicUrl(
                    playbackUrl = "https://example.com/from-adapter.flac",
                    requestHeaders = mapOf("Cookie" to "MUSIC_U=demo"),
                    contentLengthBytes = 21_321_000L,
                    durationMs = 219_893L,
                    expiresAtMs = 5_000L,
                    previewClip = null,
                    appliedAudioQuality = PlaybackAudioQuality.LOSSLESS
                )
            )
        )
        val planner = OnlinePlaybackPreparationPlanner(
            cacheLookup = { Result.success(null) },
            sourceAdapterProvider = { adapter }
        )

        val plan = planner.buildPlan(
            track = PlaybackTrack(
                playable = MusicInfo(
                    id = "queue-online-adapter",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 219_893L,
                    playbackUri = ""
                )
            ),
            preferredAudioQuality = PlaybackAudioQuality.HIRES
        ).getOrThrow()

        assertEquals("song_1969519579_lossless_full", plan.resourceKey)
        assertEquals("https://example.com/from-adapter.flac", plan.playbackUrl)
        assertEquals(PlaybackAudioQuality.HIRES, adapter.lastContext?.preferredAudioQuality)
        assertEquals(PlaybackAudioQuality.LOSSLESS, plan.appliedAudioQuality)
    }

    @Test
    fun buildPlan_shouldApplyNearestAvailableAudioQualityWhenPreferredIsUnavailable() = runBlocking {
        val resolver = CapturingResolver(
            Result.success(
                ResolvedOnlineStream(
                    playbackUrl = "https://example.com/lossless.flac",
                    requestHeaders = emptyMap(),
                    contentLengthBytes = 21_321_000L,
                    durationMs = 219_893L,
                    expiresAtMs = 5_000L
                )
            )
        )
        val planner = OnlinePlaybackPreparationPlanner(
            cacheLookup = { Result.success(null) },
            resolver = resolver,
            audioQualityCatalogProvider = { _, _ ->
                SongAudioQualityCatalog(
                    songId = "1969519579",
                    options = listOf(
                        SongAudioQualityOption(
                            quality = PlaybackAudioQuality.LOSSLESS,
                            rawKey = "lossless",
                            bitRate = 999_000,
                            sizeBytes = 21_321_000L,
                            sampleRate = 44_100
                        ),
                        SongAudioQualityOption(
                            quality = PlaybackAudioQuality.STANDARD,
                            rawKey = "standard",
                            bitRate = 128_000,
                            sizeBytes = 3_210_000L,
                            sampleRate = 44_100
                        )
                    )
                )
            }
        )

        val plan = planner.buildPlan(
            track = PlaybackTrack(
                playable = MusicInfo(
                    id = "queue-online-quality",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 219_893L,
                    playbackUri = ""
                )
            ),
            preferredAudioQuality = PlaybackAudioQuality.HIRES
        ).getOrThrow()

        assertEquals("song_1969519579_lossless_full", plan.resourceKey)
        assertEquals(PlaybackAudioQuality.HIRES, plan.preferredAudioQuality)
        assertEquals(PlaybackAudioQuality.LOSSLESS, plan.appliedAudioQuality)
        assertEquals("lossless", resolver.lastRequestedLevel)
        assertEquals(999_000, resolver.lastFallbackBitrate)
    }

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
    fun buildPlanUsesCompleteCacheWhenResolverFails() {
        runBlocking {
            val root = Files.createTempDirectory("online-cache-plan-resolver-fail-").toFile()
            val snapshot = completeSnapshot(root, "song_1969519579_exhigh_full").also {
                writeTrustedClipMode(it.extraFilePath, OnlineClipMode.FULL)
            }
            val resolver = FakeResolver(
                Result.failure(IOException("network unavailable"))
            )
            val planner = OnlinePlaybackPreparationPlanner(
                cacheLookup = { Result.success(snapshot.takeIf { key -> key.resourceKey == it }) },
                resolver = resolver
            )

            val plan = planner.buildPlan(
                PlaybackTrack(
                    playable = MusicInfo(
                        id = "queue-online-cache-first",
                        songId = "1969519579",
                        title = "夜曲",
                        durationMs = 219_893L,
                        playbackUri = ""
                    )
                )
            ).getOrThrow()

            assertTrue(plan.useCacheOnlyProvider)
            assertEquals("song_1969519579_exhigh_full", plan.resourceKey)
            assertEquals(0, resolver.calls)
            root.deleteRecursively()
        }
    }

    @Test
    fun buildPlanUsesCompleteSidecarWhenCacheLookupReturnsNull() {
        runBlocking {
            val root = Files.createTempDirectory("online-cache-plan-sidecar-").toFile()
            val resourceKey = "song_1859245776_exhigh_full"
            writeCompleteSidecarCache(
                root = root,
                resourceKey = resourceKey,
                contentLength = 30_147_193L,
                durationMs = 141_810L
            )
            val resolver = FakeResolver(
                Result.success(
                    ResolvedOnlineStream(
                        playbackUrl = "https://example.com/should-not-be-used.flac",
                        requestHeaders = emptyMap(),
                        contentLengthBytes = 5_674_605L,
                        durationMs = 141_810L,
                        expiresAtMs = 5_000L
                    )
                )
            )
            val planner = OnlinePlaybackPreparationPlanner(
                cacheLookup = { Result.success(null) },
                cacheRootDirPath = root.absolutePath,
                resolver = resolver
            )

            val plan = planner.buildPlan(
                PlaybackTrack(
                    playable = MusicInfo(
                        id = "queue-online-sidecar",
                        songId = "1859245776",
                        title = "STAY",
                        durationMs = 141_810L,
                        playbackUri = ""
                    )
                )
            ).getOrThrow()

            assertTrue(plan.useCacheOnlyProvider)
            assertEquals(resourceKey, plan.resourceKey)
            assertEquals(141_810L, plan.durationHintMs)
            assertEquals(30_147_193L, plan.contentLengthHintBytes)
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
        assertEquals(PlaybackPreviewClip(15_000L, 90_000L), plan.previewClip)
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
            fallbackBitrate: Int?,
            preferredClipMode: OnlineClipMode,
            expectedDurationMs: Long
        ): Result<ResolvedOnlineStream> {
            calls += 1
            return result
        }
    }

    private class CapturingResolver(
        private val result: Result<ResolvedOnlineStream>
    ) : OnlinePlaybackResolver {
        var lastRequestedLevel: String? = null
        var lastFallbackBitrate: Int? = null

        override suspend fun resolve(
            songId: String,
            requestHeaders: Map<String, String>,
            requestedLevel: String,
            fallbackBitrate: Int?,
            preferredClipMode: OnlineClipMode,
            expectedDurationMs: Long
        ): Result<ResolvedOnlineStream> {
            lastRequestedLevel = requestedLevel
            lastFallbackBitrate = fallbackBitrate
            return result
        }
    }

    private class FakePlanningSourceAdapter(
        private val result: Result<SourceActionResult.MusicUrl>
    ) : SourceAdapter {
        override val metadata: SourceMetadata = SourceMetadata(
            id = "fake-planning-source",
            name = "Fake Planning Source"
        )
        override val normalizedConfigJson: String? = null
        var lastContext: SourceActionContext? = null

        override fun init(): Result<SourceState> = Result.success(SourceState())

        override suspend fun handle(
            action: SourceAction,
            context: SourceActionContext
        ): Result<SourceActionResult> {
            lastContext = context
            return result.map { it as SourceActionResult }
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

    private fun writeCompleteSidecarCache(
        root: File,
        resourceKey: String,
        contentLength: Long,
        durationMs: Long
    ) {
        RandomAccessFile(File(root, "$resourceKey.data"), "rw").use { file ->
            file.setLength(contentLength)
        }
        File(root, "${resourceKey}_config.json").writeText(
            """
            {
              "version": 1,
              "resourceKey": "$resourceKey",
              "contentLength": $contentLength,
              "durationMs": $durationMs,
              "blockSizeBytes": 65536,
              "blocks": [0],
              "completedRanges": [{"start":0,"end":$contentLength}],
              "lastAccessEpochMs": 1
            }
            """.trimIndent()
        )
        File(root, "${resourceKey}_extra.json").writeText(
            """
            {
              "${OnlineCacheMetadata.CLIP_MODE_KEY}": "${OnlineClipMode.FULL.wireValue}"
            }
            """.trimIndent()
        )
    }
}
