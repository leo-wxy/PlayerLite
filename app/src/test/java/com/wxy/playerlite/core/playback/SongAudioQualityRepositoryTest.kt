package com.wxy.playerlite.core.playback

import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SongAudioQualityRepositoryTest {
    @Test
    fun fetchCatalog_shouldMapCurrentMusicDetailObjectShape() = runBlocking {
        val remote = FakeSongAudioQualityRemoteDataSource(
            payload = buildCurrentMusicDetailPayload()
        )
        val repository = DefaultSongAudioQualityRepository(
            remoteDataSource = remote
        )

        val catalog = repository.fetchCatalog("296839")

        assertNotNull(catalog)
        assertEquals(
            listOf(
                PlaybackAudioQuality.LOSSLESS,
                PlaybackAudioQuality.EXHIGH,
                PlaybackAudioQuality.STANDARD
            ),
            catalog?.options?.map { it.quality }
        )
        assertEquals(listOf("sq", "h", "m"), catalog?.options?.map { it.rawKey })
    }

    @Test
    fun fetchCatalog_shouldMapPayloadAndReuseCacheForSameSongId() = runBlocking {
        val remote = FakeSongAudioQualityRemoteDataSource(
            payload = buildPayload()
        )
        val repository = DefaultSongAudioQualityRepository(
            remoteDataSource = remote
        )

        val first = repository.fetchCatalog("347230")
        val second = repository.fetchCatalog("347230")

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
    fun clear_shouldDropInMemoryCacheAndForceNextFetch() = runBlocking {
        val remote = FakeSongAudioQualityRemoteDataSource(
            payload = buildPayload()
        )
        val repository = DefaultSongAudioQualityRepository(
            remoteDataSource = remote
        )

        assertNotNull(repository.fetchCatalog("347230"))
        repository.clear()
        assertNull(repository.peekCachedCatalog("347230"))
        assertNotNull(repository.fetchCatalog("347230"))

        assertEquals(2, remote.calls)
    }

    private class FakeSongAudioQualityRemoteDataSource(
        private val payload: JsonObject
    ) : SongAudioQualityRemoteDataSource {
        var calls: Int = 0

        override suspend fun fetchCatalog(songId: String): JsonObject {
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

    private fun buildCurrentMusicDetailPayload(): JsonObject {
        return buildJsonObject {
            put("code", JsonPrimitive(200))
            put(
                "data",
                buildJsonObject {
                    put("songId", JsonPrimitive(296839))
                    put(
                        "h",
                        buildJsonObject {
                            put("br", JsonPrimitive(320000))
                            put("size", JsonPrimitive(9_894_182))
                            put("sr", JsonPrimitive(44_100))
                        }
                    )
                    put(
                        "m",
                        buildJsonObject {
                            put("br", JsonPrimitive(192000))
                            put("size", JsonPrimitive(5_936_527))
                            put("sr", JsonPrimitive(44_100))
                        }
                    )
                    put(
                        "l",
                        buildJsonObject {
                            put("br", JsonPrimitive(128000))
                            put("size", JsonPrimitive(3_957_699))
                            put("sr", JsonPrimitive(44_100))
                        }
                    )
                    put(
                        "sq",
                        buildJsonObject {
                            put("br", JsonPrimitive(810381))
                            put("size", JsonPrimitive(25_048_891))
                            put("sr", JsonPrimitive(44_100))
                        }
                    )
                    put(
                        "hr",
                        JsonPrimitive(null as String?)
                    )
                    put(
                        "vi",
                        buildJsonObject {
                            put("br", JsonPrimitive(832042))
                            put("size", JsonPrimitive(25_718_154))
                            put("sr", JsonPrimitive(44_100))
                        }
                    )
                }
            )
        }
    }
}
