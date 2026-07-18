package com.wxy.playerlite.feature.artist

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

object ArtistDetailJsonMapper {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun parseDetailContent(
        payload: JsonObject,
        encyclopediaPayload: JsonObject,
        dynamicPayload: JsonObject,
        followCountPayload: JsonObject
    ): ArtistDetailContent {
        val artist = payload.objectValue("data").objectValue("artist")
        val encyclopedia = parseEncyclopediaContent(encyclopediaPayload)
        val enhancement = parseEnhancementContent(
            dynamicPayload = dynamicPayload,
            followCountPayload = followCountPayload
        )
        return ArtistDetailContent(
            artistId = artist.stringValue("id").orEmpty(),
            name = artist.stringValue("name").orEmpty(),
            aliases = artist.arrayValue("alias").stringList(),
            identities = artist.arrayValue("identities").stringList(),
            avatarUrl = artist.stringValue("avatar"),
            coverUrl = artist.stringValue("cover"),
            briefDesc = artist.stringValue("briefDesc").orEmpty(),
            encyclopediaSummary = encyclopedia.summary,
            encyclopediaSections = encyclopedia.sections,
            musicCount = artist.intValue("musicSize"),
            albumCount = artist.intValue("albumSize"),
            isFollowed = enhancement.isFollowed,
            videoCount = enhancement.videoCount,
            fansCount = enhancement.fansCount
        )
    }

    fun parseEncyclopediaContent(payload: JsonObject): ArtistEncyclopediaContent {
        val content = payload.objectValue("data").takeUnless { it.isEmpty() } ?: payload
        val sections = content.arrayValue("introduction").mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val title = item.stringValue("ti").orEmpty()
            val body = item.stringValue("txt").orEmpty()
            if (title.isBlank() && body.isBlank()) {
                null
            } else {
                ArtistEncyclopediaSection(
                    title = title,
                    body = body
                )
            }
        }
        return ArtistEncyclopediaContent(
            summary = content.stringValue("briefDesc").orEmpty(),
            sections = sections
        )
    }

    fun parseHotSongs(payload: JsonObject): List<ArtistHotSongRow> {
        return payload.arrayValue("songs").mapNotNull { element ->
            val song = element as? JsonObject ?: return@mapNotNull null
            ArtistHotSongRow(
                trackId = song.stringValue("id").orEmpty(),
                title = song.stringValue("name").orEmpty(),
                artistText = song.arrayValue("ar")
                    .mapNotNull { artist ->
                        (artist as? JsonObject)?.stringValue("name")
                    }
                    .joinToString(separator = " / "),
                albumTitle = song.objectValue("al").stringValue("name").orEmpty(),
                coverUrl = song.objectValue("al").stringValue("picUrl"),
                durationMs = song.longValue("dt")
            )
        }
    }

    fun parseAlbumPage(payload: JsonObject): ArtistAlbumPage {
        return ArtistAlbumPage(
            items = payload.arrayValue("hotAlbums").mapNotNull { element ->
                val album = element as? JsonObject ?: return@mapNotNull null
                ArtistAlbumRow(
                    albumId = album.stringValue("id").orEmpty(),
                    title = album.stringValue("name").orEmpty(),
                    artistText = album.objectValue("artist").stringValue("name")
                        ?: album.arrayValue("artists")
                            .mapNotNull { artist ->
                                (artist as? JsonObject)?.stringValue("name")
                            }
                            .joinToString(separator = " / "),
                    coverUrl = album.stringValue("picUrl"),
                    trackCount = album.intValue("size"),
                    type = album.stringValue("type").orEmpty(),
                    publishTimeText = album.longValue("publishTime")
                        .takeIf { it > 0L }
                        ?.let { millis ->
                            Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("Asia/Shanghai"))
                                .toLocalDate()
                                .format(dateFormatter)
                        }
                        .orEmpty(),
                    showYearOnly = false
                )
            },
            hasMore = payload.booleanValue("more")
        )
    }

    private fun parseEnhancementContent(
        dynamicPayload: JsonObject,
        followCountPayload: JsonObject
    ): ArtistDetailEnhancement {
        val followCountData = followCountPayload.objectValue("data")
        return ArtistDetailEnhancement(
            isFollowed = dynamicPayload.nullableBooleanValue("followed")
                ?: followCountData.nullableBooleanValue("isFollow")
                ?: followCountData.nullableBooleanValue("follow"),
            videoCount = dynamicPayload.arrayValue("videoNum")
                .mapNotNull { element ->
                    (element as? JsonObject)?.intValue("num")
                }
                .maxOrNull()
                ?: 0,
            fansCount = followCountData.longValue("fansCnt")
        )
    }
}

private data class ArtistDetailEnhancement(
    val isFollowed: Boolean?,
    val videoCount: Int,
    val fansCount: Long
)

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

private fun JsonObject.booleanValue(key: String): Boolean {
    return this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
}

private fun JsonObject.nullableBooleanValue(key: String): Boolean? {
    return this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
}

private fun JsonArray.stringList(): List<String> {
    return mapNotNull { element ->
        element.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
    }
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())
