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
import org.junit.Assert.assertFalse
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
                encyclopediaPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "data": {
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
                    }
                    """
                ),
                dynamicPayload = Result.success(
                    jsonObject(
                        """
                        {
                          "followed": true,
                          "concert": {
                            "simpleConcert": null,
                            "onlineCount": 0,
                            "view": true
                          },
                          "code": 200,
                          "videoNum": [
                            { "cat": 0, "num": 12 },
                            { "cat": 1, "num": 27 }
                          ],
                          "rcmdResource": null
                        }
                        """
                    )
                ),
                followCountPayload = Result.success(
                    jsonObject(
                        """
                        {
                          "code": 200,
                          "data": {
                            "fansCnt": 1558
                          }
                        }
                        """
                    )
                ),
                hotSongsPayload = jsonObject("""{"code":200,"songs":[]}"""),
                albumsPayload = jsonObject("""{"code":200,"hotAlbums":[],"more":false}""")
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
        assertEquals(true, content.isFollowed)
        assertEquals(27, content.videoCount)
        assertEquals(1558L, content.fansCount)
    }

    @Test
    fun fetchArtistDetail_shouldFallbackFollowedStateFromFollowCountWhenDynamicIsMissing() = runBlocking {
        val repository = DefaultArtistDetailRepository(
            remoteDataSource = FakeArtistDetailRemoteDataSource(
                detailPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "data": {
                        "artist": {
                          "id": 2116,
                          "name": "林俊杰",
                          "musicSize": 233,
                          "albumSize": 19
                        }
                      }
                    }
                    """
                ),
                encyclopediaPayload = jsonObject("""{"code":200,"data":{"briefDesc":"","introduction":[]}}"""),
                dynamicPayload = Result.success(
                    jsonObject(
                        """
                        {
                          "code": 200,
                          "videoNum": [
                            { "cat": 0, "num": 8 },
                            { "cat": 1, "num": 18 }
                          ]
                        }
                        """
                    )
                ),
                followCountPayload = Result.success(
                    jsonObject(
                        """
                        {
                          "code": 200,
                          "message": "success",
                          "data": {
                            "isFollow": false,
                            "fansCnt": 13267753,
                            "followCnt": 0,
                            "followDay": "",
                            "followDayCnt": 0,
                            "follow": false
                          }
                        }
                        """
                    )
                ),
                hotSongsPayload = jsonObject("""{"code":200,"songs":[]}"""),
                albumsPayload = jsonObject("""{"code":200,"hotAlbums":[],"more":false}""")
            )
        )

        val content = repository.fetchArtistDetail("2116")

        assertEquals("2116", content.artistId)
        assertEquals(false, content.isFollowed)
        assertEquals(18, content.videoCount)
        assertEquals(13267753L, content.fansCount)
    }

    @Test
    fun fetchArtistDetail_shouldKeepBaseHeaderWhenDynamicEnhancementsFail() = runBlocking {
        val repository = DefaultArtistDetailRepository(
            remoteDataSource = FakeArtistDetailRemoteDataSource(
                detailPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "data": {
                        "artist": {
                          "id": 15396,
                          "name": "田馥甄",
                          "musicSize": 120,
                          "albumSize": 14
                        }
                      }
                    }
                    """
                ),
                encyclopediaPayload = jsonObject("""{"code":200,"data":{"briefDesc":"","introduction":[]}}"""),
                dynamicPayload = Result.failure(IllegalStateException("dynamic failed")),
                followCountPayload = Result.failure(IllegalStateException("follow count failed")),
                hotSongsPayload = jsonObject("""{"code":200,"songs":[]}"""),
                albumsPayload = jsonObject("""{"code":200,"hotAlbums":[],"more":false}""")
            )
        )

        val content = repository.fetchArtistDetail("15396")

        assertEquals("15396", content.artistId)
        assertEquals("田馥甄", content.name)
        assertEquals(null, content.isFollowed)
        assertEquals(0, content.videoCount)
        assertEquals(0L, content.fansCount)
    }

    @Test
    fun fetchArtistHotSongs_shouldMapCompactTrackRows() = runBlocking {
        val repository = DefaultArtistDetailRepository(
            remoteDataSource = FakeArtistDetailRemoteDataSource(
                detailPayload = jsonObject("""{"code":200,"data":{"artist":{"id":6452,"name":"周杰伦"}}}"""),
                encyclopediaPayload = jsonObject("""{"code":200,"data":{"briefDesc":"","introduction":[]}}"""),
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
                ),
                albumsPayload = jsonObject("""{"code":200,"hotAlbums":[],"more":false}""")
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
    fun fetchArtistEncyclopedia_shouldMapSummaryAndSectionsFromUgcPayload() = runBlocking {
        val repository = DefaultArtistDetailRepository(
            remoteDataSource = FakeArtistDetailRemoteDataSource(
                detailPayload = jsonObject("""{"code":200,"data":{"artist":{"id":6452,"name":"周杰伦"}}}"""),
                encyclopediaPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "data": {
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
                    }
                    """
                ),
                hotSongsPayload = jsonObject("""{"code":200,"songs":[]}"""),
                albumsPayload = jsonObject("""{"code":200,"hotAlbums":[],"more":false}""")
            )
        )

        val encyclopedia = repository.fetchArtistEncyclopedia("6452")

        assertEquals("周杰伦（Jay Chou），中国台湾流行乐男歌手。", encyclopedia.summary)
        assertEquals(2, encyclopedia.sections.size)
        assertEquals("主要成就", encyclopedia.sections.first().title)
        assertEquals("获得十五座金曲奖", encyclopedia.sections.first().body)
    }

    @Test
    fun fetchArtistEncyclopedia_shouldReturnEmptyContentWhenLoginIsRequired() = runBlocking {
        val repository = DefaultArtistDetailRepository(
            remoteDataSource = FakeArtistDetailRemoteDataSource(
                detailPayload = jsonObject("""{"code":200,"data":{"artist":{"id":6452,"name":"周杰伦"}}}"""),
                encyclopediaPayload = jsonObject(
                    """{"code":301,"message":"系统错误","data":null,"msg":"需要登录"}"""
                ),
                hotSongsPayload = jsonObject("""{"code":200,"songs":[]}"""),
                albumsPayload = jsonObject("""{"code":200,"hotAlbums":[],"more":false}""")
            )
        )

        val encyclopedia = repository.fetchArtistEncyclopedia("6452")

        assertEquals("", encyclopedia.summary)
        assertTrue(encyclopedia.sections.isEmpty())
    }

    @Test
    fun fetchArtistAlbums_shouldMapPagedAlbumRowsAndHasMore() = runBlocking {
        val repository = DefaultArtistDetailRepository(
            remoteDataSource = FakeArtistDetailRemoteDataSource(
                detailPayload = jsonObject("""{"code":200,"data":{"artist":{"id":6452,"name":"周杰伦"}}}"""),
                encyclopediaPayload = jsonObject("""{"code":200,"data":{"briefDesc":"","introduction":[]}}"""),
                hotSongsPayload = jsonObject("""{"code":200,"songs":[]}"""),
                albumsPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "more": true,
                      "hotAlbums": [
                        {
                          "id": 274336916,
                          "name": "即兴曲",
                          "type": "Single",
                          "size": 1,
                          "publishTime": 1749139200000,
                          "picUrl": "http://example.com/album-1.jpg",
                          "artist": {
                            "name": "周杰伦"
                          }
                        },
                        {
                          "id": 259316984,
                          "name": "Six Degrees",
                          "type": "Single",
                          "size": 1,
                          "publishTime": 1736438400000,
                          "picUrl": "http://example.com/album-2.jpg",
                          "artist": {
                            "name": "派伟俊"
                          }
                        }
                      ]
                    }
                    """
                )
            )
        )

        val page = repository.fetchArtistAlbums(
            artistId = "6452",
            offset = 30,
            limit = 30
        )

        assertTrue(page.hasMore)
        assertEquals(2, page.items.size)
        assertEquals("274336916", page.items.first().albumId)
        assertEquals("即兴曲", page.items.first().title)
        assertEquals("周杰伦", page.items.first().artistText)
        assertEquals("Single", page.items.first().type)
        assertEquals(1, page.items.first().trackCount)
        assertEquals("http://example.com/album-1.jpg", page.items.first().coverUrl)
        assertEquals("2025-06-06", page.items.first().publishTimeText)
        assertFalse(page.items.first().showYearOnly)
    }

    @Test
    fun artistAlbumPage_append_shouldKeepExistingItemsAndAdoptNextPageHasMore() {
        val firstPage = ArtistAlbumPage(
            items = listOf(
                ArtistAlbumRow(
                    albumId = "1",
                    title = "第一页专辑",
                    artistText = "周杰伦",
                    coverUrl = null,
                    trackCount = 10,
                    type = "Album",
                    publishTimeText = "2024-01-01"
                )
            ),
            hasMore = true
        )
        val nextPage = ArtistAlbumPage(
            items = listOf(
                ArtistAlbumRow(
                    albumId = "2",
                    title = "第二页专辑",
                    artistText = "周杰伦",
                    coverUrl = null,
                    trackCount = 8,
                    type = "EP",
                    publishTimeText = "2024-02-01"
                )
            ),
            hasMore = false
        )

        val merged = firstPage.append(nextPage)

        assertEquals(listOf("1", "2"), merged.items.map { it.albumId })
        assertFalse(merged.hasMore)
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
                "/ugc/artist/get" to """
                    {
                      "code": 200,
                      "data": {
                        "briefDesc": "周杰伦，中国台湾流行乐男歌手。",
                        "introduction": []
                      }
                    }
                """.trimIndent(),
                "/artist/detail/dynamic" to """
                    {
                      "followed": false,
                      "concert": {
                        "simpleConcert": null,
                        "onlineCount": 0,
                        "view": true
                      },
                      "code": 200,
                      "videoNum": [
                        { "cat": 0, "num": 2 },
                        { "cat": 1, "num": 3 }
                      ],
                      "rcmdResource": null
                    }
                """.trimIndent(),
                "/artist/follow/count" to """
                    {
                      "code": 200,
                      "data": {
                        "fansCnt": 987654
                      }
                    }
                """.trimIndent(),
                "/artist/top/song" to """
                    {
                      "code": 200,
                      "songs": []
                    }
                """.trimIndent(),
                "/artist/album" to """
                    {
                      "code": 200,
                      "hotAlbums": [],
                      "more": false
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
            remoteDataSource.fetchArtistEncyclopedia("6452")
            remoteDataSource.fetchArtistDynamic("6452")
            remoteDataSource.fetchArtistFollowCount("6452")
            remoteDataSource.fetchArtistHotSongs("6452")
            remoteDataSource.fetchArtistAlbums(
                artistId = "6452",
                offset = 30,
                limit = 30
            )

            assertEquals(
                listOf(
                    "/artist/detail?id=6452",
                    "/ugc/artist/get?id=6452",
                    "/artist/detail/dynamic?id=6452",
                    "/artist/follow/count?id=6452",
                    "/artist/top/song?id=6452",
                    "/artist/album?id=6452&offset=30&limit=30"
                ),
                server.requestPaths
            )
            assertTrue(server.cookieHeaders.all { it.contains("MUSIC_U=token") })
            assertEquals(
                listOf(
                    "csrf-token",
                    "csrf-token",
                    "csrf-token",
                    "csrf-token",
                    "csrf-token",
                    "csrf-token"
                ),
                server.csrfHeaders
            )
        } finally {
            server.close()
        }
    }
}

private class FakeArtistDetailRemoteDataSource(
    private val detailPayload: JsonObject,
    private val encyclopediaPayload: JsonObject,
    private val dynamicPayload: Result<JsonObject> = Result.success(jsonObject("""{}""")),
    private val followCountPayload: Result<JsonObject> = Result.success(jsonObject("""{}""")),
    private val hotSongsPayload: JsonObject,
    private val albumsPayload: JsonObject
) : ArtistDetailRemoteDataSource {
    override suspend fun fetchArtistDetail(artistId: String): JsonObject = detailPayload

    override suspend fun fetchArtistEncyclopedia(artistId: String): JsonObject = encyclopediaPayload

    override suspend fun fetchArtistDynamic(artistId: String): JsonObject = dynamicPayload.getOrThrow()

    override suspend fun fetchArtistFollowCount(artistId: String): JsonObject {
        return followCountPayload.getOrThrow()
    }

    override suspend fun fetchArtistHotSongs(artistId: String): JsonObject = hotSongsPayload

    override suspend fun fetchArtistAlbums(
        artistId: String,
        offset: Int,
        limit: Int
    ): JsonObject = albumsPayload
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
