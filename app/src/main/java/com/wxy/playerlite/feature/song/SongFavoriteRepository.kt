package com.wxy.playerlite.feature.song

import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.user.UserSessionInvalidException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal interface SongFavoriteRepository {
    suspend fun favoriteSong(songId: String): Result<Unit>
}

internal class DefaultSongFavoriteRepository(
    private val remoteDataSource: SongFavoriteRemoteDataSource
) : SongFavoriteRepository {
    override suspend fun favoriteSong(songId: String): Result<Unit> {
        return runCatching {
            remoteDataSource.favoriteSong(songId)
        }
    }
}

internal interface SongFavoriteRemoteDataSource {
    suspend fun favoriteSong(songId: String)
}

internal class NeteaseSongFavoriteRemoteDataSource(
    private val httpClient: JsonHttpClient
) : SongFavoriteRemoteDataSource {
    override suspend fun favoriteSong(songId: String) {
        val payload = httpClient.get(
            path = "/like",
            queryParams = mapOf(
                "id" to songId,
                "like" to "true"
            ),
            requiresAuth = true
        )
        ensureSuccess(payload)
    }

    private fun ensureSuccess(payload: JsonObject) {
        val code = payload.intValue("code")
        if (code == 200) {
            return
        }
        val message = payload.stringValue("message")
            ?: payload.stringValue("msg")
            ?: "Request failed($code)"
        if (code == 301 || code == 302) {
            throw UserSessionInvalidException(message)
        }
        throw IllegalStateException(message)
    }
}

private fun JsonObject.stringValue(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.intValue(key: String): Int {
    return stringValue(key)?.toIntOrNull() ?: 0
}
