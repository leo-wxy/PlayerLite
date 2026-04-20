package com.wxy.playerlite.playback.process

import com.wxy.playerlite.playback.model.PlaybackPreviewClip
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePlaybackUrlResolverTest {
    @Test
    fun remoteDataSource_shouldUseLatestBaseUrlProviderAcrossRequests() = runBlocking {
        var currentBaseUrl = "https://source-a.example.com"
        val recordedRequests = mutableListOf<Pair<String, String>>()
        val remote = NeteaseOnlinePlaybackRemoteDataSource(
            baseUrlProvider = { currentBaseUrl },
            httpGet = { baseUrl, path, _, _ ->
                recordedRequests += baseUrl to path
                songUrlPayload(
                    url = "https://media.example.com/source.mp3",
                    size = 8_798_445L,
                    expiSeconds = 1200,
                    durationMs = 219_893L
                )
            }
        )

        remote.fetchSongUrlV1(
            songIds = "1969519579",
            level = "exhigh",
            requestHeaders = emptyMap()
        )
        currentBaseUrl = "https://source-b.example.com"
        remote.fetchSongUrl(
            songIds = "1969519579",
            bitrate = 320_000,
            requestHeaders = emptyMap()
        )

        assertEquals(
            listOf(
                "https://source-a.example.com" to "/song/url/v1",
                "https://source-b.example.com" to "/song/url"
            ),
            recordedRequests
        )
    }

    @Test
    fun resolveFallsBackToLegacySongUrlAndCachesFreshResult() = runBlocking {
        val remote = FakeOnlinePlaybackRemoteDataSource(
            songUrlV1Payload = songUrlPayload(url = null, size = 0L, expiSeconds = 1200, durationMs = 0L),
            songUrlPayload = songUrlPayload(
                url = "https://example.com/fallback.mp3",
                size = 8_798_445L,
                expiSeconds = 1200,
                durationMs = 219_893L
            )
        )
        val resolver = OnlinePlaybackUrlResolver(
            remoteDataSource = remote,
            memoryCache = ResolvedOnlineUrlMemoryCache(maxEntries = 10),
            nowMs = { 1_000L }
        )

        val first = resolver.resolve(
            songId = "1969519579",
            requestHeaders = mapOf("Cookie" to "MUSIC_U=demo"),
            requestedLevel = "exhigh"
        ).getOrThrow()
        val second = resolver.resolve(
            songId = "1969519579",
            requestHeaders = mapOf("Cookie" to "MUSIC_U=demo"),
            requestedLevel = "exhigh"
        ).getOrThrow()

        assertEquals("https://example.com/fallback.mp3", first.playbackUrl)
        assertEquals(8_798_445L, first.contentLengthBytes)
        assertEquals(219_893L, first.durationMs)
        assertTrue(first.requestHeaders.getValue("Cookie").contains("os=pc"))
        assertEquals(first, second)
        assertEquals(1, remote.songUrlV1Calls)
        assertEquals(1, remote.songUrlCalls)
    }

    @Test
    fun resolve_shouldUseProvidedLegacyFallbackBitrateWhenV1Fails() = runBlocking {
        val remote = FakeOnlinePlaybackRemoteDataSource(
            songUrlV1Payload = songUrlPayload(url = null, size = 0L, expiSeconds = 1200, durationMs = 0L),
            songUrlPayload = songUrlPayload(
                url = "https://example.com/lossless.flac",
                size = 21_321_000L,
                expiSeconds = 1200,
                durationMs = 219_893L
            )
        )
        val resolver = OnlinePlaybackUrlResolver(
            remoteDataSource = remote,
            memoryCache = ResolvedOnlineUrlMemoryCache(maxEntries = 10),
            nowMs = { 1_000L }
        )

        val resolved = resolver.resolve(
            songId = "1969519579",
            requestHeaders = emptyMap(),
            requestedLevel = "lossless",
            fallbackBitrate = 999_000
        ).getOrThrow()

        assertEquals("https://example.com/lossless.flac", resolved.playbackUrl)
        assertEquals(999_000, remote.lastSongUrlBitrate)
    }

    @Test
    fun resolveReturnsCheckMusicFailureMessageWhenNoPlayableUrlExists() = runBlocking {
        val remote = FakeOnlinePlaybackRemoteDataSource(
            songUrlV1Payload = songUrlPayload(url = null, size = 0L, expiSeconds = 1200, durationMs = 0L),
            songUrlPayload = songUrlPayload(url = null, size = 0L, expiSeconds = 1200, durationMs = 0L),
            checkMusicPayload = buildJsonObject {
                put("success", JsonPrimitive(false))
                put("message", JsonPrimitive("亲爱的,暂无版权"))
            }
        )
        val resolver = OnlinePlaybackUrlResolver(
            remoteDataSource = remote,
            memoryCache = ResolvedOnlineUrlMemoryCache(maxEntries = 10),
            nowMs = { 1_000L }
        )

        val result = resolver.resolve(
            songId = "1969519579",
            requestHeaders = emptyMap(),
            requestedLevel = "exhigh"
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as? OnlinePlaybackResolutionException
        assertEquals(OnlinePlaybackFailureKind.RESOURCE_UNAVAILABLE, error?.failure?.kind)
        assertTrue(error?.message?.contains("暂无版权") == true)
    }

    @Test
    fun resolveShouldClassifyExpiredUrlWhenPayloadMarksUrlExpired() = runBlocking {
        val remote = FakeOnlinePlaybackRemoteDataSource(
            songUrlV1Payload = songUrlPayload(
                url = null,
                size = 0L,
                expiSeconds = 1200,
                durationMs = 0L,
                message = "URL expired"
            ),
            songUrlPayload = songUrlPayload(
                url = null,
                size = 0L,
                expiSeconds = 1200,
                durationMs = 0L,
                message = "URL expired"
            )
        )
        val resolver = OnlinePlaybackUrlResolver(
            remoteDataSource = remote,
            memoryCache = ResolvedOnlineUrlMemoryCache(maxEntries = 10),
            nowMs = { 1_000L }
        )

        val result = resolver.resolve(
            songId = "1969519579",
            requestHeaders = emptyMap(),
            requestedLevel = "exhigh"
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as? OnlinePlaybackResolutionException
        assertEquals(OnlinePlaybackFailureKind.URL_EXPIRED, error?.failure?.kind)
        assertTrue(error?.message?.contains("expired") == true)
    }

    @Test
    fun resolveShouldClassifyTransientNetworkFailureAsRetryable() = runBlocking {
        val resolver = OnlinePlaybackUrlResolver(
            remoteDataSource = object : OnlinePlaybackRemoteDataSource {
                override suspend fun fetchSongUrlV1(
                    songIds: String,
                    level: String,
                    requestHeaders: Map<String, String>,
                    unblock: Boolean
                ): JsonObject {
                    throw IOException("network timeout")
                }

                override suspend fun fetchSongUrl(
                    songIds: String,
                    bitrate: Int,
                    requestHeaders: Map<String, String>
                ): JsonObject {
                    throw IOException("connection reset")
                }

                override suspend fun checkMusic(
                    songId: String,
                    bitrate: Int,
                    requestHeaders: Map<String, String>
                ): JsonObject {
                    return buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("message", JsonPrimitive("ok"))
                    }
                }
            },
            memoryCache = ResolvedOnlineUrlMemoryCache(maxEntries = 10),
            nowMs = { 1_000L }
        )

        val result = resolver.resolve(
            songId = "1969519579",
            requestHeaders = emptyMap(),
            requestedLevel = "exhigh"
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as? OnlinePlaybackResolutionException
        assertEquals(OnlinePlaybackFailureKind.RETRYABLE_NETWORK, error?.failure?.kind)
        assertTrue(error?.message?.contains("network") == true)
    }

    @Test
    fun resolveFull_shouldIgnoreCachedPreviewResultAndRequeryRemote() = runBlocking {
        val cache = ResolvedOnlineUrlMemoryCache(maxEntries = 10)
        cache.put(
            OnlinePlaybackCacheKey(
                songId = "1969519579",
                level = "exhigh",
                clipMode = OnlineClipMode.FULL
            ),
            ResolvedOnlineStream(
                playbackUrl = "https://example.com/preview.mp3",
                requestHeaders = emptyMap(),
                contentLengthBytes = 1_024L,
                durationMs = 60_000L,
                expiresAtMs = 5_000L,
                previewClip = PlaybackPreviewClip(startMs = 0L, endMs = 60_000L)
            )
        )
        cache.put(
            OnlinePlaybackCacheKey(
                songId = "1969519579",
                level = "exhigh",
                clipMode = OnlineClipMode.PREVIEW
            ),
            ResolvedOnlineStream(
                playbackUrl = "https://example.com/preview.mp3",
                requestHeaders = emptyMap(),
                contentLengthBytes = 1_024L,
                durationMs = 60_000L,
                expiresAtMs = 5_000L,
                previewClip = PlaybackPreviewClip(startMs = 0L, endMs = 60_000L)
            )
        )
        val remote = FakeOnlinePlaybackRemoteDataSource(
            songUrlV1Payload = songUrlPayload(
                url = "https://example.com/full.mp3",
                size = 8_798_445L,
                expiSeconds = 1200,
                durationMs = 219_893L
            ),
            songUrlPayload = songUrlPayload(
                url = "https://example.com/full.mp3",
                size = 8_798_445L,
                expiSeconds = 1200,
                durationMs = 219_893L
            )
        )
        val resolver = OnlinePlaybackUrlResolver(
            remoteDataSource = remote,
            memoryCache = cache,
            nowMs = { 1_000L }
        )

        val resolved = resolver.resolve(
            songId = "1969519579",
            requestHeaders = mapOf("Cookie" to "MUSIC_U=member"),
            requestedLevel = "exhigh",
            preferredClipMode = OnlineClipMode.FULL
        ).getOrThrow()

        assertEquals("https://example.com/full.mp3", resolved.playbackUrl)
        assertEquals(null, resolved.previewClip)
        assertEquals(1, remote.songUrlV1Calls)
    }

    @Test
    fun resolvePreview_shouldNotOccupyUrlMemoryCache() = runBlocking {
        val remote = FakeOnlinePlaybackRemoteDataSource(
            songUrlV1Payload = songUrlPayload(
                url = "https://example.com/preview.mp3",
                size = 1_024L,
                expiSeconds = 1200,
                durationMs = 60_000L,
                previewClipEndMs = 60_000L
            ),
            songUrlPayload = songUrlPayload(
                url = "https://example.com/preview.mp3",
                size = 1_024L,
                expiSeconds = 1200,
                durationMs = 60_000L,
                previewClipEndMs = 60_000L
            )
        )
        val resolver = OnlinePlaybackUrlResolver(
            remoteDataSource = remote,
            memoryCache = ResolvedOnlineUrlMemoryCache(maxEntries = 10),
            nowMs = { 1_000L }
        )

        val first = resolver.resolve(
            songId = "1969519588",
            requestHeaders = emptyMap(),
            requestedLevel = "exhigh",
            preferredClipMode = OnlineClipMode.FULL
        ).getOrThrow()
        val second = resolver.resolve(
            songId = "1969519588",
            requestHeaders = emptyMap(),
            requestedLevel = "exhigh",
            preferredClipMode = OnlineClipMode.FULL
        ).getOrThrow()

        assertEquals("https://example.com/preview.mp3", first.playbackUrl)
        assertEquals("https://example.com/preview.mp3", second.playbackUrl)
        assertEquals(2, remote.songUrlV1Calls)
    }

    @Test
    fun resolveShortFullWithoutFreeTrialInfo_shouldTreatAsPreviewAndSkipCache() = runBlocking {
        val remote = FakeOnlinePlaybackRemoteDataSource(
            songUrlV1Payload = songUrlPayload(
                url = "https://example.com/suspicious-short.mp3",
                size = 3_600_000L,
                expiSeconds = 1200,
                durationMs = 90_000L
            ),
            songUrlPayload = songUrlPayload(
                url = "https://example.com/suspicious-short.mp3",
                size = 3_600_000L,
                expiSeconds = 1200,
                durationMs = 90_000L
            )
        )
        val cache = ResolvedOnlineUrlMemoryCache(maxEntries = 10)
        val resolver = OnlinePlaybackUrlResolver(
            remoteDataSource = remote,
            memoryCache = cache,
            nowMs = { 1_000L }
        )

        val first = resolver.resolve(
            songId = "1299550532",
            requestHeaders = mapOf("Cookie" to "MUSIC_U=member"),
            requestedLevel = "exhigh",
            preferredClipMode = OnlineClipMode.FULL,
            expectedDurationMs = 229_333L
        ).getOrThrow()
        val second = resolver.resolve(
            songId = "1299550532",
            requestHeaders = mapOf("Cookie" to "MUSIC_U=member"),
            requestedLevel = "exhigh",
            preferredClipMode = OnlineClipMode.FULL,
            expectedDurationMs = 229_333L
        ).getOrThrow()

        assertEquals(0L, first.previewClip?.startMs)
        assertEquals(90_000L, first.previewClip?.endMs)
        assertEquals(2, remote.songUrlV1Calls)
        assertEquals(
            null,
            cache.getIfFresh(
                OnlinePlaybackCacheKey(
                    songId = "1299550532",
                    level = "exhigh",
                    clipMode = OnlineClipMode.FULL
                ),
                nowMs = 1_000L
            )
        )
        assertEquals("https://example.com/suspicious-short.mp3", second.playbackUrl)
    }

    @Test
    fun resolve_shouldIsolateMemoryCacheBySourceIdentity() = runBlocking {
        var sourceIdentity = "source-a"
        val remote = FakeOnlinePlaybackRemoteDataSource(
            songUrlV1Payload = songUrlPayload(
                url = "https://example.com/source-a.mp3",
                size = 8_798_445L,
                expiSeconds = 1200,
                durationMs = 219_893L
            ),
            songUrlPayload = songUrlPayload(
                url = "https://example.com/source-a.mp3",
                size = 8_798_445L,
                expiSeconds = 1200,
                durationMs = 219_893L
            )
        )
        val resolver = OnlinePlaybackUrlResolver(
            remoteDataSource = remote,
            memoryCache = ResolvedOnlineUrlMemoryCache(maxEntries = 10),
            sourceIdentityProvider = { sourceIdentity },
            nowMs = { 1_000L }
        )

        resolver.resolve(
            songId = "1969519579",
            requestHeaders = emptyMap(),
            requestedLevel = "exhigh"
        ).getOrThrow()
        resolver.resolve(
            songId = "1969519579",
            requestHeaders = emptyMap(),
            requestedLevel = "exhigh"
        ).getOrThrow()
        sourceIdentity = "source-b"
        resolver.resolve(
            songId = "1969519579",
            requestHeaders = emptyMap(),
            requestedLevel = "exhigh"
        ).getOrThrow()

        assertEquals(2, remote.songUrlV1Calls)
    }

    @Test
    fun clear_shouldDropResolvedUrlMemoryCache() = runBlocking {
        val remote = FakeOnlinePlaybackRemoteDataSource(
            songUrlV1Payload = songUrlPayload(
                url = "https://example.com/source-a.mp3",
                size = 8_798_445L,
                expiSeconds = 1200,
                durationMs = 219_893L
            ),
            songUrlPayload = songUrlPayload(
                url = "https://example.com/source-a.mp3",
                size = 8_798_445L,
                expiSeconds = 1200,
                durationMs = 219_893L
            )
        )
        val resolver = OnlinePlaybackUrlResolver(
            remoteDataSource = remote,
            memoryCache = ResolvedOnlineUrlMemoryCache(maxEntries = 10),
            nowMs = { 1_000L }
        )

        resolver.resolve(
            songId = "1969519579",
            requestHeaders = emptyMap(),
            requestedLevel = "exhigh"
        ).getOrThrow()
        resolver.resolve(
            songId = "1969519579",
            requestHeaders = emptyMap(),
            requestedLevel = "exhigh"
        ).getOrThrow()

        resolver.clear()

        resolver.resolve(
            songId = "1969519579",
            requestHeaders = emptyMap(),
            requestedLevel = "exhigh"
        ).getOrThrow()

        assertEquals(2, remote.songUrlV1Calls)
    }

    private class FakeOnlinePlaybackRemoteDataSource(
        private val songUrlV1Payload: JsonObject,
        private val songUrlPayload: JsonObject,
        private val checkMusicPayload: JsonObject = buildJsonObject {
            put("success", JsonPrimitive(true))
            put("message", JsonPrimitive("ok"))
        }
    ) : OnlinePlaybackRemoteDataSource {
        var songUrlV1Calls: Int = 0
        var songUrlCalls: Int = 0
        var checkMusicCalls: Int = 0
        var lastSongUrlBitrate: Int? = null
        var lastCheckMusicBitrate: Int? = null

        override suspend fun fetchSongUrlV1(
            songIds: String,
            level: String,
            requestHeaders: Map<String, String>,
            unblock: Boolean
        ): JsonObject {
            songUrlV1Calls += 1
            return songUrlV1Payload
        }

        override suspend fun fetchSongUrl(
            songIds: String,
            bitrate: Int,
            requestHeaders: Map<String, String>
        ): JsonObject {
            songUrlCalls += 1
            lastSongUrlBitrate = bitrate
            return songUrlPayload
        }

        override suspend fun checkMusic(
            songId: String,
            bitrate: Int,
            requestHeaders: Map<String, String>
        ): JsonObject {
            checkMusicCalls += 1
            lastCheckMusicBitrate = bitrate
            return checkMusicPayload
        }
    }

    private fun songUrlPayload(
        url: String?,
        size: Long,
        expiSeconds: Int,
        durationMs: Long,
        previewClipEndMs: Long? = null,
        message: String? = null
    ): JsonObject {
        return buildJsonObject {
            put("code", JsonPrimitive(200))
            put(
                "data",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", JsonPrimitive(1969519579))
                            url?.let { put("url", JsonPrimitive(it)) }
                            put("size", JsonPrimitive(size))
                            put("expi", JsonPrimitive(expiSeconds))
                            put("time", JsonPrimitive(durationMs))
                            message?.let { put("message", JsonPrimitive(it)) }
                            if (previewClipEndMs != null) {
                                put(
                                    "freeTrialInfo",
                                    buildJsonObject {
                                        put("start", JsonPrimitive(0L))
                                        put("end", JsonPrimitive(previewClipEndMs))
                                    }
                                )
                            } else {
                                put("freeTrialInfo", JsonPrimitive(null))
                            }
                        }
                    )
                }
            )
        }
    }
}
