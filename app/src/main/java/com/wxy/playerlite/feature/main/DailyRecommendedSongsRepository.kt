package com.wxy.playerlite.feature.main

import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.user.UserSessionInvalidException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal interface DailyRecommendedSongsRepository {
    suspend fun fetchDailyRecommendedSongs(): List<DailyRecommendedSongUiModel>
}

internal data class DailyRecommendedSongUiModel(
    val id: String,
    val songId: String,
    val title: String,
    val artistText: String,
    val primaryArtistId: String? = null,
    val albumTitle: String? = null,
    val coverUrl: String? = null,
    val durationMs: Long = 0L,
    val recommendReason: String? = null
)

internal class DefaultDailyRecommendedSongsRepository(
    private val remoteDataSource: DailyRecommendedSongsRemoteDataSource
) : DailyRecommendedSongsRepository {
    override suspend fun fetchDailyRecommendedSongs(): List<DailyRecommendedSongUiModel> {
        return DailyRecommendedSongsJsonMapper.parseSongs(
            payload = remoteDataSource.fetchDailyRecommendedSongs()
        )
    }
}

internal interface DailyRecommendedSongsRemoteDataSource {
    suspend fun fetchDailyRecommendedSongs(): JsonObject
}

internal class NeteaseDailyRecommendedSongsRemoteDataSource(
    private val httpClient: JsonHttpClient
) : DailyRecommendedSongsRemoteDataSource {
    override suspend fun fetchDailyRecommendedSongs(): JsonObject {
        return httpClient.get(
            path = "/recommend/songs",
            requiresAuth = true
        ).also(::ensureSuccess)
    }

    private fun ensureSuccess(payload: JsonObject) {
        val code = payload.intValue("code")
        if (code == 200) {
            return
        }
        val message = payload.stringValue("message")
            ?: payload.stringValue("msg")
            ?: "Request failed($code)"
        if (code == 301 || code == 302) {
            throw UserSessionInvalidException(message)
        }
        throw IllegalStateException(message)
    }
}

internal object DailyRecommendedSongsJsonMapper {
    fun parseSongs(payload: JsonObject): List<DailyRecommendedSongUiModel> {
        return payload.dailySongsArray()
            .mapNotNull { element ->
                (element as? JsonObject)?.toSongUiModel()
            }
    }

    private fun JsonObject.toSongUiModel(): DailyRecommendedSongUiModel? {
        val songId = stringValue("id") ?: return null
        val title = stringValue("name") ?: return null
        val artists = artistArray()
        val album = objectValue("al").takeIf { it.isNotEmpty() }
            ?: objectValue("album")
        return DailyRecommendedSongUiModel(
            id = songId,
            songId = songId,
            title = title,
            artistText = artists.mapNotNull { it.stringValue("name") }
                .joinToString(separator = " / ")
                .ifBlank { "歌曲" },
            primaryArtistId = artists.firstOrNull()?.stringValue("id"),
            albumTitle = album.stringValue("name"),
            coverUrl = album.stringValue("picUrl")
                ?: stringValue("picUrl")
                ?: stringValue("coverUrl"),
            durationMs = longValue("dt"),
            recommendReason = stringValue("recommendReason")
                ?: stringValue("reason")
        )
    }

    private fun JsonObject.artistArray(): List<JsonObject> {
        val primary = arrayValue("ar").mapNotNull { it as? JsonObject }
        if (primary.isNotEmpty()) {
            return primary
        }
        return arrayValue("artists").mapNotNull { it as? JsonObject }
    }

    private fun JsonObject.dailySongsArray(): JsonArray {
        val nestedData = objectValue("data")
        if ("dailySongs" in nestedData) {
            return nestedData.arrayValue("dailySongs")
        }
        if ("dailySongs" in this) {
            return arrayValue("dailySongs")
        }
        throw IllegalStateException("Missing dailySongs in /recommend/songs response")
    }
}

private fun JsonObject.objectValue(key: String): JsonObject {
    return (this[key] as? JsonObject) ?: emptyJsonObject
}

private fun JsonObject.arrayValue(key: String): JsonArray {
    return this[key]?.jsonArray ?: emptyJsonArray
}

private fun JsonObject.stringValue(key: String): String? {
    return (this[key] as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
}

private fun JsonObject.intValue(key: String): Int {
    return this[key]?.jsonPrimitive?.intOrNull
        ?: stringValue(key)?.toIntOrNull()
        ?: 0
}

private fun JsonObject.longValue(key: String): Long {
    return this[key]?.jsonPrimitive?.longOrNull
        ?: stringValue(key)?.toLongOrNull()
        ?: 0L
}

private fun JsonObject.isNotEmpty(): Boolean = entries.isNotEmpty()

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())
