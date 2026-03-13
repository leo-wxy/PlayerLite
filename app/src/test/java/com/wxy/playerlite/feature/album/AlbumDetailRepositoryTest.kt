package com.wxy.playerlite.feature.album

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

class AlbumDetailRepositoryTest {
    @Test
    fun fetchAlbumContent_shouldMapStableAlbumSummaryAndTracks() = runBlocking {
        val repository = DefaultAlbumDetailRepository(
            remoteDataSource = FakeAlbumDetailRemoteDataSource(
                contentPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "album": {
                        "id": 32311,
                        "name": "神的游戏",
                        "picUrl": "http://example.com/album.jpg",
                        "description": "专辑简介",
                        "company": "索尼音乐",
                        "publishTime": 1344528000000,
                        "size": 9,
                        "artist": {
                          "name": "张悬"
                        }
                      },
                      "songs": [
                        {
                          "id": 326696,
                          "name": "疯狂的阳光",
                          "ar": [{ "name": "张悬" }],
                          "al": {
                            "name": "神的游戏",
                            "picUrl": "http://example.com/track.jpg"
                          },
                          "dt": 235146
                        }
                      ]
                    }
                    """
                ),
                dynamicPayload = jsonObject("""{"code":200,"commentCount":1990,"shareCount":8542,"subCount":66888}""")
            )
        )

        val content = repository.fetchAlbumContent(
            albumId = "32311",
            offset = 0,
            limit = 30
        )

        assertEquals("32311", content.albumId)
        assertEquals("神的游戏", content.title)
        assertEquals("张悬", content.artistText)
        assertEquals("专辑简介", content.description)
        assertEquals("索尼音乐", content.company)
        assertEquals(9, content.trackCount)
        assertEquals(1, content.tracks.size)
        assertEquals("326696", content.tracks.single().trackId)
    }

    @Test
    fun fetchAlbumDynamic_shouldMapCounts() = runBlocking {
        val repository = DefaultAlbumDetailRepository(
            remoteDataSource = FakeAlbumDetailRemoteDataSource(
                contentPayload = jsonObject("""{"code":200,"album":{"id":32311,"name":"神的游戏"},"songs":[]}"""),
                dynamicPayload = jsonObject(
                    """{"code":200,"commentCount":1990,"shareCount":8542,"subCount":66888}"""
                )
            )
        )

        val dynamic = repository.fetchAlbumDynamic("32311")

        assertEquals(1990, dynamic.commentCount)
        assertEquals(8542, dynamic.shareCount)
        assertEquals(66888, dynamic.subscribedCount)
    }

    @Test
    fun fetchAlbumRequests_shouldUseProtectedEndpointsAndForwardAuthHeaders() = runBlocking {
        val server = AlbumResponseHttpServer(
            responses = mapOf(
                "/album" to """
                    {
                      "code": 200,
                      "album": {
                        "id": 32311,
                        "name": "神的游戏"
                      },
                      "songs": []
                    }
                """.trimIndent(),
                "/album/detail/dynamic" to """
                    {
                      "code": 200,
                      "commentCount": 1990,
                      "shareCount": 8542,
                      "subCount": 66888
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
            val remoteDataSource = NeteaseAlbumDetailRemoteDataSource(client)

            remoteDataSource.fetchAlbumContent(
                albumId = "32311",
                offset = 30,
                limit = 30
            )
            remoteDataSource.fetchAlbumDynamic("32311")

            assertEquals(
                listOf(
                    "/album?id=32311&offset=30&limit=30",
                    "/album/detail/dynamic?id=32311"
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

private class FakeAlbumDetailRemoteDataSource(
    private val contentPayload: JsonObject,
    private val dynamicPayload: JsonObject
) : AlbumDetailRemoteDataSource {
    override suspend fun fetchAlbumContent(
        albumId: String,
        offset: Int,
        limit: Int
    ): JsonObject = contentPayload

    override suspend fun fetchAlbumDynamic(albumId: String): JsonObject = dynamicPayload
}

private class AlbumResponseHttpServer(
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
