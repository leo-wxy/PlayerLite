package com.wxy.playerlite.playback.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

data class SongAudioQualityOption(
    val quality: PlaybackAudioQuality,
    val rawKey: String,
    val bitRate: Int,
    val sizeBytes: Long? = null,
    val volumeDelta: Double? = null,
    val sampleRate: Int? = null
)

data class SongAudioQualityCatalog(
    val songId: String,
    val options: List<SongAudioQualityOption>
)

object SongAudioQualityCatalogJsonMapper {
    fun parseCatalog(payload: JsonObject, songId: String): SongAudioQualityCatalog {
        val detail = payload.objectValueOrNull("data")
            ?.takeIf { it.matchesSongId(songId) || !it.isEmpty() }
            ?: payload.arrayValue("data")
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.matchesSongId(songId) }
            ?: payload.arrayValue("data")
                .firstNotNullOfOrNull { it as? JsonObject }

        val options = detail
            ?.entries
            ?.mapNotNull { (key, value) ->
                if (key.shouldIgnoreMusicDetailKey()) {
                    return@mapNotNull null
                }
                val item = value as? JsonObject ?: return@mapNotNull null
                if (!item.looksLikeQualityMetadata()) {
                    return@mapNotNull null
                }
                SongAudioQualityOption(
                    quality = PlaybackAudioQuality.fromApiKey(key) ?: PlaybackAudioQuality.UNKNOWN,
                    rawKey = key,
                    bitRate = item.intValue("br"),
                    sizeBytes = item.longValueOrNull("size"),
                    volumeDelta = item.doubleValueOrNull("vd"),
                    sampleRate = item.intValueOrNull("sr")
                )
            }
            .orEmpty()
            .sortedWith(
                compareByDescending<SongAudioQualityOption> { it.quality.sortOrder }
                    .thenBy { it.rawKey }
            )

        return SongAudioQualityCatalog(
            songId = songId,
            options = options
        )
    }
}

private fun JsonObject.matchesSongId(songId: String): Boolean {
    return stringValue("id") == songId || stringValue("songId") == songId
}

private fun String.shouldIgnoreMusicDetailKey(): Boolean {
    return when (trim().lowercase()) {
        "l", "vi" -> true
        else -> false
    }
}

private fun JsonObject.looksLikeQualityMetadata(): Boolean {
    return containsKey("br") || containsKey("size") || containsKey("vd") || containsKey("sr")
}

private fun JsonObject.objectValueOrNull(key: String): JsonObject? {
    return this[key] as? JsonObject
}

private fun JsonObject.arrayValue(key: String): JsonArray {
    return this[key]?.jsonArray ?: emptyJsonArray
}

private fun JsonObject.stringValue(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.intValue(key: String): Int {
    return intValueOrNull(key) ?: 0
}

private fun JsonObject.intValueOrNull(key: String): Int? {
    return stringValue(key)?.toIntOrNull()
}

private fun JsonObject.longValueOrNull(key: String): Long? {
    return stringValue(key)?.toLongOrNull()
}

private fun JsonObject.doubleValueOrNull(key: String): Double? {
    return stringValue(key)?.toDoubleOrNull()
}

private val emptyJsonArray = JsonArray(emptyList())
