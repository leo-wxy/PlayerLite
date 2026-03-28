package com.wxy.playerlite.playback.process

import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CachedSongAudioQualityCatalogProviderTest {
    @Test
    fun remoteDataSource_shouldUseLatestBaseUrlProviderAcrossRequests() = runBlocking {
        var currentBaseUrl = "https://source-a.example.com"
        val recordedRequests = mutableListOf<Pair<String, String>>()
        val remote = NeteaseSongAudioQualityCatalogRemoteDataSource(
            baseUrlProvider = { currentBaseUrl },
            httpGet = { baseUrl, path, _, _ ->
                recordedRequests += baseUrl to path
                buildPayload()
            }
        )

        remote.fetchSongAudioQualityCatalog(
            songId = "347230",
            requestHeaders = emptyMap()
        )
        currentBaseUrl = "https://source-b.example.com"
        remote.fetchSongAudioQualityCatalog(
            songId = "347230",
            requestHeaders = emptyMap()
        )

        assertEquals(
            listOf(
                "https://source-a.example.com" to "/song/music/detail",
                "https://source-b.example.com" to "/song/music/detail"
            ),
            recordedRequests
        )
    }

    @Test
    fun getCatalog_shouldMapPayloadAndReuseCacheForSameSongAndHeaders() = runBlocking {
        val remote = FakeSongAudioQualityCatalogRemoteDataSource(
            payload = buildPayload()
        )
        val provider = CachedSongAudioQualityCatalogProvider(remoteDataSource = remote)

        val first = provider.getCatalog(
            songId = "347230",
            requestHeaders = mapOf("Cookie" to "MUSIC_U=demo")
        )
        val second = provider.getCatalog(
            songId = "347230",
            requestHeaders = mapOf("Cookie" to "MUSIC_U=demo")
        )

        assertNotNull(first)
        assertEquals(
            listOf(
                PlaybackAudioQuality.JYMASTER,
                PlaybackAudioQuality.LOSSLESS,
                PlaybackAudioQuality.STANDARD
            ),
            first?.options?.map { it.quality }
        )
        assertEquals(1, remote.calls)
        assertEquals(first, second)
    }

    @Test
    fun getCatalog_shouldIsolateCacheBySourceIdentity() = runBlocking {
        var sourceIdentity = "source-a"
        val remote = FakeSongAudioQualityCatalogRemoteDataSource(
            payload = buildPayload()
        )
        val provider = CachedSongAudioQualityCatalogProvider(
            remoteDataSource = remote,
            sourceIdentityProvider = { sourceIdentity }
        )

        provider.getCatalog(
            songId = "347230",
            requestHeaders = mapOf("Cookie" to "MUSIC_U=demo")
        )
        provider.getCatalog(
            songId = "347230",
            requestHeaders = mapOf("Cookie" to "MUSIC_U=demo")
        )
        sourceIdentity = "source-b"
        provider.getCatalog(
            songId = "347230",
            requestHeaders = mapOf("Cookie" to "MUSIC_U=demo")
        )

        assertEquals(2, remote.calls)
    }

    private class FakeSongAudioQualityCatalogRemoteDataSource(
        private val payload: JsonObject
    ) : SongAudioQualityCatalogRemoteDataSource {
        var calls: Int = 0

        override suspend fun fetchSongAudioQualityCatalog(
            songId: String,
            requestHeaders: Map<String, String>
        ): JsonObject {
            calls += 1
            return payload
        }
    }

    private fun buildPayload(): JsonObject {
        return buildJsonObject {
            put("code", JsonPrimitive(200))
            put(
                "data",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", JsonPrimitive(347230))
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
                            put(
                                "jymaster",
                                buildJsonObject {
                                    put("br", JsonPrimitive(1_999_000))
                                    put("size", JsonPrimitive(48_321_000L))
                                    put("sr", JsonPrimitive(192_000))
                                }
                            )
                        }
                    )
                }
            )
        }
    }
}
