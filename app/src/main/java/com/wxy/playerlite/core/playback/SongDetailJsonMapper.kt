package com.wxy.playerlite.core.playback

import com.wxy.playerlite.playback.model.MusicInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal object SongDetailJsonMapper {
    fun parseSongs(
        payload: JsonObject,
        playbackContext: com.wxy.playerlite.playback.model.PlaybackContext? = null
    ): List<MusicInfo> {
        return payload.arrayValue("songs").mapNotNull { element ->
            (element as? JsonObject)?.let { parseSong(it, playbackContext) }
        }
    }

    fun parseSong(
        track: JsonObject,
        playbackContext: com.wxy.playerlite.playback.model.PlaybackContext? = null
    ): MusicInfo {
        val songId = track.stringValue("id").orEmpty()
        return MusicInfo(
            id = songId,
            songId = songId.ifBlank { null },
            title = track.stringValue("name").orEmpty(),
            artistNames = track.arrayValue("ar")
                .mapNotNull { artist ->
                    (artist as? JsonObject)?.stringValue("name")
                },
            artistIds = track.arrayValue("ar")
                .mapNotNull { artist ->
                    (artist as? JsonObject)?.stringValue("id")
                },
            albumTitle = track.objectValue("al").stringValue("name"),
            coverUrl = track.objectValue("al").stringValue("picUrl"),
            durationMs = track.longValue("dt"),
            playbackUri = "",
            playbackContext = playbackContext
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

private fun JsonObject.longValue(key: String): Long {
    return stringValue(key)?.toLongOrNull() ?: 0L
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())
