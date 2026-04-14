package com.wxy.playerlite.feature.main

import com.sun.net.httpserver.HttpServer
import com.wxy.playerlite.network.core.AuthHeaderProvider
import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.user.UserSessionInvalidException
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyRecommendedSongsRepositoryTest {
    @Test
    fun fetchDailyRecommendedSongs_shouldMapSongMetadataAndRecommendationReason() = runBlocking {
        val repository = DefaultDailyRecommendedSongsRepository(
            remoteDataSource = FakeDailyRecommendedSongsRemoteDataSource(
                payload = jsonObject(
                    """
                    {
                      "code": 200,
                      "data": {
                        "dailySongs": [
                          {
                            "id": 1887297743,
                            "name": "L’Amour, Les Baguettes, Paris",
                            "ar": [
                              { "id": 1054076, "name": "Stella Jang" }
                            ],
                            "al": {
                              "id": 134839887,
                              "name": "Stairs",
                              "picUrl": "http://example.com/cover.jpg"
                            },
                            "dt": 167172,
                            "reason": "超80%人播放",
                            "recommendReason": "超80%人播放"
                          }
                        ]
                      }
                    }
                    """
                )
            )
        )

        val songs = repository.fetchDailyRecommendedSongs()

        assertEquals(1, songs.size)
        assertEquals("1887297743", songs.single().id)
        assertEquals("1887297743", songs.single().songId)
        assertEquals("L’Amour, Les Baguettes, Paris", songs.single().title)
        assertEquals("Stella Jang", songs.single().artistText)
        assertEquals("134839887", songs.single().albumId)
        assertEquals("Stairs", songs.single().albumTitle)
        assertEquals("http://example.com/cover.jpg", songs.single().coverUrl)
        assertEquals(167172L, songs.single().durationMs)
        assertEquals("超80%人播放", songs.single().recommendReason)
    }

    @Test
    fun fetchDailyRecommendedSongs_shouldFallbackToNullRecommendationReason() = runBlocking {
        val repository = DefaultDailyRecommendedSongsRepository(
            remoteDataSource = FakeDailyRecommendedSongsRemoteDataSource(
                payload = jsonObject(
                    """
                    {
                      "code": 200,
                      "data": {
                        "dailySongs": [
                          {
                            "id": 10,
                            "name": "Song A",
                            "artists": [
                              { "id": 7, "name": "Artist A" }
                            ],
                            "album": {
                              "id": 8,
                              "name": "Album A",
                              "picUrl": "http://example.com/album.jpg"
                            },
                            "dt": 123000
                          }
                        ]
                      }
                    }
                    """
                )
            )
        )

        val songs = repository.fetchDailyRecommendedSongs()

        assertEquals(1, songs.size)
        assertEquals("Artist A", songs.single().artistText)
        assertEquals("8", songs.single().albumId)
        assertEquals("Album A", songs.single().albumTitle)
        assertNull(songs.single().recommendReason)
    }

    @Test
    fun fetchDailyRecommendedSongs_shouldSupportTopLevelDailySongsArray() = runBlocking {
        val repository = DefaultDailyRecommendedSongsRepository(
            remoteDataSource = FakeDailyRecommendedSongsRemoteDataSource(
                payload = jsonObject(
                    """
                    {
                      "code": 200,
                      "dailySongs": [
                        {
                          "id": 11,
                          "name": "Song B",
                          "ar": [
                            { "id": 9, "name": "Artist B" }
                          ],
                          "al": {
                            "id": 10,
                            "name": "Album B",
                            "picUrl": "http://example.com/album-b.jpg"
                          },
                          "dt": 456000,
                          "reason": "与你口味相似"
                        }
                      ]
                    }
                    """
                )
            )
        )

        val songs = repository.fetchDailyRecommendedSongs()

        assertEquals(1, songs.size)
        assertEquals("11", songs.single().songId)
        assertEquals("Artist B", songs.single().artistText)
        assertEquals("10", songs.single().albumId)
        assertEquals("Album B", songs.single().albumTitle)
        assertEquals("与你口味相似", songs.single().recommendReason)
    }

    @Test(expected = IllegalStateException::class)
    fun fetchDailyRecommendedSongs_shouldThrowWhenSuccessPayloadMissingDailySongs() {
        runBlocking {
            val repository = DefaultDailyRecommendedSongsRepository(
                remoteDataSource = FakeDailyRecommendedSongsRemoteDataSource(
                    payload = jsonObject("""{"code":200,"data":{}}""")
                )
            )

            repository.fetchDailyRecommendedSongs()
        }
    }

    @Test
    fun fetchDailyRecommendedSongs_shouldReturnEmptyListForEmptyPayload() = runBlocking {
        val repository = DefaultDailyRecommendedSongsRepository(
            remoteDataSource = FakeDailyRecommendedSongsRemoteDataSource(
                payload = jsonObject("""{"code":200,"data":{"dailySongs":[]}}""")
            )
        )

        val songs = repository.fetchDailyRecommendedSongs()

        assertEquals(emptyList<DailyRecommendedSongUiModel>(), songs)
    }

    @Test(expected = UserSessionInvalidException::class)
    fun fetchDailyRecommendedSongs_shouldThrowSessionInvalidWhenResponseCodeIs301() {
        runBlocking {
            val server = DailyRecommendedSongsHttpServer(
                responses = mapOf(
                    "/recommend/songs" to """{"code":301,"message":"需要登录"}"""
                )
            )
            server.start()
            try {
                val repository = DefaultDailyRecommendedSongsRepository(
                    remoteDataSource = NeteaseDailyRecommendedSongsRemoteDataSource(
                        httpClient = JsonHttpClient(
                            baseUrl = server.baseUrl,
                            authHeaderProvider = AuthHeaderProvider {
                                mapOf("Cookie" to "MUSIC_U=token")
                            }
                        )
                    )
                )

                repository.fetchDailyRecommendedSongs()
            } finally {
                server.stop()
            }
        }
    }
}

private class FakeDailyRecommendedSongsRemoteDataSource(
    private val payload: JsonObject
) : DailyRecommendedSongsRemoteDataSource {
    override suspend fun fetchDailyRecommendedSongs(): JsonObject = payload
}

private class DailyRecommendedSongsHttpServer(
    private val responses: Map<String, String>
) {
    private val server = HttpServer.create(InetSocketAddress(0), 0).apply {
        createContext("/") { exchange ->
            val body = responses[exchange.requestURI.path]
                ?: error("Unexpected path ${exchange.requestURI.path}")
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    val baseUrl: String
        get() = "http://127.0.0.1:${server.address.port}"

    fun start() {
        server.start()
    }

    fun stop() {
        server.stop(0)
    }
}

private fun jsonObject(raw: String): JsonObject {
    return Json.parseToJsonElement(raw.trimIndent()).jsonObject
}
