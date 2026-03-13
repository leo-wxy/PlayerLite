package com.wxy.playerlite.feature.artist

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

class ArtistDetailRepositoryTest {
    @Test
    fun fetchArtistDetail_shouldMapStableArtistSummary() = runBlocking {
        val repository = DefaultArtistDetailRepository(
            remoteDataSource = FakeArtistDetailRemoteDataSource(
                detailPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "data": {
                        "artist": {
                          "id": 6452,
                          "name": "周杰伦",
                          "cover": "http://example.com/cover.jpg",
                          "avatar": "http://example.com/avatar.jpg",
                          "alias": ["Jay Chou", "周董"],
                          "identities": ["作曲", "演唱"],
                          "briefDesc": "简介",
                          "musicSize": 568,
                          "albumSize": 44
                        }
                      }
                    }
                    """
                ),
                descPayload = jsonObject(
                    """
                    {
                      "briefDesc": "周杰伦，中国台湾流行乐男歌手。",
                      "introduction": [
                        {
                          "ti": "人物简介",
                          "txt": "周杰伦是华语流行音乐代表人物。"
                        },
                        {
                          "ti": "主要成就",
                          "txt": "获得多项音乐大奖。"
                        }
                      ]
                    }
                    """
                ),
                hotSongsPayload = jsonObject("""{"code":200,"songs":[]}""")
            )
        )

        val content = repository.fetchArtistDetail("6452")

        assertEquals("6452", content.artistId)
        assertEquals("周杰伦", content.name)
        assertEquals("http://example.com/avatar.jpg", content.avatarUrl)
        assertEquals("http://example.com/cover.jpg", content.coverUrl)
        assertEquals(listOf("Jay Chou", "周董"), content.aliases)
        assertEquals(listOf("作曲", "演唱"), content.identities)
        assertEquals("简介", content.briefDesc)
        assertEquals("周杰伦，中国台湾流行乐男歌手。", content.encyclopediaSummary)
        assertEquals(2, content.encyclopediaSections.size)
        assertEquals("人物简介", content.encyclopediaSections.first().title)
        assertEquals(568, content.musicCount)
        assertEquals(44, content.albumCount)
    }

    @Test
    fun fetchArtistHotSongs_shouldMapCompactTrackRows() = runBlocking {
        val repository = DefaultArtistDetailRepository(
            remoteDataSource = FakeArtistDetailRemoteDataSource(
                detailPayload = jsonObject("""{"code":200,"data":{"artist":{"id":6452,"name":"周杰伦"}}}"""),
                descPayload = jsonObject("""{"briefDesc":"","introduction":[]}"""),
                hotSongsPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "songs": [
                        {
                          "id": 210049,
                          "name": "布拉格广场",
                          "ar": [
                            { "name": "蔡依林" },
                            { "name": "周杰伦" }
                          ],
                          "al": {
                            "name": "看我72变",
                            "picUrl": "http://example.com/song.jpg"
                          },
                          "dt": 294600
                        }
                      ]
                    }
                    """
                )
            )
        )

        val songs = repository.fetchArtistHotSongs("6452")

        assertEquals(1, songs.size)
        assertEquals("210049", songs.single().trackId)
        assertEquals("布拉格广场", songs.single().title)
        assertEquals("蔡依林 / 周杰伦", songs.single().artistText)
        assertEquals("看我72变", songs.single().albumTitle)
        assertEquals("http://example.com/song.jpg", songs.single().coverUrl)
        assertEquals(294600L, songs.single().durationMs)
    }

    @Test
    fun fetchArtistEncyclopedia_shouldMapSummaryAndSections() = runBlocking {
        val repository = DefaultArtistDetailRepository(
            remoteDataSource = FakeArtistDetailRemoteDataSource(
                detailPayload = jsonObject("""{"code":200,"data":{"artist":{"id":6452,"name":"周杰伦"}}}"""),
                descPayload = jsonObject(
                    """
                    {
                      "briefDesc": "周杰伦（Jay Chou），中国台湾流行乐男歌手。",
                      "introduction": [
                        {
                          "ti": "主要成就",
                          "txt": "获得十五座金曲奖"
                        },
                        {
                          "ti": "代表作品",
                          "txt": "青花瓷、夜曲"
                        }
                      ]
                    }
                    """
                ),
                hotSongsPayload = jsonObject("""{"code":200,"songs":[]}""")
            )
        )

        val encyclopedia = repository.fetchArtistEncyclopedia("6452")

        assertEquals("周杰伦（Jay Chou），中国台湾流行乐男歌手。", encyclopedia.summary)
        assertEquals(2, encyclopedia.sections.size)
        assertEquals("主要成就", encyclopedia.sections.first().title)
        assertEquals("获得十五座金曲奖", encyclopedia.sections.first().body)
    }

    @Test
    fun fetchArtistRequests_shouldUseProtectedEndpointsAndForwardAuthHeaders() = runBlocking {
        val server = CapturingHttpServer(
            responses = mapOf(
                "/artist/detail" to """
                    {
                      "code": 200,
                      "data": {
                        "artist": {
                          "id": 6452,
                          "name": "周杰伦"
                        }
                      }
                    }
                """.trimIndent(),
                "/artist/desc" to """
                    {
                      "briefDesc": "周杰伦，中国台湾流行乐男歌手。",
                      "introduction": []
                    }
                """.trimIndent(),
                "/artist/top/song" to """
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
            val remoteDataSource = NeteaseArtistDetailRemoteDataSource(client)

            remoteDataSource.fetchArtistDetail("6452")
            remoteDataSource.fetchArtistDesc("6452")
            remoteDataSource.fetchArtistHotSongs("6452")

            assertEquals(
                listOf(
                    "/artist/detail?id=6452",
                    "/artist/desc?id=6452",
                    "/artist/top/song?id=6452"
                ),
                server.requestPaths
            )
            assertTrue(server.cookieHeaders.all { it.contains("MUSIC_U=token") })
            assertEquals(listOf("csrf-token", "csrf-token", "csrf-token"), server.csrfHeaders)
        } finally {
            server.close()
        }
    }
}

private class FakeArtistDetailRemoteDataSource(
    private val detailPayload: JsonObject,
    private val descPayload: JsonObject,
    private val hotSongsPayload: JsonObject
) : ArtistDetailRemoteDataSource {
    override suspend fun fetchArtistDetail(artistId: String): JsonObject = detailPayload

    override suspend fun fetchArtistDesc(artistId: String): JsonObject = descPayload

    override suspend fun fetchArtistHotSongs(artistId: String): JsonObject = hotSongsPayload
}

private class CapturingHttpServer(
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
            requestPaths += buildString {
                append(exchange.requestURI.path)
                exchange.requestURI.rawQuery?.let { query ->
                    append('?')
                    append(query)
                }
            }
            cookieHeaders += exchange.requestHeaders.getFirst("Cookie").orEmpty()
            csrfHeaders += exchange.requestHeaders.getFirst("X-CSRF-Token").orEmpty()
            val payload = responses.getValue(exchange.requestURI.path).toByteArray()
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
