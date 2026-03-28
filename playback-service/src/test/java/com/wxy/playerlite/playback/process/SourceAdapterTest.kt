package com.wxy.playerlite.playback.process

import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackPreviewClip
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceAdapterTest {
    @Test
    fun httpMappingSourceAdapter_shouldExtractPlaybackUrlAndComposeStreamHeaders() = runBlocking {
        val adapter = HttpMappingSourceAdapter(
            metadata = SourceMetadata(
                id = "source-http-mapping",
                name = "HTTP Mapping"
            ),
            config = HttpMappingSourceConfig(
                request = HttpMappingRequestConfig(
                    method = HttpMappingMethod.GET,
                    url = "https://mapping.example.com/song/url",
                    query = mapOf(
                        "songId" to "{songId}",
                        "quality" to "{quality}"
                    ),
                    headers = mapOf("Cookie" to "{header.Cookie}")
                ),
                stream = HttpMappingStreamConfig(
                    headers = mapOf("Referer" to "https://mapping.example.com"),
                    inheritRequestHeaders = true
                ),
                response = HttpMappingResponseConfig(
                    playbackUrlPath = "data.url",
                    durationMsPath = "data.duration",
                    expiresAtMsPath = "data.expireAt",
                    previewStartMsPath = "data.preview.start",
                    previewEndMsPath = "data.preview.end"
                ),
                qualityMap = mapOf(
                    PlaybackAudioQuality.EXHIGH to "exhigh"
                )
            ),
            executeRequest = { request ->
                assertEquals("GET", request.method)
                assertEquals("https://mapping.example.com/song/url", request.url)
                assertEquals("12345", request.query.getValue("songId"))
                assertEquals("exhigh", request.query.getValue("quality"))
                assertEquals("MUSIC_U=demo", request.headers.getValue("Cookie"))
                buildJsonObject {
                    put(
                        "data",
                        buildJsonObject {
                            put("url", JsonPrimitive("https://cdn.example.com/audio.flac"))
                            put("duration", JsonPrimitive(219_893L))
                            put("expireAt", JsonPrimitive(9_999_999L))
                            put(
                                "preview",
                                buildJsonObject {
                                    put("start", JsonPrimitive(15_000L))
                                    put("end", JsonPrimitive(90_000L))
                                }
                            )
                        }
                    )
                }
            }
        )

        val result = adapter.handle(
            action = SourceAction.ResolveMusicUrl,
            context = SourceActionContext(
                songId = "12345",
                title = "夜曲",
                artistText = "周杰伦",
                albumTitle = "十一月的萧邦",
                durationMs = 219_893L,
                preferredAudioQuality = PlaybackAudioQuality.EXHIGH,
                requestHeaders = mapOf("Cookie" to "MUSIC_U=demo"),
                previewClip = null
            )
        ).getOrThrow() as SourceActionResult.MusicUrl

        assertEquals("https://cdn.example.com/audio.flac", result.playbackUrl)
        assertEquals(PlaybackAudioQuality.EXHIGH, result.appliedAudioQuality)
        assertEquals("https://mapping.example.com", result.requestHeaders.getValue("Referer"))
        assertEquals("MUSIC_U=demo", result.requestHeaders.getValue("Cookie"))
        assertEquals(PlaybackPreviewClip(15_000L, 90_000L), result.previewClip)
    }

    @Test
    fun httpMappingSourceAdapter_shouldReturnUnsupportedForResolveLyricAndResolvePic() = runBlocking {
        val adapter = HttpMappingSourceAdapter(
            metadata = SourceMetadata(
                id = "source-http-mapping",
                name = "HTTP Mapping"
            ),
            config = HttpMappingSourceConfig(
                request = HttpMappingRequestConfig(
                    method = HttpMappingMethod.GET,
                    url = "https://mapping.example.com/song/url"
                ),
                response = HttpMappingResponseConfig(
                    playbackUrlPath = "data.url"
                )
            ),
            executeRequest = { error("should not be called") }
        )
        val context = SourceActionContext(
            songId = "12345",
            title = "夜曲",
            artistText = "周杰伦",
            albumTitle = "十一月的萧邦",
            durationMs = 219_893L,
            preferredAudioQuality = PlaybackAudioQuality.EXHIGH,
            requestHeaders = emptyMap(),
            previewClip = null
        )

        val lyricResult = adapter.handle(SourceAction.ResolveLyric, context)
        val picResult = adapter.handle(SourceAction.ResolvePic, context)

        assertTrue(lyricResult.isFailure)
        assertTrue(picResult.isFailure)
        assertTrue(lyricResult.exceptionOrNull() is UnsupportedOperationException)
        assertTrue(picResult.exceptionOrNull() is UnsupportedOperationException)
    }

    @Test
    fun httpMappingSourceAdapter_shouldExposeResponseErrorWhenPlaybackUrlMissing() = runBlocking {
        val adapter = HttpMappingSourceAdapter(
            metadata = SourceMetadata(
                id = "source-http-mapping",
                name = "HTTP Mapping"
            ),
            config = HttpMappingSourceConfig(
                request = HttpMappingRequestConfig(
                    method = HttpMappingMethod.GET,
                    url = "https://mapping.example.com/song/url"
                ),
                response = HttpMappingResponseConfig(
                    playbackUrlPath = "data.url",
                    errorMessagePath = "message"
                )
            ),
            executeRequest = {
                buildJsonObject {
                    put("message", JsonPrimitive("mapping payload missing playbackUrl"))
                }
            }
        )

        val result = adapter.handle(
            action = SourceAction.ResolveMusicUrl,
            context = sourceActionContext(preferredAudioQuality = PlaybackAudioQuality.EXHIGH)
        )

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("mapping payload missing playbackUrl") == true
        )
    }

    @Test
    fun neteaseCompatibleSourceAdapter_shouldFallbackToNearestAvailableAudioQuality() = runBlocking {
        val resolver = CapturingResolver(
            Result.success(
                ResolvedOnlineStream(
                    playbackUrl = "https://cdn.example.com/night.flac",
                    requestHeaders = mapOf("Cookie" to "MUSIC_U=demo"),
                    contentLengthBytes = 21_321_000L,
                    durationMs = 219_893L,
                    expiresAtMs = 5_000L
                )
            )
        )
        val adapter = NeteaseCompatibleSourceAdapter(
            metadata = SourceMetadata(
                id = "source-netease",
                name = "Netease Compatible"
            ),
            config = NeteaseCompatibleSourceConfig(
                baseUrl = "https://mirror.example.com/api"
            ),
            normalizedConfigJson = null,
            catalogProvider = CachedSongAudioQualityCatalogProvider(
                remoteDataSource = object : SongAudioQualityCatalogRemoteDataSource {
                    override suspend fun fetchSongAudioQualityCatalog(
                        songId: String,
                        requestHeaders: Map<String, String>
                    ): JsonObject {
                        return catalogPayload()
                    }
                }
            ),
            resolver = resolver
        )

        val result = adapter.handle(
            action = SourceAction.ResolveMusicUrl,
            context = sourceActionContext(preferredAudioQuality = PlaybackAudioQuality.HIRES)
        ).getOrThrow() as SourceActionResult.MusicUrl

        assertEquals(PlaybackAudioQuality.LOSSLESS, result.appliedAudioQuality)
        assertEquals("lossless", resolver.lastRequestedLevel)
        assertEquals(999_000, resolver.lastFallbackBitrate)
    }

    private fun sourceActionContext(
        preferredAudioQuality: PlaybackAudioQuality
    ): SourceActionContext {
        return SourceActionContext(
            songId = "12345",
            title = "夜曲",
            artistText = "周杰伦",
            albumTitle = "十一月的萧邦",
            durationMs = 219_893L,
            preferredAudioQuality = preferredAudioQuality,
            requestHeaders = mapOf("Cookie" to "MUSIC_U=demo"),
            previewClip = null
        )
    }

    private fun catalogPayload(): JsonObject {
        return buildJsonObject {
            put("code", JsonPrimitive(200))
            put(
                "data",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", JsonPrimitive(12345))
                            put(
                                "standard",
                                buildJsonObject {
                                    put("br", JsonPrimitive(128000))
                                    put("size", JsonPrimitive(3_210_000L))
                                    put("sr", JsonPrimitive(44_100))
                                }
                            )
                            put(
                                "lossless",
                                buildJsonObject {
                                    put("br", JsonPrimitive(999000))
                                    put("size", JsonPrimitive(21_321_000L))
                                    put("sr", JsonPrimitive(44_100))
                                }
                            )
                        }
                    )
                }
            )
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
}
