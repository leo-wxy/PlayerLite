package com.wxy.playerlite.feature.artist

import kotlinx.serialization.json.JsonObject

internal interface ArtistDetailRepository {
    suspend fun fetchArtistDetail(artistId: String): ArtistDetailContent

    suspend fun fetchArtistEncyclopedia(artistId: String): ArtistEncyclopediaContent

    suspend fun fetchArtistHotSongs(artistId: String): List<ArtistHotSongRow>

    suspend fun fetchArtistAlbums(
        artistId: String,
        offset: Int = 0,
        limit: Int = DEFAULT_ARTIST_ALBUM_PAGE_SIZE
    ): ArtistAlbumPage
}

internal class DefaultArtistDetailRepository(
    private val remoteDataSource: ArtistDetailRemoteDataSource
) : ArtistDetailRepository {
    override suspend fun fetchArtistDetail(artistId: String): ArtistDetailContent {
        val detailPayload = remoteDataSource.fetchArtistDetail(artistId)
        val encyclopediaPayload = runCatching {
            remoteDataSource.fetchArtistEncyclopedia(artistId)
        }.getOrElse {
            JsonObject(emptyMap())
        }
        val dynamicPayload = runCatching {
            remoteDataSource.fetchArtistDynamic(artistId)
        }.getOrElse {
            JsonObject(emptyMap())
        }
        val followCountPayload = runCatching {
            remoteDataSource.fetchArtistFollowCount(artistId)
        }.getOrElse {
            JsonObject(emptyMap())
        }
        return ArtistDetailJsonMapper.parseDetailContent(
            payload = detailPayload,
            encyclopediaPayload = encyclopediaPayload,
            dynamicPayload = dynamicPayload,
            followCountPayload = followCountPayload
        )
    }

    override suspend fun fetchArtistEncyclopedia(artistId: String): ArtistEncyclopediaContent {
        return ArtistDetailJsonMapper.parseEncyclopediaContent(
            payload = remoteDataSource.fetchArtistEncyclopedia(artistId)
        )
    }

    override suspend fun fetchArtistHotSongs(artistId: String): List<ArtistHotSongRow> {
        return ArtistDetailJsonMapper.parseHotSongs(
            payload = remoteDataSource.fetchArtistHotSongs(artistId)
        )
    }

    override suspend fun fetchArtistAlbums(
        artistId: String,
        offset: Int,
        limit: Int
    ): ArtistAlbumPage {
        return ArtistDetailJsonMapper.parseAlbumPage(
            payload = remoteDataSource.fetchArtistAlbums(
                artistId = artistId,
                offset = offset,
                limit = limit
            )
        )
    }
}
