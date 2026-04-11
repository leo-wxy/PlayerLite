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

    // Keep source compatibility for other test fakes: they don't need to implement this new method.
    suspend fun fetchFavoriteMvs(): List<UserCenterCollectionItemUiModel> = emptyList()

    suspend fun fetchCreatedPlaylists(userId: Long): List<UserCenterCollectionItemUiModel> =
        fetchUserPlaylists(userId)

    suspend fun fetchCollectedPlaylists(userId: Long): List<UserCenterCollectionItemUiModel> = emptyList()

    suspend fun fetchUserPlaylists(userId: Long): List<UserCenterCollectionItemUiModel>

    suspend fun fetchLikedPlaylist(userId: Long): UserCenterCollectionItemUiModel?

    suspend fun fetchRecentSongs(limit: Int): List<UserCenterCollectionItemUiModel>
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

    override suspend fun fetchFavoriteMvs(): List<UserCenterCollectionItemUiModel> {
        return UserCenterJsonMapper.parseFavoriteMvs(
            payload = remoteDataSource.fetchFavoriteMvs()
        )
    }

    override suspend fun fetchCreatedPlaylists(userId: Long): List<UserCenterCollectionItemUiModel> {
        return UserCenterJsonMapper.parsePlaylistList(
            payload = remoteDataSource.fetchCreatedPlaylists(userId)
        )
    }

    override suspend fun fetchCollectedPlaylists(userId: Long): List<UserCenterCollectionItemUiModel> {
        return UserCenterJsonMapper.parsePlaylistList(
            payload = remoteDataSource.fetchCollectedPlaylists(userId)
        )
    }

    override suspend fun fetchUserPlaylists(userId: Long): List<UserCenterCollectionItemUiModel> {
        return UserCenterJsonMapper.parseUserPlaylists(
            payload = remoteDataSource.fetchUserPlaylists(userId),
            userId = userId
        )
    }

    override suspend fun fetchLikedPlaylist(userId: Long): UserCenterCollectionItemUiModel? {
        return UserCenterJsonMapper.parseLikedPlaylist(
            payload = remoteDataSource.fetchUserPlaylists(userId),
            userId = userId
        )
    }

    override suspend fun fetchRecentSongs(limit: Int): List<UserCenterCollectionItemUiModel> {
        return UserCenterJsonMapper.parseRecentSongs(
            payload = remoteDataSource.fetchRecentSongs(limit)
        )
    }
}

internal interface UserCenterRemoteDataSource {
    suspend fun fetchFavoriteArtists(): JsonObject

    suspend fun fetchFavoriteColumns(): JsonObject

    suspend fun fetchFavoriteMvs(): JsonObject

    suspend fun fetchCreatedPlaylists(userId: Long): JsonObject

    suspend fun fetchCollectedPlaylists(userId: Long): JsonObject

    suspend fun fetchUserPlaylists(userId: Long): JsonObject

    suspend fun fetchRecentSongs(limit: Int): JsonObject
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
            path = "/topic/sublist",
            requiresAuth = true
        ).also(::ensureSuccess)
    }

    override suspend fun fetchFavoriteMvs(): JsonObject {
        return httpClient.get(
            path = "/mv/sublist",
            requiresAuth = true
        ).also(::ensureSuccess)
    }

    override suspend fun fetchCreatedPlaylists(userId: Long): JsonObject {
        return httpClient.get(
            path = "/user/playlist/create",
            queryParams = mapOf("uid" to userId.toString()),
            requiresAuth = true
        ).also(::ensureSuccess)
    }

    override suspend fun fetchCollectedPlaylists(userId: Long): JsonObject {
        return httpClient.get(
            path = "/user/playlist/collect",
            queryParams = mapOf("uid" to userId.toString()),
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

    override suspend fun fetchRecentSongs(limit: Int): JsonObject {
        return httpClient.get(
            path = "/record/recent/song",
            queryParams = mapOf("limit" to limit.toString()),
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
        val topics = payload.arrayValue("data")
        if (topics.isNotEmpty()) {
            return topics.mapNotNull { element ->
                val topic = element as? JsonObject ?: return@mapNotNull null
                val creator = topic.objectValue("creator")
                UserCenterCollectionItemUiModel(
                    id = topic.stringValue("id").orEmpty(),
                    title = topic.stringValue("title")
                        ?: topic.stringValue("name")
                        ?: "",
                    subtitle = creator.stringValue("nickname")
                        ?: creator.stringValue("name")
                        ?: topic.stringValue("category")
                        ?: "专栏",
                    imageUrl = topic.stringValue("coverUrl")
                        ?: topic.stringValue("cover")
                        ?: topic.stringValue("picUrl"),
                    badge = topic.stringValue("category"),
                    meta = topic.intValue("subCount").takeIf { it > 0 }?.let { "$it 人收藏" }
                        ?: topic.intValue("readCount").takeIf { it > 0 }?.let { "$it 阅读" },
                    action = ContentEntryAction.Unsupported(UnsupportedColumnDetailMessage)
                )
            }
        }

        return payload.arrayValue("djRadios").mapNotNull { element ->
            val radio = element as? JsonObject ?: return@mapNotNull null
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

    fun parseFavoriteMvs(payload: JsonObject): List<UserCenterCollectionItemUiModel> {
        val list = payload.arrayValue("data").ifEmpty { payload.arrayValue("mvs") }
        return list.mapNotNull { element ->
            val mv = element as? JsonObject ?: return@mapNotNull null
            val playCount = mv.longValue("playCount")
            UserCenterCollectionItemUiModel(
                id = mv.stringValue("id").orEmpty(),
                title = mv.stringValue("name")
                    ?: mv.stringValue("title")
                    ?: "",
                subtitle = mv.stringValue("artistName")
                    ?: mv.objectValue("artist").stringValue("name")
                    ?: "MV",
                imageUrl = mv.stringValue("cover")
                    ?: mv.stringValue("coverUrl")
                    ?: mv.stringValue("imgurl")
                    ?: mv.stringValue("picUrl"),
                meta = playCount.takeIf { it > 0 }?.let { "$it 播放" },
                action = ContentEntryAction.Unsupported(message = "当前版本暂不支持打开收藏 MV")
            )
        }
    }

    fun parsePlaylistList(payload: JsonObject): List<UserCenterCollectionItemUiModel> {
        return payload.arrayValue("playlist").mapNotNull { element ->
            val playlist = element as? JsonObject ?: return@mapNotNull null
            playlist.toPlaylistItem()
        }
    }

    fun parseUserPlaylists(
        payload: JsonObject,
        userId: Long
    ): List<UserCenterCollectionItemUiModel> {
        return payload.arrayValue("playlist").mapNotNull { element ->
            val playlist = element as JsonObject
            val creator = playlist.objectValue("creator")
            if (creator.longValue("userId") != userId) {
                return@mapNotNull null
            }
            playlist.toPlaylistItem(creator)
        }
    }

    fun parseLikedPlaylist(
        payload: JsonObject,
        userId: Long
    ): UserCenterCollectionItemUiModel? {
        return payload.arrayValue("playlist").firstNotNullOfOrNull { element ->
            val playlist = element as? JsonObject ?: return@firstNotNullOfOrNull null
            val creator = playlist.objectValue("creator")
            if (creator.longValue("userId") != userId || !playlist.isLikedPlaylist()) {
                return@firstNotNullOfOrNull null
            }
            playlist.toPlaylistItem(creator)
        }
    }

    fun parseRecentSongs(payload: JsonObject): List<UserCenterCollectionItemUiModel> {
        val list = payload.objectValue("data").arrayValue("list")
        return list.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val song = (item["data"] as? JsonObject) ?: item
            val id = song.stringValue("id") ?: return@mapNotNull null
            val title = song.stringValue("name") ?: return@mapNotNull null
            val artists = song.arrayValue("ar").mapNotNull { artistElement ->
                (artistElement as? JsonObject)?.stringValue("name")
            }.ifEmpty {
                song.arrayValue("artists").mapNotNull { artistElement ->
                    (artistElement as? JsonObject)?.stringValue("name")
                }
            }
            val albumName = song.objectValue("al").stringValue("name")
                ?: song.objectValue("album").stringValue("name")
            val coverUrl = song.objectValue("al").stringValue("picUrl")
                ?: song.objectValue("album").stringValue("picUrl")
                ?: song.stringValue("picUrl")
                ?: song.stringValue("coverUrl")
            UserCenterCollectionItemUiModel(
                id = id,
                title = title,
                subtitle = if (artists.isEmpty()) "歌曲" else artists.joinToString(" / "),
                imageUrl = coverUrl,
                meta = albumName,
                action = ContentEntryAction.OpenDetail(
                    SearchRouteTarget.Song(songId = id)
                )
            )
        }
    }
}

private fun JsonObject.toPlaylistItem(
    creator: JsonObject = objectValue("creator")
): UserCenterCollectionItemUiModel {
    return UserCenterCollectionItemUiModel(
        id = stringValue("id").orEmpty(),
        title = stringValue("name").orEmpty(),
        subtitle = creator.stringValue("nickname") ?: "歌单",
        imageUrl = stringValue("coverImgUrl"),
        meta = intValue("trackCount").takeIf { it > 0 }?.let { "$it 首歌曲" },
        action = stringValue("id")
            ?.takeIf { it.isNotBlank() }
            ?.let { id ->
                ContentEntryAction.OpenDetail(
                    SearchRouteTarget.Playlist(playlistId = id)
                )
            }
            ?: ContentEntryAction.Unsupported()
    )
}

private fun JsonObject.isLikedPlaylist(): Boolean {
    return intValue("specialType") == 5 ||
        stringValue("name")?.contains("喜欢的音乐") == true
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

private fun JsonObject.longValue(key: String): Long {
    return stringValue(key)?.toLongOrNull()
        ?: this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        ?: 0L
}

private fun JsonArray.firstString(): String? {
    return firstOrNull()?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())
