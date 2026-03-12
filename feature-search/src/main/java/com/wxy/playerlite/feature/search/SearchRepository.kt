package com.wxy.playerlite.feature.search

import android.content.SharedPreferences
import com.wxy.playerlite.network.core.JsonHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface SearchRepository {
    suspend fun fetchHotKeywords(): List<SearchHotKeywordUiModel>

    suspend fun fetchSuggestions(keyword: String): List<SearchSuggestionUiModel>

    suspend fun search(keyword: String, type: SearchResultType): List<SearchResultUiModel>

    fun readSearchHistory(): List<String>

    suspend fun recordSearchHistory(keyword: String)
}

object SearchFeatureServiceFactory {
    fun createRepository(
        httpClient: JsonHttpClient,
        historyPreferences: SharedPreferences
    ): SearchRepository {
        return DefaultSearchRepository(
            remoteDataSource = NeteaseSearchRemoteDataSource(httpClient),
            historyStorage = SharedPreferencesSearchHistoryStorage(historyPreferences)
        )
    }
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

    override suspend fun search(keyword: String, type: SearchResultType): List<SearchResultUiModel> {
        return NeteaseSearchJsonMapper.parseSearchResults(
            payload = remoteDataSource.search(keyword.trim(), type),
            type = type
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

    suspend fun search(keyword: String, type: SearchResultType): JsonObject
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

    override suspend fun search(keyword: String, type: SearchResultType): JsonObject {
        return httpClient.get(
            path = "/cloudsearch",
            queryParams = mapOf(
                "keywords" to keyword,
                "type" to type.typeCode.toString()
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

    fun parseSearchResults(
        payload: JsonObject,
        type: SearchResultType
    ): List<SearchResultUiModel> {
        val result = payload.objectValue("result")
        return SearchTypePayloadMapper.itemsFor(
            result = result,
            type = type
        ).mapNotNull { element ->
            SearchItemMapper.map(
                item = element.jsonObject,
                type = type
            )
        }
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

internal object SearchTypePayloadMapper {
    fun itemsFor(
        result: JsonObject,
        type: SearchResultType
    ): JsonArray {
        return when (type) {
            SearchResultType.COMPOSITE -> buildJsonArray {
                listOf(
                    "songs",
                    "albums",
                    "artists",
                    "playlists",
                    "userprofiles",
                    "userProfiles",
                    "mvs",
                    "djRadios",
                    "djradios",
                    "videos"
                ).forEach { payloadKey ->
                    result.arrayValue(payloadKey).forEach(::add)
                }
            }

            else -> {
                type.payloadKeys
                    .asSequence()
                    .map { payloadKey -> result.arrayValue(payloadKey) }
                    .firstOrNull { payload -> payload.isNotEmpty() }
                    ?: emptyJsonArray
            }
        }
    }
}

internal object SearchItemMapper {
    fun map(
        item: JsonObject,
        type: SearchResultType
    ): SearchResultUiModel? {
        return when (type) {
            SearchResultType.SONG -> item.toSongItem()
            SearchResultType.ALBUM -> item.toAlbumItem()
            SearchResultType.ARTIST -> item.toArtistItem()
            SearchResultType.PLAYLIST -> item.toPlaylistItem()
            SearchResultType.USER -> item.toUserItem()
            SearchResultType.MV -> item.toMvItem()
            SearchResultType.LYRIC -> item.toLyricItem()
            SearchResultType.RADIO -> item.toRadioItem()
            SearchResultType.VIDEO -> item.toVideoItem()
            SearchResultType.COMPOSITE -> item.toCompositeItem()
            SearchResultType.VOICE -> item.toVoiceItem()
        }
    }

    private fun JsonObject.toSongItem(): SearchResultUiModel.Song? {
        val id = stringValue("id") ?: return null
        val title = stringValue("name") ?: return null
        val artistText = arrayValue("ar")
            .mapNotNull { element -> element.jsonObject.stringValue("name") }
            .ifEmpty {
                arrayValue("artists").mapNotNull { element ->
                    element.jsonObject.stringValue("name")
                }
            }.joinToString(separator = " / ")
        val albumObject = objectValue("al").takeIf { it.isNotEmpty() }
            ?: objectValue("album")
        val albumTitle = albumObject.stringValue("name").orEmpty()
        return SearchResultUiModel.Song(
            id = id,
            title = title,
            artistText = artistText,
            albumTitle = albumTitle,
            coverUrl = albumObject.stringValue("picUrl"),
            routeTarget = SearchResultRouteMapper.song(id)
        )
    }

    private fun JsonObject.toAlbumItem(): SearchResultUiModel.Album? {
        val id = stringValue("id") ?: return null
        val title = stringValue("name") ?: return null
        val artistText = objectValue("artist").stringValue("name")
            ?: arrayValue("artists").firstOrNull()?.jsonObject?.stringValue("name")
            ?: ""
        return SearchResultUiModel.Album(
            id = id,
            title = title,
            artistText = artistText,
            songCount = intValue("size"),
            coverUrl = stringValue("picUrl"),
            routeTarget = SearchResultRouteMapper.album(id)
        )
    }

    private fun JsonObject.toArtistItem(): SearchResultUiModel.Artist? {
        val id = stringValue("id") ?: return null
        val title = stringValue("name") ?: return null
        return SearchResultUiModel.Artist(
            id = id,
            title = title,
            albumCount = intValue("albumSize"),
            coverUrl = stringValue("picUrl"),
            routeTarget = SearchResultRouteMapper.artist(id)
        )
    }

    private fun JsonObject.toPlaylistItem(): SearchResultUiModel.Playlist? {
        val id = stringValue("id") ?: return null
        val title = stringValue("name") ?: return null
        return SearchResultUiModel.Playlist(
            id = id,
            title = title,
            creatorName = objectValue("creator").stringValue("nickname").orEmpty(),
            trackCount = intValue("trackCount"),
            coverUrl = stringValue("coverImgUrl"),
            routeTarget = SearchResultRouteMapper.playlist(id)
        )
    }

    private fun JsonObject.toUserItem(): SearchResultUiModel.Generic? {
        val id = stringValue("userId") ?: stringValue("id") ?: return null
        val title = stringValue("nickname") ?: stringValue("userName") ?: return null
        return genericItem(
            id = id,
            type = SearchResultType.USER,
            title = title,
            subtitle = listOfNotNull(
                "用户",
                stringValue("signature")
            ).filter { it.isNotBlank() }.joinToString(" · "),
            tertiary = intValue("playlistCount")?.let { "${it} 个歌单" }.orEmpty(),
            coverUrl = stringValue("avatarUrl")
        )
    }

    private fun JsonObject.toMvItem(): SearchResultUiModel.Generic? {
        val id = stringValue("id") ?: stringValue("mvId") ?: return null
        val title = stringValue("name") ?: stringValue("title") ?: return null
        return genericItem(
            id = id,
            type = SearchResultType.MV,
            title = title,
            subtitle = listOf(
                "MV",
                stringValue("artistName")
                    ?: arrayValue("artists").mapNotNull { it.jsonObject.stringValue("name") }.joinToString(" / ")
            ).filter { it.isNotBlank() }.joinToString(" · "),
            tertiary = intValue("playCount")?.let { "${it} 次播放" }.orEmpty(),
            coverUrl = stringValue("cover") ?: stringValue("imgurl16v9")
        )
    }

    private fun JsonObject.toLyricItem(): SearchResultUiModel.Generic? {
        val id = stringValue("id") ?: stringValue("songId") ?: return null
        val title = stringValue("name") ?: stringValue("title") ?: return null
        val artistText = arrayValue("artists")
            .mapNotNull { it.jsonObject.stringValue("name") }
            .ifEmpty {
                arrayValue("ar").mapNotNull { it.jsonObject.stringValue("name") }
            }
            .joinToString(" / ")
        return genericItem(
            id = id,
            type = SearchResultType.LYRIC,
            title = title,
            subtitle = listOf("歌词", artistText).filter { it.isNotBlank() }.joinToString(" · "),
            tertiary = stringValue("lyric") ?: stringValue("content").orEmpty(),
            coverUrl = objectValue("album").stringValue("picUrl") ?: objectValue("al").stringValue("picUrl")
        )
    }

    private fun JsonObject.toRadioItem(): SearchResultUiModel.Generic? {
        val id = stringValue("id") ?: stringValue("djRadioId") ?: return null
        val title = stringValue("name") ?: return null
        return genericItem(
            id = id,
            type = SearchResultType.RADIO,
            title = title,
            subtitle = listOfNotNull(
                "电台",
                objectValue("dj").stringValue("nickname") ?: stringValue("djNickname")
            ).filter { it.isNotBlank() }.joinToString(" · "),
            tertiary = intValue("programCount")?.let { "${it} 期节目" }.orEmpty(),
            coverUrl = stringValue("picUrl") ?: stringValue("coverUrl")
        )
    }

    private fun JsonObject.toVideoItem(): SearchResultUiModel.Generic? {
        val id = stringValue("vid") ?: stringValue("id") ?: return null
        val title = stringValue("title") ?: stringValue("name") ?: return null
        val creator = arrayValue("creator")
            .mapNotNull { it.jsonObject.stringValue("userName") ?: it.jsonObject.stringValue("nickname") }
            .joinToString(" / ")
        return genericItem(
            id = id,
            type = SearchResultType.VIDEO,
            title = title,
            subtitle = listOf("视频", creator).filter { it.isNotBlank() }.joinToString(" · "),
            tertiary = intValue("playTime")?.let { "${it} 次播放" }.orEmpty(),
            coverUrl = stringValue("coverUrl") ?: stringValue("cover")
        )
    }

    private fun JsonObject.toCompositeItem(): SearchResultUiModel.Generic? {
        val type = guessCompositeType()
        val id = stringValue("id")
            ?: stringValue("userId")
            ?: stringValue("vid")
            ?: return null
        val title = stringValue("name")
            ?: stringValue("title")
            ?: stringValue("nickname")
            ?: return null
        return genericItem(
            id = id,
            type = SearchResultType.COMPOSITE,
            title = title,
            subtitle = listOf("综合", type.displayLabel).joinToString(" · "),
            tertiary = "",
            coverUrl = stringValue("coverImgUrl")
                ?: stringValue("picUrl")
                ?: stringValue("avatarUrl")
                ?: stringValue("cover")
                ?: stringValue("coverUrl")
        )
    }

    private fun JsonObject.toVoiceItem(): SearchResultUiModel.Generic? {
        val id = stringValue("id") ?: stringValue("resourceId") ?: return null
        val title = stringValue("name") ?: stringValue("title") ?: return null
        return genericItem(
            id = id,
            type = SearchResultType.VOICE,
            title = title,
            subtitle = listOfNotNull(
                "声音",
                stringValue("creator")
                    ?: objectValue("dj").stringValue("nickname")
            ).filter { it.isNotBlank() }.joinToString(" · "),
            tertiary = intValue("playCount")?.let { "${it} 次播放" }.orEmpty(),
            coverUrl = stringValue("coverUrl") ?: stringValue("picUrl")
        )
    }

    private fun JsonObject.genericItem(
        id: String,
        type: SearchResultType,
        title: String,
        subtitle: String,
        tertiary: String,
        coverUrl: String?
    ): SearchResultUiModel.Generic {
        return SearchResultUiModel.Generic(
            id = id,
            resultType = type,
            title = title,
            subtitle = subtitle,
            tertiary = tertiary,
            coverUrl = coverUrl,
            routeTarget = SearchResultRouteMapper.generic(type, id)
        )
    }

    private fun JsonObject.guessCompositeType(): SearchResultType {
        return when {
            containsKey("nickname") || containsKey("userId") -> SearchResultType.USER
            containsKey("artistName") || containsKey("imgurl16v9") -> SearchResultType.MV
            containsKey("coverImgUrl") || containsKey("trackCount") -> SearchResultType.PLAYLIST
            containsKey("albumSize") -> SearchResultType.ARTIST
            containsKey("size") -> SearchResultType.ALBUM
            containsKey("vid") || containsKey("coverUrl") -> SearchResultType.VIDEO
            else -> SearchResultType.SONG
        }
    }
}

internal object SearchResultRouteMapper {
    fun song(songId: String): SearchRouteTarget = SearchRouteTarget.Song(songId)

    fun album(albumId: String): SearchRouteTarget = SearchRouteTarget.Album(albumId)

    fun artist(artistId: String): SearchRouteTarget = SearchRouteTarget.Artist(artistId)

    fun playlist(playlistId: String): SearchRouteTarget = SearchRouteTarget.Playlist(playlistId)

    fun generic(type: SearchResultType, targetId: String): SearchRouteTarget {
        return SearchRouteTarget.Generic(
            resultType = type,
            targetId = targetId
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
    value?.takeIf { it.isNotBlank() }?.let(::add)
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())
