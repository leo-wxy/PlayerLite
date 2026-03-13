package com.wxy.playerlite.feature.main

import com.sun.net.httpserver.HttpServer
import com.wxy.playerlite.network.core.AuthHeaderProvider
import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.feature.search.SearchRouteTarget
import com.wxy.playerlite.user.UserSessionInvalidException
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserCenterRepositoryTest {
    @Test
    fun fetchFavoriteArtists_shouldMapCompactCollectionCards() = runBlocking {
        val repository = DefaultUserCenterRepository(
            remoteDataSource = FakeUserCenterRemoteDataSource(
                artistPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "data": [
                        {
                          "id": 35796201,
                          "name": "yama",
                          "picUrl": "http://example.com/artist.jpg",
                          "alias": ["真昼"],
                          "albumSize": 68,
                          "mvSize": 7
                        }
                      ]
                    }
                    """
                ),
                columnPayload = jsonObject("""{"code":200,"djRadios":[]}"""),
                playlistPayload = jsonObject("""{"code":200,"playlist":[]}""")
            )
        )

        val items = repository.fetchFavoriteArtists()

        assertEquals(1, items.size)
        assertEquals("35796201", items.single().id)
        assertEquals("yama", items.single().title)
        assertEquals("真昼", items.single().subtitle)
        assertEquals("http://example.com/artist.jpg", items.single().imageUrl)
        assertEquals("68 张专辑", items.single().meta)
        assertEquals(
            ContentEntryAction.OpenDetail(
                SearchRouteTarget.Artist(artistId = "35796201")
            ),
            items.single().action
        )
    }

    @Test
    fun fetchFavoriteColumns_shouldMapCompactCollectionCards() = runBlocking {
        val repository = DefaultUserCenterRepository(
            remoteDataSource = FakeUserCenterRemoteDataSource(
                artistPayload = jsonObject("""{"code":200,"data":[]}"""),
                columnPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "djRadios": [
                        {
                          "id": 126,
                          "name": "卧房撸歌",
                          "picUrl": "http://example.com/dj.jpg",
                          "programCount": 506,
                          "category": "音乐播客",
                          "dj": {
                            "nickname": "云村播主"
                          }
                        }
                      ]
                    }
                    """
                ),
                playlistPayload = jsonObject("""{"code":200,"playlist":[]}""")
            )
        )

        val items = repository.fetchFavoriteColumns()

        assertEquals(1, items.size)
        assertEquals("126", items.single().id)
        assertEquals("卧房撸歌", items.single().title)
        assertEquals("云村播主", items.single().subtitle)
        assertEquals("http://example.com/dj.jpg", items.single().imageUrl)
        assertEquals("506 期节目", items.single().meta)
        assertEquals("音乐播客", items.single().badge)
        assertEquals(
            ContentEntryAction.Unsupported(UnsupportedColumnDetailMessage),
            items.single().action
        )
    }

    @Test
    fun fetchUserPlaylists_shouldMapCompactCollectionCards() = runBlocking {
        val repository = DefaultUserCenterRepository(
            remoteDataSource = FakeUserCenterRemoteDataSource(
                artistPayload = jsonObject("""{"code":200,"data":[]}"""),
                columnPayload = jsonObject("""{"code":200,"djRadios":[]}"""),
                playlistPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "playlist": [
                        {
                          "id": 85243793,
                          "name": "Wucy002222喜欢的音乐",
                          "coverImgUrl": "http://example.com/playlist.jpg",
                          "trackCount": 10139,
                          "creator": {
                            "nickname": "Wucy002222"
                          }
                        }
                      ]
                    }
                    """
                )
            )
        )

        val items = repository.fetchUserPlaylists(77462767L)

        assertEquals(1, items.size)
        assertEquals("85243793", items.single().id)
        assertEquals("Wucy002222喜欢的音乐", items.single().title)
        assertEquals("Wucy002222", items.single().subtitle)
        assertEquals("http://example.com/playlist.jpg", items.single().imageUrl)
        assertEquals("10139 首歌曲", items.single().meta)
        assertEquals(
            ContentEntryAction.OpenDetail(
                SearchRouteTarget.Playlist(playlistId = "85243793")
            ),
            items.single().action
        )
    }

    @Test(expected = UserSessionInvalidException::class)
    fun fetchFavoriteArtists_shouldThrowSessionInvalidWhenResponseCodeIs301() {
        runBlocking {
            val server = UserCenterHttpServer(
                responses = mapOf(
                    "/artist/sublist" to """{"code":301,"message":"需要登录"}"""
                )
            )
            server.start()
            try {
                val remoteDataSource = NeteaseUserCenterRemoteDataSource(
                    httpClient = JsonHttpClient(
                        baseUrl = server.baseUrl,
                        authHeaderProvider = AuthHeaderProvider {
                            mapOf("Cookie" to "MUSIC_U=token")
                        }
                    )
                )

                remoteDataSource.fetchFavoriteArtists()
            } finally {
                server.close()
            }
        }
    }

    @Test
    fun remoteDataSourceRequests_shouldUseProtectedEndpointsAndForwardAuthHeaders() = runBlocking {
        val server = UserCenterHttpServer(
            responses = mapOf(
                "/artist/sublist" to """{"code":200,"data":[]}""",
                "/dj/sublist" to """{"code":200,"djRadios":[]}""",
                "/user/playlist" to """{"code":200,"playlist":[]}"""
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
            val remoteDataSource = NeteaseUserCenterRemoteDataSource(client)

            remoteDataSource.fetchFavoriteArtists()
            remoteDataSource.fetchFavoriteColumns()
            remoteDataSource.fetchUserPlaylists(77462767L)

            assertEquals(
                listOf(
                    "/artist/sublist",
                    "/dj/sublist",
                    "/user/playlist?uid=77462767"
                ),
                server.requestPaths
            )
            assertTrue(server.cookieHeaders.all { it.contains("MUSIC_U=token") })
            assertEquals(
                listOf("csrf-token", "csrf-token", "csrf-token"),
                server.csrfHeaders
            )
        } finally {
            server.close()
        }
    }
}

private class FakeUserCenterRemoteDataSource(
    private val artistPayload: JsonObject,
    private val columnPayload: JsonObject,
    private val playlistPayload: JsonObject
) : UserCenterRemoteDataSource {
    override suspend fun fetchFavoriteArtists(): JsonObject = artistPayload

    override suspend fun fetchFavoriteColumns(): JsonObject = columnPayload

    override suspend fun fetchUserPlaylists(userId: Long): JsonObject = playlistPayload
}

private class UserCenterHttpServer(
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
