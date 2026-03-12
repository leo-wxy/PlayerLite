package com.wxy.playerlite.feature.search

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRepositoryTest {
    @Test
    fun fetchHotKeywords_shouldReuseCacheWhenTtlNotExpired() = runBlocking {
        var nowMs = 1_000L
        val remoteDataSource = FakeSearchRemoteDataSource(
            hotPayloads = ArrayDeque<JsonObject>().apply {
                add(
                    jsonObject(
                        """
                        {
                          "code": 200,
                          "data": [
                            { "searchWord": "海屿你", "score": 110246, "iconType": 4, "iconUrl": "http://example.com/hot.png" },
                            { "searchWord": "小半", "score": 54245, "iconType": 0, "content": "" }
                          ]
                        }
                        """
                    )
                )
            }
        )
        val repository = DefaultSearchRepository(
            remoteDataSource = remoteDataSource,
            historyStorage = InMemorySearchHistoryStorage(),
            hotCacheTtlMs = 10_000L,
            nowMsProvider = { nowMs }
        )

        val first = repository.fetchHotKeywords()
        nowMs += 3_000L
        val second = repository.fetchHotKeywords()

        assertEquals(listOf("海屿你", "小半"), first.map { it.keyword })
        assertEquals(110246, first.first().score)
        assertEquals(4, first.first().iconType)
        assertEquals("http://example.com/hot.png", first.first().iconUrl)
        assertEquals(first, second)
        assertEquals(1, remoteDataSource.hotRequestCount)
    }

    @Test
    fun fetchHotKeywords_shouldRefreshWhenCacheExpired() = runBlocking {
        var nowMs = 1_000L
        val remoteDataSource = FakeSearchRemoteDataSource(
            hotPayloads = ArrayDeque<JsonObject>().apply {
                add(
                    jsonObject(
                        """
                        {
                          "code": 200,
                          "data": [{ "searchWord": "热搜 A", "score": 1, "iconType": 0 }]
                        }
                        """
                    )
                )
                add(
                    jsonObject(
                        """
                        {
                          "code": 200,
                          "data": [{ "searchWord": "热搜 B", "score": 2, "iconType": 0 }]
                        }
                        """
                    )
                )
            }
        )
        val repository = DefaultSearchRepository(
            remoteDataSource = remoteDataSource,
            historyStorage = InMemorySearchHistoryStorage(),
            hotCacheTtlMs = 1_000L,
            nowMsProvider = { nowMs }
        )

        val first = repository.fetchHotKeywords()
        nowMs += 2_000L
        val second = repository.fetchHotKeywords()

        assertEquals("热搜 A", first.single().keyword)
        assertEquals("热搜 B", second.single().keyword)
        assertEquals(2, remoteDataSource.hotRequestCount)
    }

    @Test
    fun fetchSuggestions_shouldMapDistinctKeywordsFromSuggestPayload() = runBlocking {
        val repository = DefaultSearchRepository(
            remoteDataSource = FakeSearchRemoteDataSource(
                suggestPayload = jsonObject(
                    """
                    {
                      "result": {
                        "songs": [
                          { "name": "周" },
                          { "name": "周末" }
                        ],
                        "artists": [
                          { "name": "周深" }
                        ],
                        "albums": [
                          { "name": "周杰伦作品集" }
                        ]
                      },
                      "code": 200
                    }
                    """
                )
            ),
            historyStorage = InMemorySearchHistoryStorage()
        )

        val suggestions = repository.fetchSuggestions("周")

        assertEquals(listOf("周", "周末", "周深", "周杰伦作品集"), suggestions.map { it.keyword })
    }

    @Test
    fun search_shouldMapSongResultsIntoTypedStableModels() = runBlocking {
        val repository = DefaultSearchRepository(
            remoteDataSource = FakeSearchRemoteDataSource(
                searchPayloads = mutableMapOf(
                    SearchResultType.SONG to jsonObject(
                        """
                        {
                          "code": 200,
                          "result": {
                            "songs": [
                              {
                                "id": 2124381474,
                                "name": "小美满",
                                "ar": [{ "name": "周深" }],
                                "al": {
                                  "name": "小美满",
                                  "picUrl": "http://example.com/cover.jpg"
                                }
                              }
                            ]
                          }
                        }
                        """
                    )
                )
            ),
            historyStorage = InMemorySearchHistoryStorage()
        )

        val results = repository.search("小美满", SearchResultType.SONG)

        assertEquals(1, results.size)
        val result = results.single() as SearchResultUiModel.Song
        assertEquals("2124381474", result.id)
        assertEquals("小美满", result.title)
        assertEquals("周深", result.artistText)
        assertEquals("小美满", result.albumTitle)
        assertEquals("http://example.com/cover.jpg", result.coverUrl)
        assertEquals(SearchRouteTarget.Song(songId = "2124381474"), result.routeTarget)
    }

    @Test
    fun search_shouldMapAlbumResultsIntoTypedStableModels() = runBlocking {
        val remoteDataSource = FakeSearchRemoteDataSource(
            searchPayloads = mutableMapOf(
                SearchResultType.ALBUM to jsonObject(
                    """
                    {
                      "code": 200,
                      "result": {
                        "albums": [
                          {
                            "id": 9981,
                            "name": "反方向的钟",
                            "artist": { "name": "周杰伦" },
                            "picUrl": "http://example.com/album.jpg",
                            "size": 12
                          }
                        ]
                      }
                    }
                    """
                )
            )
        )

        val repository = DefaultSearchRepository(
            remoteDataSource = remoteDataSource,
            historyStorage = InMemorySearchHistoryStorage()
        )

        val results = repository.search("周杰伦", SearchResultType.ALBUM)

        assertEquals(1, results.size)
        val result = results.single() as SearchResultUiModel.Album
        assertEquals("9981", result.id)
        assertEquals("反方向的钟", result.title)
        assertEquals("周杰伦", result.artistText)
        assertEquals(12, result.songCount)
        assertEquals(SearchRouteTarget.Album(albumId = "9981"), result.routeTarget)
        assertEquals(SearchResultType.ALBUM, remoteDataSource.searchRequests.single().type)
    }

    @Test
    fun search_shouldMapArtistAndPlaylistResultsIntoTypedStableModels() = runBlocking {
        val repository = DefaultSearchRepository(
            remoteDataSource = FakeSearchRemoteDataSource(
                searchPayloads = mutableMapOf(
                    SearchResultType.ARTIST to jsonObject(
                        """
                        {
                          "code": 200,
                          "result": {
                            "artists": [
                              {
                                "id": 135,
                                "name": "周杰伦",
                                "picUrl": "http://example.com/artist.jpg",
                                "albumSize": 16
                              }
                            ]
                          }
                        }
                        """
                    ),
                    SearchResultType.PLAYLIST to jsonObject(
                        """
                        {
                          "code": 200,
                          "result": {
                            "playlists": [
                              {
                                "id": 2001,
                                "name": "华语收藏",
                                "coverImgUrl": "http://example.com/playlist.jpg",
                                "trackCount": 24,
                                "creator": { "nickname": "小王" }
                              }
                            ]
                          }
                        }
                        """
                    )
                )
            ),
            historyStorage = InMemorySearchHistoryStorage()
        )

        val artistResults = repository.search("周杰伦", SearchResultType.ARTIST)
        val playlistResults = repository.search("收藏", SearchResultType.PLAYLIST)

        assertEquals(SearchRouteTarget.Artist(artistId = "135"), artistResults.single().routeTarget)
        assertEquals(SearchRouteTarget.Playlist(playlistId = "2001"), playlistResults.single().routeTarget)
    }

    @Test
    fun search_shouldReturnEmptyListWhenPayloadHasNoItemsForCurrentType() = runBlocking {
        val repository = DefaultSearchRepository(
            remoteDataSource = FakeSearchRemoteDataSource(
                searchPayloads = mutableMapOf(
                    SearchResultType.SONG to jsonObject(
                        """
                        {
                          "code": 200,
                          "result": {
                            "songs": []
                          }
                        }
                        """
                    )
                )
            ),
            historyStorage = InMemorySearchHistoryStorage()
        )

        val results = repository.search("不存在", SearchResultType.SONG)

        assertTrue(results.isEmpty())
    }

    @Test
    fun recordSearchHistory_shouldPersistDeduplicatedRecentKeywords() = runBlocking {
        val repository = DefaultSearchRepository(
            remoteDataSource = FakeSearchRemoteDataSource(),
            historyStorage = InMemorySearchHistoryStorage()
        )

        repository.recordSearchHistory("周深")
        repository.recordSearchHistory("小美满")
        repository.recordSearchHistory("周深")

        assertEquals(
            listOf("周深", "小美满"),
            repository.readSearchHistory()
        )
    }

    @Test
    fun search_shouldMapExtendedTypesIntoStableModels() = runBlocking {
        val repository = DefaultSearchRepository(
            remoteDataSource = FakeSearchRemoteDataSource(
                searchPayloads = mutableMapOf(
                    SearchResultType.USER to jsonObject(
                        """
                        {
                          "code": 200,
                          "result": {
                            "userprofiles": [
                              {
                                "userId": 77,
                                "nickname": "听歌的人",
                                "signature": "今天也在循环播放",
                                "avatarUrl": "http://example.com/user.jpg",
                                "playlistCount": 8
                              }
                            ]
                          }
                        }
                        """
                    ),
                    SearchResultType.MV to jsonObject(
                        """
                        {
                          "code": 200,
                          "result": {
                            "mvs": [
                              {
                                "id": 88,
                                "name": "一路向北",
                                "artistName": "周杰伦",
                                "cover": "http://example.com/mv.jpg",
                                "playCount": 1200
                              }
                            ]
                          }
                        }
                        """
                    ),
                    SearchResultType.LYRIC to jsonObject(
                        """
                        {
                          "code": 200,
                          "result": {
                            "songs": [
                              {
                                "id": 99,
                                "name": "晴天",
                                "artists": [{ "name": "周杰伦" }],
                                "lyric": "故事的小黄花..."
                              }
                            ]
                          }
                        }
                        """
                    ),
                    SearchResultType.RADIO to jsonObject(
                        """
                        {
                          "code": 200,
                          "result": {
                            "djRadios": [
                              {
                                "id": 66,
                                "name": "深夜电台",
                                "dj": { "nickname": "阿树" },
                                "programCount": 18,
                                "picUrl": "http://example.com/radio.jpg"
                              }
                            ]
                          }
                        }
                        """
                    ),
                    SearchResultType.VIDEO to jsonObject(
                        """
                        {
                          "code": 200,
                          "result": {
                            "videos": [
                              {
                                "vid": "video-1",
                                "title": "演唱会现场",
                                "creator": [{ "userName": "官方频道" }],
                                "playTime": 9800,
                                "coverUrl": "http://example.com/video.jpg"
                              }
                            ]
                          }
                        }
                        """
                    ),
                    SearchResultType.COMPOSITE to jsonObject(
                        """
                        {
                          "code": 200,
                          "result": {
                            "songs": [
                              {
                                "id": 101,
                                "name": "夜曲"
                              }
                            ],
                            "artists": [
                              {
                                "id": 102,
                                "name": "周杰伦",
                                "albumSize": 16
                              }
                            ]
                          }
                        }
                        """
                    ),
                    SearchResultType.VOICE to jsonObject(
                        """
                        {
                          "code": 200,
                          "result": {
                            "resources": [
                              {
                                "resourceId": 202,
                                "title": "睡前故事",
                                "creator": "声音主播",
                                "playCount": 667,
                                "coverUrl": "http://example.com/voice.jpg"
                              }
                            ]
                          }
                        }
                        """
                    )
                )
            ),
            historyStorage = InMemorySearchHistoryStorage()
        )

        val user = repository.search("听歌的人", SearchResultType.USER).single() as SearchResultUiModel.Generic
        val mv = repository.search("一路向北", SearchResultType.MV).single() as SearchResultUiModel.Generic
        val lyric = repository.search("晴天", SearchResultType.LYRIC).single() as SearchResultUiModel.Generic
        val radio = repository.search("深夜", SearchResultType.RADIO).single() as SearchResultUiModel.Generic
        val video = repository.search("现场", SearchResultType.VIDEO).single() as SearchResultUiModel.Generic
        val composite = repository.search("周杰伦", SearchResultType.COMPOSITE).first() as SearchResultUiModel.Generic
        val voice = repository.search("故事", SearchResultType.VOICE).single() as SearchResultUiModel.Generic

        assertEquals(SearchResultType.USER, user.resultType)
        assertEquals(SearchRouteTarget.Generic(SearchResultType.USER, "77"), user.routeTarget)
        assertEquals("一路向北", mv.title)
        assertEquals(SearchRouteTarget.Generic(SearchResultType.MV, "88"), mv.routeTarget)
        assertEquals("歌词 · 周杰伦", lyric.subtitle)
        assertEquals("电台 · 阿树", radio.subtitle)
        assertEquals("视频 · 官方频道", video.subtitle)
        assertEquals(SearchResultType.COMPOSITE, composite.resultType)
        assertEquals(SearchRouteTarget.Generic(SearchResultType.VOICE, "202"), voice.routeTarget)
    }
}

private class FakeSearchRemoteDataSource(
    private val hotPayloads: ArrayDeque<JsonObject> = ArrayDeque(),
    private val suggestPayload: JsonObject? = null,
    private val searchPayloads: MutableMap<SearchResultType, JsonObject> = linkedMapOf()
) : SearchRemoteDataSource {
    var hotRequestCount: Int = 0
    val searchRequests: MutableList<SearchRequest> = mutableListOf()

    override suspend fun fetchHotKeywords(): JsonObject {
        hotRequestCount += 1
        return requireNotNull(hotPayloads.removeFirstOrNull()) {
            "hot payload must be provided"
        }
    }

    override suspend fun fetchSuggestions(keyword: String): JsonObject {
        return requireNotNull(suggestPayload) { "suggest payload must be provided" }
    }

    override suspend fun search(keyword: String, type: SearchResultType): JsonObject {
        searchRequests += SearchRequest(keyword = keyword, type = type)
        return requireNotNull(searchPayloads[type]) { "search payload must be provided for $type" }
    }

    data class SearchRequest(
        val keyword: String,
        val type: SearchResultType
    )
}

private class InMemorySearchHistoryStorage : SearchHistoryStorage {
    private var keywords: List<String> = emptyList()

    override fun read(): List<String> = keywords

    override fun write(keywords: List<String>) {
        this.keywords = keywords
    }
}

private fun jsonObject(raw: String): JsonObject {
    return Json.parseToJsonElement(raw.trimIndent()).jsonObject
}
