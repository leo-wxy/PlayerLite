package com.wxy.playerlite.core.playback

import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.playback.model.MusicInfo
import kotlinx.serialization.json.JsonObject

internal interface SongDetailRepository {
    suspend fun fetchSongs(songIds: List<String>): List<MusicInfo>
}

internal class DefaultSongDetailRepository(
    private val remoteDataSource: SongDetailRemoteDataSource
) : SongDetailRepository {
    override suspend fun fetchSongs(songIds: List<String>): List<MusicInfo> {
        val normalizedIds = songIds
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        if (normalizedIds.isEmpty()) {
            return emptyList()
        }
        return SongDetailJsonMapper.parseSongs(
            payload = remoteDataSource.fetchSongs(normalizedIds)
        )
    }
}

internal interface SongDetailRemoteDataSource {
    suspend fun fetchSongs(songIds: List<String>): JsonObject
}

internal class NeteaseSongDetailRemoteDataSource(
    private val httpClient: JsonHttpClient
) : SongDetailRemoteDataSource {
    override suspend fun fetchSongs(songIds: List<String>): JsonObject {
        return httpClient.get(
            path = "/song/detail",
            queryParams = mapOf("ids" to songIds.joinToString(separator = ",")),
            requiresAuth = true
        )
    }
}
