package com.wxy.playerlite.feature.player

import com.wxy.playerlite.feature.player.model.SongWikiSummary
import com.wxy.playerlite.feature.player.model.SongWikiSummaryPlaylist
import com.wxy.playerlite.feature.player.model.SongWikiSummarySection
import com.wxy.playerlite.feature.player.model.SongWikiSummarySong
import com.wxy.playerlite.network.core.JsonHttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal interface SongWikiRepository {
    suspend fun fetchSongWiki(songId: String): SongWikiSummary?
}

internal class DefaultSongWikiRepository(
    private val remoteDataSource: SongWikiRemoteDataSource
) : SongWikiRepository {
    override suspend fun fetchSongWiki(songId: String): SongWikiSummary? {
        val payload = remoteDataSource.fetchSongWiki(songId)
        check(payload.intValue("code") == 200) {
            "Song wiki request failed: code=${payload.intValue("code")}"
        }
        return SongWikiJsonMapper.parse(payload)
    }
}

internal interface SongWikiRemoteDataSource {
    suspend fun fetchSongWiki(songId: String): JsonObject
}

internal class NeteaseSongWikiRemoteDataSource(
    private val httpClient: JsonHttpClient
) : SongWikiRemoteDataSource {
    override suspend fun fetchSongWiki(songId: String): JsonObject {
        return httpClient.get(
            path = "/song/wiki/summary",
            queryParams = mapOf("id" to songId),
            requiresAuth = true
        )
    }
}

internal object SongWikiJsonMapper {
    private const val BASIC_BLOCK_CODE = "SONG_PLAY_ABOUT_SONG_BASIC"
    private const val SIMILAR_SONG_BLOCK_CODE = "SONG_PLAY_ABOUT_SIMILAR_SONG"
    private const val RELATED_PLAYLIST_BLOCK_CODE = "SONG_PLAY_ABOUT_RELATED_PLAYLIST"
    private val supportedCreativeTypes = setOf("songTag", "songBizTag", "language", "bpm", "sheet")

    fun parse(payload: JsonObject): SongWikiSummary? {
        val blocks = payload.objectValue("data").arrayValue("blocks")
        val block = blocks
            .firstObjectOrNull { it.stringValue("code") == BASIC_BLOCK_CODE }
            ?: return null

        val uiElement = block.objectValue("uiElement")
        val title = uiElement.objectValue("mainTitle").stringValue("title")
            .orEmpty()
            .ifBlank { "音乐百科" }
        val coverUrl = uiElement.arrayValue("images")
            .firstObjectOrNull()
            ?.stringValue("imageUrl")
        val contributionText = uiElement.arrayValue("textLinks")
            .mapNotNull { element -> (element as? JsonObject)?.stringValue("text") }
            .firstOrNull()
        val sections = block.arrayValue("creatives").mapNotNull(::parseSection)
        val similarSongs = blocks
            .firstObjectOrNull { it.stringValue("code") == SIMILAR_SONG_BLOCK_CODE }
            ?.arrayValue("creatives")
            ?.flatMap { creative ->
                (creative as? JsonObject)
                    ?.arrayValue("resources")
                    ?.mapNotNull { resource -> parseSimilarSong(resource as? JsonObject) }
                    .orEmpty()
            }
            .orEmpty()
            .distinctBy { it.songId }
        val relatedPlaylists = blocks
            .firstObjectOrNull { it.stringValue("code") == RELATED_PLAYLIST_BLOCK_CODE }
            ?.arrayValue("creatives")
            ?.flatMap { creative ->
                (creative as? JsonObject)
                    ?.arrayValue("resources")
                    ?.mapNotNull { resource -> parseRelatedPlaylist(resource as? JsonObject) }
                    .orEmpty()
            }
            .orEmpty()
            .distinctBy { it.playlistId }

        if (coverUrl == null &&
            contributionText == null &&
            sections.isEmpty() &&
            similarSongs.isEmpty() &&
            relatedPlaylists.isEmpty()
        ) {
            return null
        }

        return SongWikiSummary(
            title = title,
            coverUrl = coverUrl,
            contributionText = contributionText,
            sections = sections,
            similarSongs = similarSongs,
            relatedPlaylists = relatedPlaylists
        )
    }

    private fun parseSection(element: JsonElement): SongWikiSummarySection? {
        val creative = element as? JsonObject ?: return null
        val creativeType = creative.stringValue("creativeType").orEmpty()
        if (creativeType !in supportedCreativeTypes) {
            return null
        }
        val uiElement = creative.objectValue("uiElement")
        val title = uiElement.objectValue("mainTitle").stringValue("title").orEmpty()
        if (title.isBlank()) {
            return null
        }
        val values = buildList {
            addAll(
                uiElement.arrayValue("buttons").mapNotNull { button ->
                    (button as? JsonObject)?.stringValue("text")
                }
            )
            addAll(
                uiElement.arrayValue("textLinks").mapNotNull { link ->
                    (link as? JsonObject)?.stringValue("text")
                }
            )
            addAll(
                creative.arrayValue("resources").mapNotNull { resource ->
                    (resource as? JsonObject)?.extractResourceTitle()
                }
            )
        }.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (values.isEmpty()) {
            return null
        }

        return SongWikiSummarySection(
            title = title,
            values = values
        )
    }

    private fun parseSimilarSong(resource: JsonObject?): SongWikiSummarySong? {
        resource ?: return null
        if (resource.stringValue("resourceType")?.uppercase() != "SONG") {
            return null
        }
        val songId = resource.stringValue("resourceId").orEmpty()
        if (songId.isBlank()) {
            return null
        }
        val uiElement = resource.objectValue("uiElement")
        val title = uiElement.objectValue("mainTitle").stringValue("title").orEmpty()
        if (title.isBlank()) {
            return null
        }
        val subtitle = uiElement.arrayValue("subTitles")
            .mapNotNull { it as? JsonObject }
            .mapNotNull { it.stringValue("title") }
            .joinToString(separator = " · ")
        val coverUrl = uiElement.arrayValue("images")
            .firstObjectOrNull()
            ?.stringValue("imageUrl")
        return SongWikiSummarySong(
            songId = songId,
            title = title,
            subtitle = subtitle,
            coverUrl = coverUrl
        )
    }

    private fun parseRelatedPlaylist(resource: JsonObject?): SongWikiSummaryPlaylist? {
        resource ?: return null
        if (resource.stringValue("resourceType")?.uppercase() != "PLAYLIST") {
            return null
        }
        val playlistId = resource.stringValue("resourceId").orEmpty()
        if (playlistId.isBlank()) {
            return null
        }
        val uiElement = resource.objectValue("uiElement")
        val title = uiElement.objectValue("mainTitle").stringValue("title").orEmpty()
        if (title.isBlank()) {
            return null
        }
        val coverUrl = uiElement.arrayValue("images")
            .firstObjectOrNull()
            ?.stringValue("imageUrl")
        val playCount = resource.objectValue("resourceExt").longValue("playCount")
        return SongWikiSummaryPlaylist(
            playlistId = playlistId,
            title = title,
            subtitle = formatPlaylistPlayCount(playCount),
            coverUrl = coverUrl
        )
    }
}

private fun JsonObject.extractResourceTitle(): String? {
    val uiElement = objectValue("uiElement")
    return uiElement.objectValue("mainTitle").stringValue("title")
        ?: uiElement.arrayValue("images")
            .firstObjectOrNull()
            ?.stringValue("title")
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

private fun JsonObject.intValue(key: String): Int {
    return stringValue(key)?.toIntOrNull() ?: 0
}

private fun JsonObject.longValue(key: String): Long {
    return stringValue(key)?.toLongOrNull() ?: 0L
}

private inline fun JsonArray.firstObjectOrNull(predicate: (JsonObject) -> Boolean): JsonObject? {
    return firstNotNullOfOrNull { element ->
        (element as? JsonObject)?.takeIf(predicate)
    }
}

private fun JsonArray.firstObjectOrNull(): JsonObject? {
    return firstNotNullOfOrNull { it as? JsonObject }
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())

private fun formatPlaylistPlayCount(playCount: Long): String {
    val normalized = playCount.coerceAtLeast(0L)
    return when {
        normalized >= 100_000_000L -> String.format("%.1f 亿播放", normalized / 100_000_000f)
        normalized >= 10_000L -> String.format("%.1f 万播放", normalized / 10_000f)
        normalized > 0L -> "$normalized 播放"
        else -> ""
    }
}
