package com.wxy.playerlite.feature.player

import com.wxy.playerlite.feature.player.model.SongWikiSummary
import com.wxy.playerlite.feature.player.model.SongWikiSummarySection
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
    private val supportedCreativeTypes = setOf("songTag", "songBizTag", "language", "bpm", "sheet")

    fun parse(payload: JsonObject): SongWikiSummary? {
        val block = payload.objectValue("data")
            .arrayValue("blocks")
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

        if (coverUrl == null && contributionText == null && sections.isEmpty()) {
            return null
        }

        return SongWikiSummary(
            title = title,
            coverUrl = coverUrl,
            contributionText = contributionText,
            sections = sections
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
