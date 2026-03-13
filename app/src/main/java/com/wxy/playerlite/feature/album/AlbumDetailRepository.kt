package com.wxy.playerlite.feature.album

import com.wxy.playerlite.network.core.JsonHttpClient
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal interface AlbumDetailRepository {
    suspend fun fetchAlbumContent(
        albumId: String,
        offset: Int = 0,
        limit: Int = DEFAULT_ALBUM_TRACK_PAGE_SIZE
    ): AlbumDetailContent

    suspend fun fetchAlbumDynamic(albumId: String): AlbumDynamicInfo
}

internal data class AlbumDetailContent(
    val albumId: String,
    val title: String,
    val artistText: String,
    val description: String,
    val coverUrl: String?,
    val company: String,
    val publishTimeText: String,
    val trackCount: Int,
    val tracks: List<AlbumTrackRow>
)

internal data class AlbumTrackRow(
    val trackId: String,
    val title: String,
    val artistText: String,
    val albumTitle: String,
    val coverUrl: String?,
    val durationMs: Long
)

internal data class AlbumDynamicInfo(
    val commentCount: Int,
    val shareCount: Int,
    val subscribedCount: Int
)

internal class DefaultAlbumDetailRepository(
    private val remoteDataSource: AlbumDetailRemoteDataSource
) : AlbumDetailRepository {
    override suspend fun fetchAlbumContent(
        albumId: String,
        offset: Int,
        limit: Int
    ): AlbumDetailContent {
        return AlbumDetailJsonMapper.parseContent(
            payload = remoteDataSource.fetchAlbumContent(
                albumId = albumId,
                offset = offset,
                limit = limit
            )
        )
    }

    override suspend fun fetchAlbumDynamic(albumId: String): AlbumDynamicInfo {
        return AlbumDetailJsonMapper.parseDynamic(
            payload = remoteDataSource.fetchAlbumDynamic(albumId)
        )
    }
}

internal interface AlbumDetailRemoteDataSource {
    suspend fun fetchAlbumContent(
        albumId: String,
        offset: Int,
        limit: Int
    ): JsonObject

    suspend fun fetchAlbumDynamic(albumId: String): JsonObject
}

internal class NeteaseAlbumDetailRemoteDataSource(
    private val httpClient: JsonHttpClient
) : AlbumDetailRemoteDataSource {
    override suspend fun fetchAlbumContent(
        albumId: String,
        offset: Int,
        limit: Int
    ): JsonObject {
        return httpClient.get(
            path = "/album",
            queryParams = mapOf(
                "id" to albumId,
                "offset" to offset.toString(),
                "limit" to limit.toString()
            ),
            requiresAuth = true
        )
    }

    override suspend fun fetchAlbumDynamic(albumId: String): JsonObject {
        return httpClient.get(
            path = "/album/detail/dynamic",
            queryParams = mapOf("id" to albumId),
            requiresAuth = true
        )
    }
}

internal object AlbumDetailJsonMapper {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun parseContent(payload: JsonObject): AlbumDetailContent {
        val album = payload.objectValue("album")
        return AlbumDetailContent(
            albumId = album.stringValue("id").orEmpty(),
            title = album.stringValue("name").orEmpty(),
            artistText = album.objectValue("artist").stringValue("name")
                ?: album.arrayValue("artists")
                    .mapNotNull { artist ->
                        (artist as? JsonObject)?.stringValue("name")
                    }
                    .joinToString(separator = " / "),
            description = album.stringValue("description").orEmpty(),
            coverUrl = album.stringValue("picUrl"),
            company = album.stringValue("company").orEmpty(),
            publishTimeText = album.longValue("publishTime").takeIf { it > 0L }?.let { millis ->
                Instant.ofEpochMilli(millis)
                    .atZone(ZoneId.of("Asia/Shanghai"))
                    .toLocalDate()
                    .format(dateFormatter)
            }.orEmpty(),
            trackCount = album.intValue("size"),
            tracks = parseTracks(payload)
        )
    }

    fun parseDynamic(payload: JsonObject): AlbumDynamicInfo {
        return AlbumDynamicInfo(
            commentCount = payload.intValue("commentCount"),
            shareCount = payload.intValue("shareCount"),
            subscribedCount = payload.intValue("subCount")
        )
    }

    private fun parseTracks(payload: JsonObject): List<AlbumTrackRow> {
        return payload.arrayValue("songs").mapNotNull { element ->
            val track = element as? JsonObject ?: return@mapNotNull null
            AlbumTrackRow(
                trackId = track.stringValue("id").orEmpty(),
                title = track.stringValue("name").orEmpty(),
                artistText = track.arrayValue("ar")
                    .mapNotNull { artist ->
                        (artist as? JsonObject)?.stringValue("name")
                    }
                    .joinToString(separator = " / "),
                albumTitle = track.objectValue("al").stringValue("name").orEmpty(),
                coverUrl = track.objectValue("al").stringValue("picUrl"),
                durationMs = track.longValue("dt")
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

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())

internal const val DEFAULT_ALBUM_TRACK_PAGE_SIZE = 30
