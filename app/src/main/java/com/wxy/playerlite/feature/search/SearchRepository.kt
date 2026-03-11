package com.wxy.playerlite.feature.search

import android.content.SharedPreferences
import com.wxy.playerlite.network.core.JsonHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal interface SearchRepository {
    suspend fun fetchHotKeywords(): List<SearchHotKeywordUiModel>

    suspend fun fetchSuggestions(keyword: String): List<SearchSuggestionUiModel>

    suspend fun search(keyword: String): List<SearchResultItemUiModel>

    fun readSearchHistory(): List<String>

    suspend fun recordSearchHistory(keyword: String)
}

internal class DefaultSearchRepository(
    private val remoteDataSource: SearchRemoteDataSource,
    private val historyStorage: SearchHistoryStorage,
    private val hotCacheTtlMs: Long = DEFAULT_HOT_CACHE_TTL_MS,
    private val nowMsProvider: () -> Long = System::currentTimeMillis
) : SearchRepository {
    private var hotCache: CachedHotKeywords? = null

    override suspend fun fetchHotKeywords(): List<SearchHotKeywordUiModel> {
        val cached = hotCache
        val nowMs = nowMsProvider()
        if (cached != null && nowMs - cached.cachedAtMs <= hotCacheTtlMs) {
            return cached.items
        }
        val items = NeteaseSearchJsonMapper.parseHotKeywords(
            remoteDataSource.fetchHotKeywords()
        )
        hotCache = CachedHotKeywords(
            items = items,
            cachedAtMs = nowMs
        )
        return items
    }

    override suspend fun fetchSuggestions(keyword: String): List<SearchSuggestionUiModel> {
        return NeteaseSearchJsonMapper.parseSuggestions(
            remoteDataSource.fetchSuggestions(keyword.trim())
        )
    }

    override suspend fun search(keyword: String): List<SearchResultItemUiModel> {
        return NeteaseSearchJsonMapper.parseSearchResults(
            remoteDataSource.search(keyword.trim())
        )
    }

    override fun readSearchHistory(): List<String> = historyStorage.read()

    override suspend fun recordSearchHistory(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) {
            return
        }
        val updated = buildList {
            add(normalized)
            historyStorage.read().forEach { existing ->
                if (!existing.equals(normalized, ignoreCase = false)) {
                    add(existing)
                }
            }
        }.take(MAX_HISTORY_SIZE)
        historyStorage.write(updated)
    }

    private data class CachedHotKeywords(
        val items: List<SearchHotKeywordUiModel>,
        val cachedAtMs: Long
    )

    private companion object {
        const val DEFAULT_HOT_CACHE_TTL_MS = 10 * 60 * 1_000L
        const val MAX_HISTORY_SIZE = 10
    }
}

internal interface SearchHistoryStorage {
    fun read(): List<String>

    fun write(keywords: List<String>)
}

internal class SharedPreferencesSearchHistoryStorage(
    private val preferences: SharedPreferences
) : SearchHistoryStorage {
    override fun read(): List<String> {
        val raw = preferences.getString(KEY_HISTORY, null)?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                element.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    override fun write(keywords: List<String>) {
        val encoded = buildJsonArray {
            keywords.forEach { keyword ->
                add(JsonPrimitive(keyword))
            }
        }.toString()
        preferences.edit().putString(KEY_HISTORY, encoded).apply()
    }

    private companion object {
        private const val KEY_HISTORY = "search_history"
        private val json = Json {
            ignoreUnknownKeys = true
        }
    }
}

internal interface SearchRemoteDataSource {
    suspend fun fetchHotKeywords(): JsonObject

    suspend fun fetchSuggestions(keyword: String): JsonObject

    suspend fun search(keyword: String): JsonObject
}

internal class NeteaseSearchRemoteDataSource(
    private val httpClient: JsonHttpClient
) : SearchRemoteDataSource {
    override suspend fun fetchHotKeywords(): JsonObject {
        return httpClient.get(
            path = "/search/hot/detail",
            requiresAuth = true
        )
    }

    override suspend fun fetchSuggestions(keyword: String): JsonObject {
        return httpClient.get(
            path = "/search/suggest",
            queryParams = mapOf("keywords" to keyword),
            requiresAuth = true
        )
    }

    override suspend fun search(keyword: String): JsonObject {
        return httpClient.get(
            path = "/cloudsearch",
            queryParams = mapOf(
                "keywords" to keyword,
                "type" to "1"
            ),
            requiresAuth = true
        )
    }
}

internal object NeteaseSearchJsonMapper {
    fun parseHotKeywords(payload: JsonObject): List<SearchHotKeywordUiModel> {
        val detailData = payload.arrayValue("data")
        val legacyData = payload.objectValue("result").arrayValue("hots")
        return (if (detailData.isNotEmpty()) detailData else legacyData)
            .mapNotNull { element ->
                element.jsonObject.toHotKeywordItem()
            }
            .distinctBy { it.keyword }
    }

    fun parseSuggestions(payload: JsonObject): List<SearchSuggestionUiModel> {
        val result = payload.objectValue("result")
        return buildList {
            result.arrayValue("allMatch").forEach { element ->
                addIfNotBlank(element.jsonObject.stringValue("keyword"))
            }
            result.arrayValue("songs").forEach { element ->
                addIfNotBlank(element.jsonObject.stringValue("name"))
            }
            result.arrayValue("artists").forEach { element ->
                addIfNotBlank(element.jsonObject.stringValue("name"))
            }
            result.arrayValue("albums").forEach { element ->
                addIfNotBlank(element.jsonObject.stringValue("name"))
            }
        }.distinct()
            .map { keyword ->
                SearchSuggestionUiModel(keyword = keyword)
            }
    }

    fun parseSearchResults(payload: JsonObject): List<SearchResultItemUiModel> {
        return payload.objectValue("result")
            .arrayValue("songs")
            .mapNotNull { element ->
                element.jsonObject.toSearchResultItem()
            }
    }

    private fun JsonObject.toSearchResultItem(): SearchResultItemUiModel? {
        val id = stringValue("id") ?: return null
        val title = stringValue("name") ?: return null
        val artists = arrayValue("ar")
            .mapNotNull { element ->
                element.jsonObject.stringValue("name")
            }
            .ifEmpty {
                arrayValue("artists").mapNotNull { element ->
                    element.jsonObject.stringValue("name")
                }
            }
        val albumObject = objectValue("al").takeIf { it.isNotEmpty() }
            ?: objectValue("album")
        val albumTitle = albumObject.stringValue("name")
        val subtitle = buildList {
            val artistText = artists.joinToString(separator = " / ").takeIf { it.isNotBlank() }
            if (artistText != null) {
                add(artistText)
            }
            if (!albumTitle.isNullOrBlank()) {
                add(albumTitle)
            }
        }.joinToString(separator = " · ")
        return SearchResultItemUiModel(
            id = id,
            title = title,
            subtitle = subtitle,
            coverUrl = albumObject.stringValue("picUrl")
        )
    }

    private fun JsonObject.toHotKeywordItem(): SearchHotKeywordUiModel? {
        val keyword = stringValue("searchWord")
            ?: stringValue("first")
            ?: return null
        return SearchHotKeywordUiModel(
            keyword = keyword,
            score = intValue("score") ?: intValue("second") ?: 0,
            iconType = intValue("iconType") ?: 0,
            iconUrl = stringValue("iconUrl"),
            content = stringValue("content").orEmpty()
        )
    }
}

private fun JsonObject.objectValue(key: String): JsonObject {
    return (this[key] as? JsonObject) ?: emptyJsonObject
}

private fun JsonObject.arrayValue(key: String): JsonArray {
    return this[key]?.jsonArray ?: emptyJsonArray
}

private fun JsonObject.stringValue(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.intValue(key: String): Int? {
    return this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
}

private fun MutableList<String>.addIfNotBlank(value: String?) {
    if (!value.isNullOrBlank()) {
        add(value)
    }
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList<JsonElement>())
