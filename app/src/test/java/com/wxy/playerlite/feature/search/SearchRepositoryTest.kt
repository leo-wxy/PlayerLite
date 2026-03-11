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
    fun search_shouldMapSongsIntoStableResultItems() = runBlocking {
        val repository = DefaultSearchRepository(
            remoteDataSource = FakeSearchRemoteDataSource(
                searchPayload = jsonObject(
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
            ),
            historyStorage = InMemorySearchHistoryStorage()
        )

        val results = repository.search("小美满")

        assertEquals(1, results.size)
        assertEquals("2124381474", results.single().id)
        assertEquals("小美满", results.single().title)
        assertEquals("周深 · 小美满", results.single().subtitle)
        assertEquals("http://example.com/cover.jpg", results.single().coverUrl)
    }

    @Test
    fun search_shouldReturnEmptyListWhenPayloadHasNoSongs() = runBlocking {
        val repository = DefaultSearchRepository(
            remoteDataSource = FakeSearchRemoteDataSource(
                searchPayload = jsonObject(
                    """
                    {
                      "code": 200,
                      "result": {
                        "songs": []
                      }
                    }
                    """
                )
            ),
            historyStorage = InMemorySearchHistoryStorage()
        )

        val results = repository.search("不存在")

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
}

private class FakeSearchRemoteDataSource(
    private val hotPayloads: ArrayDeque<JsonObject> = ArrayDeque(),
    private val suggestPayload: JsonObject? = null,
    private val searchPayload: JsonObject? = null
) : SearchRemoteDataSource {
    var hotRequestCount: Int = 0

    override suspend fun fetchHotKeywords(): JsonObject {
        hotRequestCount += 1
        return requireNotNull(hotPayloads.removeFirstOrNull()) {
            "hot payload must be provided"
        }
    }

    override suspend fun fetchSuggestions(keyword: String): JsonObject {
        return requireNotNull(suggestPayload) { "suggest payload must be provided" }
    }

    override suspend fun search(keyword: String): JsonObject {
        return requireNotNull(searchPayload) { "search payload must be provided" }
    }
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
