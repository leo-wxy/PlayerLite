package com.wxy.playerlite.feature.main

import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.feature.search.SearchRouteTarget
import com.wxy.playerlite.user.UserSessionInvalidException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal interface UserCenterRepository {
    suspend fun fetchFavoriteArtists(): List<UserCenterCollectionItemUiModel>

    suspend fun fetchFavoriteColumns(): List<UserCenterCollectionItemUiModel>

    suspend fun fetchUserPlaylists(userId: Long): List<UserCenterCollectionItemUiModel>
}

internal data class UserCenterCollectionItemUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val badge: String? = null,
    val meta: String? = null,
    val action: ContentEntryAction = ContentEntryAction.Unsupported()
)

internal class DefaultUserCenterRepository(
    private val remoteDataSource: UserCenterRemoteDataSource
) : UserCenterRepository {
    override suspend fun fetchFavoriteArtists(): List<UserCenterCollectionItemUiModel> {
        return UserCenterJsonMapper.parseFavoriteArtists(
            payload = remoteDataSource.fetchFavoriteArtists()
        )
    }

    override suspend fun fetchFavoriteColumns(): List<UserCenterCollectionItemUiModel> {
        return UserCenterJsonMapper.parseFavoriteColumns(
            payload = remoteDataSource.fetchFavoriteColumns()
        )
    }

    override suspend fun fetchUserPlaylists(userId: Long): List<UserCenterCollectionItemUiModel> {
        return UserCenterJsonMapper.parseUserPlaylists(
            payload = remoteDataSource.fetchUserPlaylists(userId)
        )
    }
}

internal interface UserCenterRemoteDataSource {
    suspend fun fetchFavoriteArtists(): JsonObject

    suspend fun fetchFavoriteColumns(): JsonObject

    suspend fun fetchUserPlaylists(userId: Long): JsonObject
}

internal class NeteaseUserCenterRemoteDataSource(
    private val httpClient: JsonHttpClient
) : UserCenterRemoteDataSource {
    override suspend fun fetchFavoriteArtists(): JsonObject {
        return httpClient.get(
            path = "/artist/sublist",
            requiresAuth = true
        ).also(::ensureSuccess)
    }

    override suspend fun fetchFavoriteColumns(): JsonObject {
        return httpClient.get(
            path = "/dj/sublist",
            requiresAuth = true
        ).also(::ensureSuccess)
    }

    override suspend fun fetchUserPlaylists(userId: Long): JsonObject {
        return httpClient.get(
            path = "/user/playlist",
            queryParams = mapOf("uid" to userId.toString()),
            requiresAuth = true
        ).also(::ensureSuccess)
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

internal object UserCenterJsonMapper {
    fun parseFavoriteArtists(payload: JsonObject): List<UserCenterCollectionItemUiModel> {
        return payload.arrayValue("data").map { element ->
            val artist = element as JsonObject
            UserCenterCollectionItemUiModel(
                id = artist.stringValue("id").orEmpty(),
                title = artist.stringValue("name").orEmpty(),
                subtitle = artist.arrayValue("alias").firstString()
                    ?: artist.stringValue("trans")
                    ?: "歌手",
                imageUrl = artist.stringValue("picUrl"),
                meta = artist.intValue("albumSize").takeIf { it > 0 }?.let { "$it 张专辑" },
                action = artist.stringValue("id")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { id ->
                        ContentEntryAction.OpenDetail(
                            SearchRouteTarget.Artist(artistId = id)
                        )
                    }
                    ?: ContentEntryAction.Unsupported()
            )
        }
    }

    fun parseFavoriteColumns(payload: JsonObject): List<UserCenterCollectionItemUiModel> {
        return payload.arrayValue("djRadios").map { element ->
            val radio = element as JsonObject
            UserCenterCollectionItemUiModel(
                id = radio.stringValue("id").orEmpty(),
                title = radio.stringValue("name").orEmpty(),
                subtitle = radio.objectValue("dj").stringValue("nickname")
                    ?: radio.stringValue("category")
                    ?: "专栏",
                imageUrl = radio.stringValue("picUrl"),
                badge = radio.stringValue("category"),
                meta = radio.intValue("programCount").takeIf { it > 0 }?.let { "$it 期节目" },
                action = ContentEntryAction.Unsupported(UnsupportedColumnDetailMessage)
            )
        }
    }

    fun parseUserPlaylists(payload: JsonObject): List<UserCenterCollectionItemUiModel> {
        return payload.arrayValue("playlist").map { element ->
            val playlist = element as JsonObject
            UserCenterCollectionItemUiModel(
                id = playlist.stringValue("id").orEmpty(),
                title = playlist.stringValue("name").orEmpty(),
                subtitle = playlist.objectValue("creator").stringValue("nickname") ?: "歌单",
                imageUrl = playlist.stringValue("coverImgUrl"),
                meta = playlist.intValue("trackCount").takeIf { it > 0 }?.let { "$it 首歌曲" },
                action = playlist.stringValue("id")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { id ->
                        ContentEntryAction.OpenDetail(
                            SearchRouteTarget.Playlist(playlistId = id)
                        )
                    }
                    ?: ContentEntryAction.Unsupported()
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
    return this[key]?.jsonPrimitive?.intOrNull
        ?: stringValue(key)?.toIntOrNull()
        ?: 0
}

private fun JsonArray.firstString(): String? {
    return firstOrNull()?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())
