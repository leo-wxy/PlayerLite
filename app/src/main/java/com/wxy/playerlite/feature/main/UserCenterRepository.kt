package com.wxy.playerlite.feature.main

import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.core.recentplayback.LocalRecentPlaybackStore
import com.wxy.playerlite.core.recentplayback.LocalRecentPlaybackRecord
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.feature.search.SearchRouteTarget
import com.wxy.playerlite.feature.song.SongRef
import com.wxy.playerlite.user.UserSessionInvalidException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    suspend fun fetchRecentSongs(limit: Int): List<UserCenterCollectionItemUiModel> = emptyList()

    suspend fun fetchRecentSongItems(limit: Int): List<RecentSongItemUiModel> = emptyList()

    suspend fun fetchLocalRecentPlaybackItems(limit: Int): List<RecentLocalPlaybackItemUiModel> =
        emptyList()

    suspend fun fetchRecentVideos(limit: Int): List<RecentPlaybackListItemUiModel> = emptyList()

    suspend fun fetchRecentVoices(limit: Int): List<RecentPlaybackListItemUiModel> = emptyList()

    suspend fun fetchRecentPlaylists(limit: Int): List<RecentPlaybackListItemUiModel> = emptyList()

    suspend fun fetchRecentAlbums(limit: Int): List<RecentPlaybackListItemUiModel> = emptyList()

    suspend fun fetchRecentDjRadios(limit: Int): List<RecentPlaybackListItemUiModel> = emptyList()
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

internal data class RecentSongItemUiModel(
    val id: String,
    val title: String,
    val artistText: String,
    val imageUrl: String?,
    val albumTitle: String? = null,
    val primaryArtistId: String? = null,
    val albumId: String? = null,
    val durationMs: Long = 0L,
    val detailAction: ContentEntryAction = ContentEntryAction.Unsupported()
) {
    fun toPlaylistItem(queueIndex: Int): PlaylistItem {
        return PlaylistItem(
            id = "recent-song:$queueIndex:$id",
            displayName = title,
            songId = id,
            title = title,
            artistText = artistText,
            primaryArtistId = primaryArtistId,
            albumId = albumId,
            albumTitle = albumTitle,
            coverUrl = imageUrl,
            durationMs = durationMs,
            itemType = PlaylistItemType.ONLINE,
            contextType = "recent_songs",
            contextId = "recent_songs",
            contextTitle = "最近播放"
        )
    }

    fun toCollectionItem(): UserCenterCollectionItemUiModel {
        return UserCenterCollectionItemUiModel(
            id = id,
            title = title,
            subtitle = artistText,
            imageUrl = imageUrl,
            meta = albumTitle,
            action = detailAction
        )
    }
}

internal data class RecentLocalPlaybackItemUiModel(
    val recordKey: String,
    val sourceType: PlaylistItemType,
    val songId: String?,
    val playbackUri: String,
    val title: String,
    val artistText: String,
    val imageUrl: String?,
    val albumTitle: String? = null,
    val primaryArtistId: String? = null,
    val albumId: String? = null,
    val durationMs: Long = 0L,
    val playedAtMs: Long = 0L
) {
    val id: String
        get() = "recent-cached-${recordKey.hashCode()}"

    fun toPlaylistItem(queueIndex: Int): PlaylistItem {
        return PlaylistItem(
            id = "recent-cached:$queueIndex:${recordKey.hashCode()}",
            uri = playbackUri,
            displayName = title,
            songId = songId,
            title = title,
            artistText = artistText,
            primaryArtistId = primaryArtistId,
            albumId = albumId,
            albumTitle = albumTitle,
            coverUrl = imageUrl,
            durationMs = durationMs,
            itemType = sourceType,
            contextType = "recent_local_cache",
            contextId = recordKey,
            contextTitle = "本机最近听歌"
        )
    }

    fun toSongRef(): SongRef {
        return if (!songId.isNullOrBlank()) {
            SongRef.Online(songId = songId)
        } else {
            SongRef.Local(
                playbackUri = playbackUri,
                title = title,
                artistText = artistText,
                albumTitle = albumTitle.orEmpty(),
                durationMs = durationMs,
                coverUrl = imageUrl
            )
        }
    }
}

internal data class RecentPlaybackListItemUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val meta: String? = null,
    val badge: String? = null
)

internal class DefaultUserCenterRepository(
    private val remoteDataSource: UserCenterRemoteDataSource,
    private val localRecentPlaybackStore: LocalRecentPlaybackStore
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
        return fetchRecentSongItems(limit).map { it.toCollectionItem() }
    }

    override suspend fun fetchRecentSongItems(limit: Int): List<RecentSongItemUiModel> {
        return UserCenterJsonMapper.parseRecentSongs(
            payload = remoteDataSource.fetchRecentSongs(limit)
        )
    }

    override suspend fun fetchLocalRecentPlaybackItems(limit: Int): List<RecentLocalPlaybackItemUiModel> {
        return localRecentPlaybackStore.read(limit).map(LocalRecentPlaybackRecord::toUiModel)
    }

    override suspend fun fetchRecentVideos(limit: Int): List<RecentPlaybackListItemUiModel> {
        return UserCenterJsonMapper.parseRecentVideos(
            payload = remoteDataSource.fetchRecentVideos(limit)
        )
    }

    override suspend fun fetchRecentVoices(limit: Int): List<RecentPlaybackListItemUiModel> {
        return UserCenterJsonMapper.parseRecentVoices(
            payload = remoteDataSource.fetchRecentVoices(limit)
        )
    }

    override suspend fun fetchRecentPlaylists(limit: Int): List<RecentPlaybackListItemUiModel> {
        return UserCenterJsonMapper.parseRecentPlaylists(
            payload = remoteDataSource.fetchRecentPlaylists(limit)
        )
    }

    override suspend fun fetchRecentAlbums(limit: Int): List<RecentPlaybackListItemUiModel> {
        return UserCenterJsonMapper.parseRecentAlbums(
            payload = remoteDataSource.fetchRecentAlbums(limit)
        )
    }

    override suspend fun fetchRecentDjRadios(limit: Int): List<RecentPlaybackListItemUiModel> {
        return UserCenterJsonMapper.parseRecentDjRadios(
            payload = remoteDataSource.fetchRecentDjRadios(limit)
        )
    }
}

private fun LocalRecentPlaybackRecord.toUiModel(): RecentLocalPlaybackItemUiModel {
    return RecentLocalPlaybackItemUiModel(
        recordKey = recordKey,
        sourceType = sourceType,
        songId = songId,
        playbackUri = playbackUri.orEmpty(),
        title = title,
        artistText = artistText,
        imageUrl = coverUrl,
        albumTitle = albumTitle,
        primaryArtistId = primaryArtistId,
        albumId = albumId,
        durationMs = durationMs,
        playedAtMs = playedAtMs
    )
}

internal interface UserCenterRemoteDataSource {
    suspend fun fetchFavoriteArtists(): JsonObject

    suspend fun fetchFavoriteColumns(): JsonObject

    suspend fun fetchFavoriteMvs(): JsonObject

    suspend fun fetchCreatedPlaylists(userId: Long): JsonObject

    suspend fun fetchCollectedPlaylists(userId: Long): JsonObject

    suspend fun fetchUserPlaylists(userId: Long): JsonObject

    suspend fun fetchRecentSongs(limit: Int): JsonObject

    suspend fun fetchRecentVideos(limit: Int): JsonObject

    suspend fun fetchRecentVoices(limit: Int): JsonObject

    suspend fun fetchRecentPlaylists(limit: Int): JsonObject

    suspend fun fetchRecentAlbums(limit: Int): JsonObject

    suspend fun fetchRecentDjRadios(limit: Int): JsonObject
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

    override suspend fun fetchRecentVideos(limit: Int): JsonObject {
        return httpClient.get(
            path = "/record/recent/video",
            queryParams = mapOf("limit" to limit.toString()),
            requiresAuth = true
        ).also(::ensureSuccess)
    }

    override suspend fun fetchRecentVoices(limit: Int): JsonObject {
        return httpClient.get(
            path = "/record/recent/voice",
            queryParams = mapOf("limit" to limit.toString()),
            requiresAuth = true
        ).also(::ensureSuccess)
    }

    override suspend fun fetchRecentPlaylists(limit: Int): JsonObject {
        return httpClient.get(
            path = "/record/recent/playlist",
            queryParams = mapOf("limit" to limit.toString()),
            requiresAuth = true
        ).also(::ensureSuccess)
    }

    override suspend fun fetchRecentAlbums(limit: Int): JsonObject {
        return httpClient.get(
            path = "/record/recent/album",
            queryParams = mapOf("limit" to limit.toString()),
            requiresAuth = true
        ).also(::ensureSuccess)
    }

    override suspend fun fetchRecentDjRadios(limit: Int): JsonObject {
        return httpClient.get(
            path = "/record/recent/dj",
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

    fun parseRecentSongs(payload: JsonObject): List<RecentSongItemUiModel> {
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
            val primaryArtistId = song.arrayValue("ar").firstObject().stringValue("id")
                ?: song.arrayValue("artists").firstObject().stringValue("id")
            val albumName = song.objectValue("al").stringValue("name")
                ?: song.objectValue("album").stringValue("name")
            val albumId = song.objectValue("al").stringValue("id")
                ?: song.objectValue("album").stringValue("id")
            val coverUrl = song.objectValue("al").stringValue("picUrl")
                ?: song.objectValue("album").stringValue("picUrl")
                ?: song.stringValue("picUrl")
                ?: song.stringValue("coverUrl")
            RecentSongItemUiModel(
                id = id,
                title = title,
                artistText = if (artists.isEmpty()) "歌曲" else artists.joinToString(" / "),
                imageUrl = coverUrl,
                albumTitle = albumName,
                primaryArtistId = primaryArtistId,
                albumId = albumId,
                durationMs = song.longValue("dt"),
                detailAction = ContentEntryAction.OpenDetail(
                    SearchRouteTarget.Song(songId = id)
                )
            )
        }
    }

    fun parseRecentVideos(payload: JsonObject): List<RecentPlaybackListItemUiModel> {
        return parseRecentGenericItems(payload) { item ->
            val video = item.recentDataObject()
            RecentPlaybackListItemUiModel(
                id = video.stringValue("id") ?: return@parseRecentGenericItems null,
                title = video.stringValue("title")
                    ?: video.stringValue("name")
                    ?: return@parseRecentGenericItems null,
                subtitle = video.stringValue("creatorName")
                    ?: video.objectValue("creator").stringValue("nickname")
                    ?: video.stringValue("artistName")
                    ?: "视频",
                imageUrl = video.stringValue("coverUrl")
                    ?: video.stringValue("cover")
                    ?: video.stringValue("picUrl"),
                meta = video.longValue("playTime").takeIf { it > 0 }?.let { "$it 播放" }
                    ?: video.stringValue("durationms")
                    ?: video.stringValue("type"),
                badge = video.stringValue("type") ?: "视频"
            )
        }
    }

    fun parseRecentVoices(payload: JsonObject): List<RecentPlaybackListItemUiModel> {
        return parseRecentGenericItems(payload) { item ->
            val voice = item.recentDataObject()
            RecentPlaybackListItemUiModel(
                id = voice.stringValue("id") ?: return@parseRecentGenericItems null,
                title = voice.stringValue("title")
                    ?: voice.stringValue("name")
                    ?: return@parseRecentGenericItems null,
                subtitle = voice.objectValue("dj").stringValue("nickname")
                    ?: voice.stringValue("author")
                    ?: voice.stringValue("creatorName")
                    ?: "声音",
                imageUrl = voice.stringValue("coverUrl")
                    ?: voice.stringValue("picUrl")
                    ?: voice.stringValue("cover"),
                meta = voice.stringValue("category")
                    ?: voice.intValue("programCount").takeIf { it > 0 }?.let { "$it 期" },
                badge = "声音"
            )
        }
    }

    fun parseRecentPlaylists(payload: JsonObject): List<RecentPlaybackListItemUiModel> {
        return parseRecentGenericItems(payload) { item ->
            val playlist = item.recentDataObject()
            RecentPlaybackListItemUiModel(
                id = playlist.stringValue("id") ?: return@parseRecentGenericItems null,
                title = playlist.stringValue("name")
                    ?: playlist.stringValue("title")
                    ?: return@parseRecentGenericItems null,
                subtitle = playlist.objectValue("creator").stringValue("nickname")
                    ?: playlist.stringValue("creatorName")
                    ?: "歌单",
                imageUrl = playlist.stringValue("coverImgUrl")
                    ?: playlist.stringValue("coverUrl")
                    ?: playlist.stringValue("picUrl"),
                meta = playlist.intValue("trackCount").takeIf { it > 0 }?.let { "$it 首歌曲" },
                badge = "歌单"
            )
        }
    }

    fun parseRecentAlbums(payload: JsonObject): List<RecentPlaybackListItemUiModel> {
        return parseRecentGenericItems(payload) { item ->
            val album = item.recentDataObject()
            val artistText = album.arrayValue("artists").mapNotNull { artist ->
                (artist as? JsonObject)?.stringValue("name")
            }.ifEmpty {
                album.arrayValue("ar").mapNotNull { artist ->
                    (artist as? JsonObject)?.stringValue("name")
                }
            }.joinToString(" / ")
            RecentPlaybackListItemUiModel(
                id = album.stringValue("id") ?: return@parseRecentGenericItems null,
                title = album.stringValue("name")
                    ?: album.stringValue("title")
                    ?: return@parseRecentGenericItems null,
                subtitle = artistText.ifBlank { album.stringValue("artistName") ?: "专辑" },
                imageUrl = album.stringValue("picUrl")
                    ?: album.stringValue("coverUrl")
                    ?: album.stringValue("blurPicUrl"),
                meta = buildList {
                    album.longValue("publishTime")
                        .takeIf { it > 0 }
                        ?.let(::formatRecentAlbumPublishDate)
                        ?.let(::add)
                    album.intValue("size").takeIf { it > 0 }?.let { add("$it 首") }
                }.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                badge = "专辑"
            )
        }
    }

    fun parseRecentDjRadios(payload: JsonObject): List<RecentPlaybackListItemUiModel> {
        return parseRecentGenericItems(payload) { item ->
            val dj = item.recentDataObject()
            RecentPlaybackListItemUiModel(
                id = dj.stringValue("id") ?: return@parseRecentGenericItems null,
                title = dj.stringValue("name")
                    ?: dj.stringValue("title")
                    ?: return@parseRecentGenericItems null,
                subtitle = dj.objectValue("dj").stringValue("nickname")
                    ?: dj.stringValue("djNickname")
                    ?: "播客",
                imageUrl = dj.stringValue("picUrl")
                    ?: dj.stringValue("coverUrl")
                    ?: dj.stringValue("cover"),
                meta = dj.intValue("programCount").takeIf { it > 0 }?.let { "$it 期节目" }
                    ?: dj.stringValue("category"),
                badge = "播客"
            )
        }
    }

    private inline fun parseRecentGenericItems(
        payload: JsonObject,
        mapper: (JsonObject) -> RecentPlaybackListItemUiModel?
    ): List<RecentPlaybackListItemUiModel> {
        val list = payload.objectValue("data").arrayValue("list")
        return list.mapNotNull { element ->
            mapper(element as? JsonObject ?: return@mapNotNull null)
        }
    }
}

private fun formatRecentAlbumPublishDate(timestampMs: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampMs))
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

private fun JsonObject.recentDataObject(): JsonObject {
    val data = this["data"] as? JsonObject
    if (data != null) {
        return data
    }
    val resource = this["resource"] as? JsonObject
    return resource ?: this
}

private fun JsonObject.arrayValue(key: String): JsonArray {
    return this[key]?.jsonArray ?: emptyJsonArray
}

private fun JsonArray.firstObject(): JsonObject {
    return firstOrNull() as? JsonObject ?: emptyJsonObject
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
