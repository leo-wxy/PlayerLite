package com.wxy.playerlite.feature.artist

import com.wxy.playerlite.network.core.JsonHttpClient
import kotlinx.serialization.json.JsonObject

internal interface ArtistDetailRemoteDataSource {
    suspend fun fetchArtistDetail(artistId: String): JsonObject

    suspend fun fetchArtistEncyclopedia(artistId: String): JsonObject

    suspend fun fetchArtistDynamic(artistId: String): JsonObject

    suspend fun fetchArtistFollowCount(artistId: String): JsonObject

    suspend fun fetchArtistHotSongs(artistId: String): JsonObject

    suspend fun fetchArtistAlbums(
        artistId: String,
        offset: Int,
        limit: Int
    ): JsonObject
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

    override suspend fun fetchArtistEncyclopedia(artistId: String): JsonObject {
        return httpClient.get(
            path = "/ugc/artist/get",
            queryParams = mapOf("id" to artistId),
            requiresAuth = true
        )
    }

    override suspend fun fetchArtistDynamic(artistId: String): JsonObject {
        return httpClient.get(
            path = "/artist/detail/dynamic",
            queryParams = mapOf("id" to artistId),
            requiresAuth = true
        )
    }

    override suspend fun fetchArtistFollowCount(artistId: String): JsonObject {
        return httpClient.get(
            path = "/artist/follow/count",
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

    override suspend fun fetchArtistAlbums(
        artistId: String,
        offset: Int,
        limit: Int
    ): JsonObject {
        return httpClient.get(
            path = "/artist/album",
            queryParams = mapOf(
                "id" to artistId,
                "offset" to offset.toString(),
                "limit" to limit.toString()
            ),
            requiresAuth = true
        )
    }
}
