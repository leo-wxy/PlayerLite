package com.wxy.playerlite.feature.artist

import com.wxy.playerlite.network.core.JsonHttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal interface ArtistDetailRepository {
    suspend fun fetchArtistDetail(artistId: String): ArtistDetailContent

    suspend fun fetchArtistEncyclopedia(artistId: String): ArtistEncyclopediaContent

    suspend fun fetchArtistHotSongs(artistId: String): List<ArtistHotSongRow>
}

internal data class ArtistDetailContent(
    val artistId: String,
    val name: String,
    val aliases: List<String>,
    val identities: List<String>,
    val avatarUrl: String?,
    val coverUrl: String?,
    val briefDesc: String,
    val encyclopediaSummary: String = "",
    val encyclopediaSections: List<ArtistEncyclopediaSection> = emptyList(),
    val musicCount: Int,
    val albumCount: Int
)

internal data class ArtistEncyclopediaContent(
    val summary: String,
    val sections: List<ArtistEncyclopediaSection>
)

internal data class ArtistEncyclopediaSection(
    val title: String,
    val body: String
)

internal data class ArtistHotSongRow(
    val trackId: String,
    val title: String,
    val artistText: String,
    val albumTitle: String,
    val coverUrl: String?,
    val durationMs: Long
)

internal class DefaultArtistDetailRepository(
    private val remoteDataSource: ArtistDetailRemoteDataSource
) : ArtistDetailRepository {
    override suspend fun fetchArtistDetail(artistId: String): ArtistDetailContent {
        val detailPayload = remoteDataSource.fetchArtistDetail(artistId)
        val descPayload = runCatching {
            remoteDataSource.fetchArtistDesc(artistId)
        }.getOrElse {
            emptyJsonObject
        }
        return ArtistDetailJsonMapper.parse(
            payload = detailPayload,
            descPayload = descPayload
        )
    }

    override suspend fun fetchArtistEncyclopedia(artistId: String): ArtistEncyclopediaContent {
        return ArtistDetailJsonMapper.parseEncyclopedia(
            payload = remoteDataSource.fetchArtistDesc(artistId)
        )
    }

    override suspend fun fetchArtistHotSongs(artistId: String): List<ArtistHotSongRow> {
        return ArtistDetailJsonMapper.parseHotSongs(
            payload = remoteDataSource.fetchArtistHotSongs(artistId)
        )
    }
}

internal interface ArtistDetailRemoteDataSource {
    suspend fun fetchArtistDetail(artistId: String): JsonObject

    suspend fun fetchArtistDesc(artistId: String): JsonObject

    suspend fun fetchArtistHotSongs(artistId: String): JsonObject
}

internal class NeteaseArtistDetailRemoteDataSource(
    private val httpClient: JsonHttpClient
) : ArtistDetailRemoteDataSource {
    override suspend fun fetchArtistDetail(artistId: String): JsonObject {
        return httpClient.get(
            path = "/artist/detail",
            queryParams = mapOf("id" to artistId),
            requiresAuth = true
        )
    }

    override suspend fun fetchArtistDesc(artistId: String): JsonObject {
        return httpClient.get(
            path = "/artist/desc",
            queryParams = mapOf("id" to artistId),
            requiresAuth = true
        )
    }

    override suspend fun fetchArtistHotSongs(artistId: String): JsonObject {
        return httpClient.get(
            path = "/artist/top/song",
            queryParams = mapOf("id" to artistId),
            requiresAuth = true
        )
    }
}

internal object ArtistDetailJsonMapper {
    fun parse(
        payload: JsonObject,
        descPayload: JsonObject
    ): ArtistDetailContent {
        val artist = payload.objectValue("data").objectValue("artist")
        val encyclopedia = parseEncyclopedia(descPayload)
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
            albumCount = artist.intValue("albumSize")
        )
    }

    fun parseEncyclopedia(payload: JsonObject): ArtistEncyclopediaContent {
        val sections = payload.arrayValue("introduction").mapNotNull { element ->
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
            summary = payload.stringValue("briefDesc").orEmpty(),
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

private fun JsonArray.stringList(): List<String> {
    return mapNotNull { element ->
        element.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
    }
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())
