package com.wxy.playerlite.feature.playlist

import com.sun.net.httpserver.HttpServer
import com.wxy.playerlite.network.core.AuthHeaderProvider
import com.wxy.playerlite.network.core.JsonHttpClient
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistDetailRepositoryTest {
    @Test
    fun fetchPlaylistHeader_shouldMapStablePlaylistHeader() = runBlocking {
        val repository = DefaultPlaylistDetailRepository(
            remoteDataSource = FakePlaylistDetailRemoteDataSource(
                detailPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "playlist": {
                        "id": 3778678,
                        "name": "热歌榜",
                        "coverImgUrl": "http://example.com/playlist.jpg",
                        "description": "云音乐热歌榜",
                        "trackCount": 200,
                        "playCount": 13755319296,
                        "subscribedCount": 12882840,
                        "creator": {
                          "nickname": "网易云音乐"
                        }
                      }
                    }
                    """
                ),
                tracksPayload = jsonObject("""{"code":200,"songs":[]}""")
            )
        )

        val header = repository.fetchPlaylistHeader("3778678")

        assertEquals("3778678", header.playlistId)
        assertEquals("热歌榜", header.title)
        assertEquals("网易云音乐", header.creatorName)
        assertEquals("云音乐热歌榜", header.description)
        assertEquals("http://example.com/playlist.jpg", header.coverUrl)
        assertEquals(200, header.trackCount)
        assertEquals(13755319296L, header.playCount)
        assertEquals(12882840L, header.subscribedCount)
    }

    @Test
    fun fetchPlaylistTracks_shouldMapCompactTrackRows() = runBlocking {
        val repository = DefaultPlaylistDetailRepository(
            remoteDataSource = FakePlaylistDetailRemoteDataSource(
                detailPayload = jsonObject("""{"code":200,"playlist":{"id":3778678,"name":"热歌榜"}}"""),
                tracksPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "songs": [
                        {
                          "id": 1973665667,
                          "name": "海屿你",
                          "ar": [{ "name": "马也_Crabbit" }],
                          "al": {
                            "name": "海屿你",
                            "picUrl": "http://example.com/cover.jpg"
                          },
                          "dt": 295940
                        }
                      ]
                    }
                    """
                )
            )
        )

        val tracks = repository.fetchPlaylistTracks(
            playlistId = "3778678",
            offset = 0,
            limit = 30
        )

        assertEquals(1, tracks.size)
        assertEquals("1973665667", tracks.single().trackId)
        assertEquals("海屿你", tracks.single().title)
        assertEquals("马也_Crabbit", tracks.single().artistText)
        assertEquals("海屿你", tracks.single().albumTitle)
        assertEquals("http://example.com/cover.jpg", tracks.single().coverUrl)
        assertEquals(295940L, tracks.single().durationMs)
    }

    @Test
    fun fetchPlaylistRequests_shouldUseProtectedEndpointsAndForwardAuthHeaders() = runBlocking {
        val server = MultiResponseHttpServer(
            responses = mapOf(
                "/playlist/detail" to """
                    {
                      "code": 200,
                      "playlist": {
                        "id": 3778678,
                        "name": "热歌榜"
                      }
                    }
                """.trimIndent(),
                "/playlist/track/all" to """
                    {
                      "code": 200,
                      "songs": []
                    }
                """.trimIndent()
            )
        )
        server.start()
        try {
            val client = JsonHttpClient(
                baseUrl = server.baseUrl,
                authHeaderProvider = AuthHeaderProvider {
                    mapOf(
                        "Cookie" to "MUSIC_U=token; __csrf=csrf-token;",
                        "X-CSRF-Token" to "csrf-token"
                    )
                }
            )
            val remoteDataSource = NeteasePlaylistDetailRemoteDataSource(client)

            remoteDataSource.fetchPlaylistDetail("3778678")
            remoteDataSource.fetchPlaylistTracks(
                playlistId = "3778678",
                offset = 30,
                limit = 30
            )

            assertEquals(
                listOf(
                    "/playlist/detail?id=3778678",
                    "/playlist/track/all?id=3778678&offset=30&limit=30"
                ),
                server.requestPaths
            )
            assertTrue(server.cookieHeaders.all { it.contains("MUSIC_U=token") })
            assertEquals(listOf("csrf-token", "csrf-token"), server.csrfHeaders)
        } finally {
            server.close()
        }
    }
}

private class FakePlaylistDetailRemoteDataSource(
    private val detailPayload: JsonObject,
    private val tracksPayload: JsonObject
) : PlaylistDetailRemoteDataSource {
    override suspend fun fetchPlaylistDetail(playlistId: String): JsonObject = detailPayload

    override suspend fun fetchPlaylistTracks(
        playlistId: String,
        offset: Int,
        limit: Int
    ): JsonObject = tracksPayload
}

private class MultiResponseHttpServer(
    private val responses: Map<String, String>
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress(0), 0)

    val requestPaths = mutableListOf<String>()
    val cookieHeaders = mutableListOf<String>()
    val csrfHeaders = mutableListOf<String>()

    val baseUrl: String
        get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requestPaths += buildString {
                append(path)
                exchange.requestURI.rawQuery?.let { query ->
                    append('?')
                    append(query)
                }
            }
            cookieHeaders += exchange.requestHeaders.getFirst("Cookie").orEmpty()
            csrfHeaders += exchange.requestHeaders.getFirst("X-CSRF-Token").orEmpty()
            val payload = responses.getValue(path).toByteArray()
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { output ->
                output.write(payload)
            }
        }
    }

    fun start() {
        server.start()
    }

    override fun close() {
        server.stop(0)
    }
}

private fun jsonObject(raw: String): JsonObject {
    return Json.parseToJsonElement(raw.trimIndent()).jsonObject
}
