package com.wxy.playerlite.core.playback

import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.playback.model.SongAudioQualityCatalog
import com.wxy.playerlite.playback.model.SongAudioQualityCatalogJsonMapper
import kotlinx.serialization.json.JsonObject

internal interface SongAudioQualityRepository {
    suspend fun fetchCatalog(songId: String): SongAudioQualityCatalog?

    fun clear()

    fun peekCachedCatalog(songId: String): SongAudioQualityCatalog?
}

internal class DefaultSongAudioQualityRepository(
    private val remoteDataSource: SongAudioQualityRemoteDataSource
) : SongAudioQualityRepository {
    private val cache = linkedMapOf<String, SongAudioQualityCatalog>()

    override suspend fun fetchCatalog(songId: String): SongAudioQualityCatalog? {
        val normalizedSongId = songId.trim().takeIf { it.isNotEmpty() } ?: return null
        synchronized(cache) {
            cache[normalizedSongId]
        }?.let { return it }

        val payload = runCatching {
            remoteDataSource.fetchCatalog(normalizedSongId)
        }.getOrNull() ?: return null
        val catalog = runCatching {
            SongAudioQualityCatalogJsonMapper.parseCatalog(
                payload = payload,
                songId = normalizedSongId
            )
        }.getOrNull() ?: return null
        synchronized(cache) {
            cache[normalizedSongId] = catalog
        }
        return catalog
    }

    override fun clear() {
        synchronized(cache) {
            cache.clear()
        }
    }

    override fun peekCachedCatalog(songId: String): SongAudioQualityCatalog? {
        val normalizedSongId = songId.trim().takeIf { it.isNotEmpty() } ?: return null
        return synchronized(cache) {
            cache[normalizedSongId]
        }
    }
}

internal interface SongAudioQualityRemoteDataSource {
    suspend fun fetchCatalog(songId: String): JsonObject
}

internal class NeteaseSongAudioQualityRemoteDataSource(
    private val httpClient: JsonHttpClient
) : SongAudioQualityRemoteDataSource {
    override suspend fun fetchCatalog(songId: String): JsonObject {
        return httpClient.get(
            path = "/song/music/detail",
            queryParams = mapOf("id" to songId),
            requiresAuth = true
        )
    }
}
