package com.wxy.playerlite.feature.playlist

import com.wxy.playerlite.network.core.JsonHttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal interface PlaylistDetailRepository {
    suspend fun fetchPlaylistHeader(playlistId: String): PlaylistHeaderContent

    suspend fun fetchPlaylistTracks(
        playlistId: String,
        offset: Int = 0,
        limit: Int = DEFAULT_DETAIL_TRACK_PAGE_SIZE
    ): List<PlaylistTrackRow>

    suspend fun fetchPlaylistDynamic(playlistId: String): PlaylistDynamicInfo

    suspend fun updatePlaylistPlayCount(playlistId: String)
}

internal data class PlaylistHeaderContent(
    val playlistId: String,
    val title: String,
    val creatorName: String,
    val description: String,
    val coverUrl: String?,
    val trackCount: Int,
    val playCount: Long,
    val subscribedCount: Long
)

internal data class PlaylistTrackRow(
    val trackId: String,
    val title: String,
    val artistText: String,
    val albumTitle: String,
    val coverUrl: String?,
    val durationMs: Long
)

internal data class PlaylistDynamicInfo(
    val commentCount: Int,
    val isSubscribed: Boolean,
    val playCount: Long
)

internal class DefaultPlaylistDetailRepository(
    private val remoteDataSource: PlaylistDetailRemoteDataSource
) : PlaylistDetailRepository {
    override suspend fun fetchPlaylistHeader(playlistId: String): PlaylistHeaderContent {
        return PlaylistDetailJsonMapper.parseHeader(
            payload = remoteDataSource.fetchPlaylistDetail(playlistId)
        )
    }

    override suspend fun fetchPlaylistTracks(
        playlistId: String,
        offset: Int,
        limit: Int
    ): List<PlaylistTrackRow> {
        return PlaylistDetailJsonMapper.parseTracks(
            payload = remoteDataSource.fetchPlaylistTracks(
                playlistId = playlistId,
                offset = offset,
                limit = limit
            )
        )
    }

    override suspend fun fetchPlaylistDynamic(playlistId: String): PlaylistDynamicInfo {
        return PlaylistDetailJsonMapper.parseDynamic(
            payload = remoteDataSource.fetchPlaylistDynamic(playlistId)
        )
    }

    override suspend fun updatePlaylistPlayCount(playlistId: String) {
        remoteDataSource.updatePlaylistPlayCount(playlistId)
    }
}

internal interface PlaylistDetailRemoteDataSource {
    suspend fun fetchPlaylistDetail(playlistId: String): JsonObject

    suspend fun fetchPlaylistTracks(
        playlistId: String,
        offset: Int,
        limit: Int
    ): JsonObject

    suspend fun fetchPlaylistDynamic(playlistId: String): JsonObject

    suspend fun updatePlaylistPlayCount(playlistId: String)
}

internal class NeteasePlaylistDetailRemoteDataSource(
    private val httpClient: JsonHttpClient
) : PlaylistDetailRemoteDataSource {
    override suspend fun fetchPlaylistDetail(playlistId: String): JsonObject {
        return httpClient.get(
            path = "/playlist/detail",
            queryParams = mapOf("id" to playlistId),
            requiresAuth = true
        )
    }

    override suspend fun fetchPlaylistTracks(
        playlistId: String,
        offset: Int,
        limit: Int
    ): JsonObject {
        return httpClient.get(
            path = "/playlist/track/all",
            queryParams = mapOf(
                "id" to playlistId,
                "offset" to offset.toString(),
                "limit" to limit.toString()
            ),
            requiresAuth = true
        )
    }

    override suspend fun fetchPlaylistDynamic(playlistId: String): JsonObject {
        return httpClient.get(
            path = "/playlist/detail/dynamic",
            queryParams = mapOf("id" to playlistId),
            requiresAuth = true
        )
    }

    override suspend fun updatePlaylistPlayCount(playlistId: String) {
        httpClient.get(
            path = "/playlist/update/playcount",
            queryParams = mapOf("id" to playlistId),
            requiresAuth = true
        )
    }
}

internal object PlaylistDetailJsonMapper {
    fun parseHeader(payload: JsonObject): PlaylistHeaderContent {
        val playlist = payload.objectValue("playlist")
        return PlaylistHeaderContent(
            playlistId = playlist.stringValue("id").orEmpty(),
            title = playlist.stringValue("name").orEmpty(),
            creatorName = playlist.objectValue("creator").stringValue("nickname").orEmpty(),
            description = playlist.stringValue("description").orEmpty(),
            coverUrl = playlist.stringValue("coverImgUrl"),
            trackCount = playlist.intValue("trackCount"),
            playCount = playlist.longValue("playCount"),
            subscribedCount = playlist.longValue("subscribedCount")
        )
    }

    fun parseTracks(payload: JsonObject): List<PlaylistTrackRow> {
        return payload.arrayValue("songs").map { element ->
            val track = element as JsonObject
            PlaylistTrackRow(
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

    fun parseDynamic(payload: JsonObject): PlaylistDynamicInfo {
        return PlaylistDynamicInfo(
            commentCount = payload.intValue("commentCount"),
            isSubscribed = payload.booleanValue("subscribed"),
            playCount = payload.longValue("playCount")
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

private fun JsonObject.intValue(key: String): Int {
    return stringValue(key)?.toIntOrNull() ?: 0
}

private fun JsonObject.longValue(key: String): Long {
    return stringValue(key)?.toLongOrNull() ?: 0L
}

private fun JsonObject.booleanValue(key: String): Boolean {
    return this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())

internal const val DEFAULT_DETAIL_TRACK_PAGE_SIZE = 30
