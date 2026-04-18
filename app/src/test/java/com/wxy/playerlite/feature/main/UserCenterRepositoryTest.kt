package com.wxy.playerlite.feature.main

import com.sun.net.httpserver.HttpServer
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.core.recentplayback.LocalRecentPlaybackRecord
import com.wxy.playerlite.core.recentplayback.LocalRecentPlaybackStore
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
            ),
            localRecentPlaybackStore = emptyLocalRecentPlaybackStore()
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
                      "count": 1,
                      "data": [
                        {
                          "id": 126,
                          "title": "卧房撸歌",
                          "coverUrl": "http://example.com/topic.jpg",
                          "creator": {
                            "nickname": "云村播主"
                          },
                          "category": "音乐播客",
                          "subCount": 506
                        }
                      ]
                    }
                    """
                ),
                playlistPayload = jsonObject("""{"code":200,"playlist":[]}""")
            ),
            localRecentPlaybackStore = emptyLocalRecentPlaybackStore()
        )

        val items = repository.fetchFavoriteColumns()

        assertEquals(1, items.size)
        assertEquals("126", items.single().id)
        assertEquals("卧房撸歌", items.single().title)
        assertEquals("云村播主", items.single().subtitle)
        assertEquals("http://example.com/topic.jpg", items.single().imageUrl)
        assertEquals("506 人收藏", items.single().meta)
        assertEquals("音乐播客", items.single().badge)
        assertEquals(
            ContentEntryAction.Unsupported(UnsupportedColumnDetailMessage),
            items.single().action
        )
    }

    @Test
    fun fetchFavoriteMvs_shouldMapCompactCollectionCards() = runBlocking {
        val repository = DefaultUserCenterRepository(
            remoteDataSource = FakeUserCenterRemoteDataSource(
                artistPayload = jsonObject("""{"code":200,"data":[]}"""),
                columnPayload = jsonObject("""{"code":200,"data":[]}"""),
                playlistPayload = jsonObject("""{"code":200,"playlist":[]}"""),
                mvPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "count": 1,
                      "data": [
                        {
                          "id": 5436712,
                          "name": "MV A",
                          "cover": "http://example.com/mv.jpg",
                          "artistName": "Artist 1",
                          "playCount": 123456
                        }
                      ]
                    }
                    """
                )
            ),
            localRecentPlaybackStore = emptyLocalRecentPlaybackStore()
        )

        val items = repository.fetchFavoriteMvs()

        assertEquals(1, items.size)
        assertEquals("5436712", items.single().id)
        assertEquals("MV A", items.single().title)
        assertEquals("Artist 1", items.single().subtitle)
        assertEquals("http://example.com/mv.jpg", items.single().imageUrl)
        assertEquals("123456 播放", items.single().meta)
        assertEquals(
            ContentEntryAction.Unsupported(message = "当前版本暂不支持打开收藏 MV"),
            items.single().action
        )
    }

    @Test
    fun fetchCreatedPlaylists_shouldMapCompactCollectionCards() = runBlocking {
        val repository = DefaultUserCenterRepository(
            remoteDataSource = FakeUserCenterRemoteDataSource(
                artistPayload = jsonObject("""{"code":200,"data":[]}"""),
                columnPayload = jsonObject("""{"code":200,"djRadios":[]}"""),
                playlistPayload = jsonObject("""{"code":200,"playlist":[]}"""),
                createdPlaylistPayload = jsonObject(
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
                            "userId": 77462767,
                            "nickname": "Wucy002222"
                          }
                        }
                      ]
                    }
                    """
                )
            ),
            localRecentPlaybackStore = emptyLocalRecentPlaybackStore()
        )

        val items = repository.fetchCreatedPlaylists(77462767L)

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

    @Test
    fun fetchCollectedPlaylists_shouldMapCompactCollectionCards() = runBlocking {
        val repository = DefaultUserCenterRepository(
            remoteDataSource = FakeUserCenterRemoteDataSource(
                artistPayload = jsonObject("""{"code":200,"data":[]}"""),
                columnPayload = jsonObject("""{"code":200,"djRadios":[]}"""),
                playlistPayload = jsonObject("""{"code":200,"playlist":[]}"""),
                collectedPlaylistPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "playlist": [
                        {
                          "id": 2,
                          "name": "别人创建我收藏的歌单",
                          "coverImgUrl": "http://example.com/subscribed.jpg",
                          "trackCount": 34,
                          "creator": {
                            "userId": 42,
                            "nickname": "Other"
                          }
                        }
                      ]
                    }
                    """
                )
            ),
            localRecentPlaybackStore = emptyLocalRecentPlaybackStore()
        )

        val items = repository.fetchCollectedPlaylists(77462767L)

        assertEquals(1, items.size)
        assertEquals("2", items.single().id)
        assertEquals("别人创建我收藏的歌单", items.single().title)
        assertEquals("Other", items.single().subtitle)
        assertEquals("34 首歌曲", items.single().meta)
    }

    @Test
    fun parseLikedPlaylist_shouldFindLikedPlaylistFromUserPlaylists() {
        val payload = jsonObject(
            """
            {
              "code": 200,
              "playlist": [
                {
                  "id": 1,
                  "name": "普通歌单",
                  "coverImgUrl": "http://example.com/created.jpg",
                  "trackCount": 12,
                  "specialType": 0,
                  "creator": {
                    "userId": 77462767,
                    "nickname": "Codex"
                  }
                },
                {
                  "id": 2,
                  "name": "Codex喜欢的音乐",
                  "coverImgUrl": "http://example.com/liked.jpg",
                  "trackCount": 34,
                  "specialType": 0,
                  "creator": {
                    "userId": 77462767,
                    "nickname": "Codex"
                  }
                }
              ]
            }
            """
        )

        val liked = UserCenterJsonMapper.parseLikedPlaylist(
            payload = payload,
            userId = 77462767L
        )

        assertEquals("2", liked?.id)
        assertEquals("Codex喜欢的音乐", liked?.title)
    }

    @Test
    fun parseRecentSongs_shouldMapSongListFromNestedPayload() {
        val payload = jsonObject(
            """
            {
              "code": 200,
              "data": {
                "list": [
                  {
                    "data": {
                      "id": 10,
                      "name": "Song A",
                      "ar": [
                        { "id": 101, "name": "Artist 1" },
                        { "name": "Artist 2" }
                      ],
                      "al": {
                        "id": 202,
                        "name": "Album 1",
                        "picUrl": "http://example.com/cover.jpg"
                      }
                    }
                  }
                ]
              }
            }
            """
        )

        val items = UserCenterJsonMapper.parseRecentSongs(payload)

        assertEquals(1, items.size)
        assertEquals("10", items.single().id)
        assertEquals("Song A", items.single().title)
        assertEquals("Artist 1 / Artist 2", items.single().artistText)
        assertEquals("http://example.com/cover.jpg", items.single().imageUrl)
        assertEquals("Album 1", items.single().albumTitle)
        assertEquals("101", items.single().primaryArtistId)
        assertEquals("202", items.single().albumId)
        assertEquals(
            ContentEntryAction.OpenDetail(
                SearchRouteTarget.Song(songId = "10")
            ),
            items.single().detailAction
        )
    }

    @Test
    fun fetchLocalRecentPlaybackItems_shouldMapStoredRecords() = runBlocking {
        val repository = DefaultUserCenterRepository(
            remoteDataSource = FakeUserCenterRemoteDataSource(
                artistPayload = jsonObject("""{"code":200,"data":[]}"""),
                columnPayload = jsonObject("""{"code":200,"data":[]}"""),
                playlistPayload = jsonObject("""{"code":200,"playlist":[]}""")
            ),
            localRecentPlaybackStore = fixedLocalRecentPlaybackStore(
                LocalRecentPlaybackRecord(
                    recordKey = "online:song-1",
                    sourceType = PlaylistItemType.ONLINE,
                    songId = "song-1",
                    playbackUri = null,
                    title = "本地歌曲",
                    artistText = "本地歌手",
                    albumTitle = "本地专辑",
                    primaryArtistId = "artist-1",
                    albumId = "album-1",
                    coverUrl = "http://example.com/local.jpg",
                    durationMs = 215_000L,
                    playedAtMs = 1234L
                )
            )
        )

        val items = repository.fetchLocalRecentPlaybackItems(limit = 100)

        assertEquals(1, items.size)
        assertEquals("online:song-1", items.single().recordKey)
        assertEquals("song-1", items.single().songId)
        assertEquals(PlaylistItemType.ONLINE, items.single().sourceType)
        assertEquals("本地歌曲", items.single().title)
        assertEquals("本地歌手", items.single().artistText)
        assertEquals("本地专辑", items.single().albumTitle)
        assertEquals("http://example.com/local.jpg", items.single().imageUrl)
        assertEquals(215_000L, items.single().durationMs)
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
                "/topic/sublist" to """{"code":200,"data":[]}""",
                "/mv/sublist" to """{"code":200,"data":[]}""",
                "/user/playlist/create" to """{"code":200,"playlist":[]}""",
                "/user/playlist/collect" to """{"code":200,"playlist":[]}""",
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
            remoteDataSource.fetchFavoriteMvs()
            remoteDataSource.fetchCreatedPlaylists(77462767L)
            remoteDataSource.fetchCollectedPlaylists(77462767L)
            remoteDataSource.fetchUserPlaylists(77462767L)

            assertEquals(
                listOf(
                    "/artist/sublist",
                    "/topic/sublist",
                    "/mv/sublist",
                    "/user/playlist/create?uid=77462767",
                    "/user/playlist/collect?uid=77462767",
                    "/user/playlist?uid=77462767"
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

    @Test
    fun fetchRecentRequests_shouldUseProtectedEndpointsAndForwardAuthHeaders() = runBlocking {
        val server = UserCenterHttpServer(
            responses = mapOf(
                "/record/recent/song" to """{"code":200,"data":{"list":[]}}""",
                "/record/recent/video" to """{"code":200,"data":{"list":[]}}""",
                "/record/recent/voice" to """{"code":200,"data":{"list":[]}}""",
                "/record/recent/playlist" to """{"code":200,"data":{"list":[]}}""",
                "/record/recent/album" to """{"code":200,"data":{"list":[]}}""",
                "/record/recent/dj" to """{"code":200,"data":{"list":[]}}"""
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

            remoteDataSource.fetchRecentSongs(limit = 1)
            remoteDataSource.fetchRecentVideos(limit = 1)
            remoteDataSource.fetchRecentVoices(limit = 1)
            remoteDataSource.fetchRecentPlaylists(limit = 1)
            remoteDataSource.fetchRecentAlbums(limit = 1)
            remoteDataSource.fetchRecentDjRadios(limit = 1)

            assertEquals(
                listOf(
                    "/record/recent/song?limit=1",
                    "/record/recent/video?limit=1",
                    "/record/recent/voice?limit=1",
                    "/record/recent/playlist?limit=1",
                    "/record/recent/album?limit=1",
                    "/record/recent/dj?limit=1"
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

private class FakeUserCenterRemoteDataSource(
    private val artistPayload: JsonObject,
    private val columnPayload: JsonObject,
    private val playlistPayload: JsonObject,
    private val createdPlaylistPayload: JsonObject = playlistPayload,
    private val collectedPlaylistPayload: JsonObject = jsonObject("""{"code":200,"playlist":[]}"""),
    private val mvPayload: JsonObject = jsonObject("""{"code":200,"data":[]}"""),
    private val recentSongsPayload: JsonObject = jsonObject("""{"code":200,"data":{"list":[]}}"""),
    private val recentVideosPayload: JsonObject = jsonObject("""{"code":200,"data":{"list":[]}}"""),
    private val recentVoicesPayload: JsonObject = jsonObject("""{"code":200,"data":{"list":[]}}"""),
    private val recentPlaylistsPayload: JsonObject = jsonObject("""{"code":200,"data":{"list":[]}}"""),
    private val recentAlbumsPayload: JsonObject = jsonObject("""{"code":200,"data":{"list":[]}}"""),
    private val recentDjRadiosPayload: JsonObject = jsonObject("""{"code":200,"data":{"list":[]}}""")
) : UserCenterRemoteDataSource {
    override suspend fun fetchFavoriteArtists(): JsonObject = artistPayload

    override suspend fun fetchFavoriteColumns(): JsonObject = columnPayload

    override suspend fun fetchFavoriteMvs(): JsonObject = mvPayload

    override suspend fun fetchCreatedPlaylists(userId: Long): JsonObject = createdPlaylistPayload

    override suspend fun fetchCollectedPlaylists(userId: Long): JsonObject = collectedPlaylistPayload

    override suspend fun fetchUserPlaylists(userId: Long): JsonObject = playlistPayload

    override suspend fun fetchRecentSongs(limit: Int): JsonObject = recentSongsPayload

    override suspend fun fetchRecentVideos(limit: Int): JsonObject = recentVideosPayload

    override suspend fun fetchRecentVoices(limit: Int): JsonObject = recentVoicesPayload

    override suspend fun fetchRecentPlaylists(limit: Int): JsonObject = recentPlaylistsPayload

    override suspend fun fetchRecentAlbums(limit: Int): JsonObject = recentAlbumsPayload

    override suspend fun fetchRecentDjRadios(limit: Int): JsonObject = recentDjRadiosPayload
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

private fun emptyLocalRecentPlaybackStore(): LocalRecentPlaybackStore {
    return fixedLocalRecentPlaybackStore()
}

private fun fixedLocalRecentPlaybackStore(
    vararg records: LocalRecentPlaybackRecord
): LocalRecentPlaybackStore {
    return object : LocalRecentPlaybackStore {
        override fun read(limit: Int): List<LocalRecentPlaybackRecord> {
            return records.take(limit)
        }

        override fun record(record: LocalRecentPlaybackRecord) = Unit

        override fun remove(recordKey: String): Boolean = false
    }
}
