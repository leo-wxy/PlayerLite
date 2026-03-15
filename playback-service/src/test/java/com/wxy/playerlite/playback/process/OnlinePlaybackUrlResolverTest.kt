package com.wxy.playerlite.playback.process

import com.wxy.playerlite.playback.model.PlaybackPreviewClip
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
        assertTrue(result.exceptionOrNull()?.message?.contains("暂无版权") == true)
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
            return songUrlPayload
        }

        override suspend fun checkMusic(
            songId: String,
            bitrate: Int,
            requestHeaders: Map<String, String>
        ): JsonObject {
            checkMusicCalls += 1
            return checkMusicPayload
        }
    }

    private fun songUrlPayload(
        url: String?,
        size: Long,
        expiSeconds: Int,
        durationMs: Long,
        previewClipEndMs: Long? = null
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
