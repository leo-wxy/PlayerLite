package com.wxy.playerlite.feature.home

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.network.core.JsonHttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface HomeDiscoveryRepository {
    suspend fun fetchHomeOverview(): HomeOverviewContent
}

object HomeFeatureServiceFactory {
    fun createRepository(httpClient: JsonHttpClient): HomeDiscoveryRepository {
        return DefaultHomeDiscoveryRepository(
            remoteDataSource = NeteaseHomeDiscoveryRemoteDataSource(httpClient)
        )
    }
}

internal class DefaultHomeDiscoveryRepository(
    private val remoteDataSource: HomeDiscoveryRemoteDataSource
) : HomeDiscoveryRepository {
    override suspend fun fetchHomeOverview(): HomeOverviewContent {
        val homepagePayload = remoteDataSource.fetchHomepageBlocks()
        val sections = NeteaseHomeDiscoveryJsonMapper.parseSections(homepagePayload)
        val searchKeywords = runCatching {
            NeteaseHomeDiscoveryJsonMapper.parseSearchKeywords(remoteDataSource.fetchDefaultSearch())
        }.getOrElse {
            HomeDefaults.fallbackSearchKeywords
        }.ifEmpty {
            HomeDefaults.fallbackSearchKeywords
        }
        return HomeOverviewContent(
            sections = sections,
            searchKeywords = searchKeywords
        )
    }
}

internal interface HomeDiscoveryRemoteDataSource {
    suspend fun fetchHomepageBlocks(): JsonObject

    suspend fun fetchDefaultSearch(): JsonObject
}

internal class NeteaseHomeDiscoveryRemoteDataSource(
    private val httpClient: JsonHttpClient
) : HomeDiscoveryRemoteDataSource {
    override suspend fun fetchHomepageBlocks(): JsonObject {
        return httpClient.get(path = "/homepage/block/page")
    }

    override suspend fun fetchDefaultSearch(): JsonObject {
        return httpClient.get(path = "/search/default")
    }
}

internal object NeteaseHomeDiscoveryJsonMapper {
    fun parseSections(payload: JsonObject): List<HomeSectionUiModel> {
        return payload.objectValue("data")
            .arrayValue("blocks")
            .mapNotNull { element ->
                element.jsonObject.toSection()
            }
    }

    fun parseSearchKeywords(payload: JsonObject): List<String> {
        val data = payload.objectValue("data")
        return buildList {
            addIfNotBlank(data.stringValue("showKeyword"))
            addIfNotBlank(data.objectValue("styleKeyword").stringValue("keyWord"))
            addIfNotBlank(data.stringValue("realkeyword"))
        }.distinct()
    }

    private fun JsonObject.toSection(): HomeSectionUiModel? {
        val code = stringValue("blockCode").orEmpty()
        val showType = stringValue("showType").orEmpty()
        return when (showType) {
            "BANNER" -> parseBannerSection(code)
            "DRAGON_BALL" -> parseResourceSection(
                code = code,
                title = objectValue("uiElement").objectValue("subTitle").stringValue("title").orEmpty(),
                layout = HomeSectionLayout.ICON_GRID
            )

            else -> parseResourceSection(
                code = code,
                title = objectValue("uiElement").objectValue("subTitle").stringValue("title").orEmpty(),
                layout = HomeSectionLayout.HORIZONTAL_LIST
            )
        }?.takeIf { it.items.isNotEmpty() }
    }

    private fun JsonObject.parseBannerSection(code: String): HomeSectionUiModel? {
        val items = objectValue("extInfo")
            .arrayValue("banners")
            .mapNotNull { element ->
                val banner = element.jsonObject
                val title = banner.stringValue("mainTitle")
                    ?: banner.stringValue("typeTitle")
                    ?: return@mapNotNull null
                HomeSectionItemUiModel(
                    id = banner.stringValue("bannerId") ?: title,
                    title = title,
                    subtitle = banner.stringValue("url").orEmpty(),
                    imageUrl = banner.stringValue("pic"),
                    badge = banner.stringValue("typeTitle"),
                    action = HomeAction.OpenContent(
                        banner.toHomeContentTarget()
                    )
                )
            }
        return HomeSectionUiModel(
            code = code,
            title = "",
            layout = HomeSectionLayout.BANNER,
            items = items
        )
    }

    private fun JsonObject.parseResourceSection(
        code: String,
        title: String,
        layout: HomeSectionLayout
    ): HomeSectionUiModel? {
        val resources = arrayValue("creatives")
            .flatMap { creative ->
                creative.jsonObject.arrayValue("resources").map { it.jsonObject }
            }
        val songPlaybackContext = resources.toSongPlaybackContext(
            sectionCode = code,
            sectionTitle = title
        )
        val items = resources.mapNotNull { resource ->
            resource.toResourceItem(
                defaultTargetType = code.toDefaultDetailType(),
                songPlaybackContext = songPlaybackContext
            )
        }
        return HomeSectionUiModel(
            code = code,
            title = title,
            layout = layout,
            items = items
        )
    }

    private fun List<JsonObject>.toSongPlaybackContext(
        sectionCode: String,
        sectionTitle: String
    ): MappedHomeSongPlaybackContext {
        val mappedSongs = mapNotNull { resource ->
            resource.toMappedSongResource(
                sectionCode = sectionCode,
                sectionTitle = sectionTitle
            )
        }
        val playbackItems = mappedSongs.map { it.playlistItem }
        val activeIndexByResourceId = mappedSongs.mapIndexed { index, song ->
            song.resourceId to index
        }.toMap()
        return MappedHomeSongPlaybackContext(
            songByResourceId = mappedSongs.associateBy(MappedHomeSongResource::resourceId),
            playbackItems = playbackItems,
            activeIndexByResourceId = activeIndexByResourceId
        )
    }

    private fun JsonObject.toResourceItem(
        defaultTargetType: HomeDetailType?,
        songPlaybackContext: MappedHomeSongPlaybackContext
    ): HomeSectionItemUiModel? {
        val uiElement = objectValue("uiElement")
        val title = uiElement.objectValue("mainTitle").stringValue("title") ?: return null
        val subTitle = uiElement.objectValue("subTitle").stringValue("title").orEmpty()
        val labels = uiElement.arrayValue("labelTexts").mapNotNull { it.jsonPrimitive.contentOrNull }
        val mappedSong = stringValue("resourceId")
            ?.let(songPlaybackContext.songByResourceId::get)
        val action = if (mappedSong != null && songPlaybackContext.playbackItems.isNotEmpty()) {
            HomeAction.ReplaceQueueAndOpenPlayer(
                items = songPlaybackContext.playbackItems,
                activeIndex = songPlaybackContext.activeIndexByResourceId[mappedSong.resourceId] ?: 0
            )
        } else if (isDailyRecommendedShortcut(title = title)) {
            HomeAction.OpenContent(HomeContentTarget.DailyRecommendedSongs)
        } else {
            HomeAction.OpenContent(
                toHomeContentTarget(defaultTargetType = defaultTargetType)
            )
        }
        return HomeSectionItemUiModel(
            id = stringValue("resourceId") ?: title,
            title = title,
            subtitle = subTitle.ifBlank { labels.joinToString(separator = " · ") },
            imageUrl = uiElement.objectValue("image").stringValue("imageUrl"),
            badge = labels.firstOrNull(),
            action = action,
            songCard = mappedSong?.songCard
        )
    }

    private fun JsonObject.toMappedSongResource(
        sectionCode: String,
        sectionTitle: String
    ): MappedHomeSongResource? {
        if (!isSongResource()) {
            return null
        }
        val songId = stringValue("resourceId") ?: return null
        val uiElement = objectValue("uiElement")
        val title = uiElement.objectValue("mainTitle").stringValue("title") ?: return null
        val recommendReason = uiElement.objectValue("subTitle").stringValue("title")
            ?: objectValue("resourceExtInfo").stringValue("recommendReason")
            ?: objectValue("resourceExtInfo").stringValue("reason")
        val artists = resolveSongArtists()
        val artistText = artists.mapNotNull { it.stringValue("name") }
            .joinToString(separator = " / ")
            .ifBlank { "歌曲" }
        val primaryArtistId = artists.firstOrNull()?.stringValue("id")
        val album = resolveSongAlbum()
        val albumId = album.stringValue("id")
        val albumTitle = album.stringValue("name")
        val coverUrl = album.stringValue("picUrl")
            ?: uiElement.objectValue("image").stringValue("imageUrl")
        val durationMs = resolveSongDurationMs()
        val playlistItem = PlaylistItem(
            id = "home:$sectionCode:$songId",
            displayName = title,
            songId = songId,
            title = title,
            artistText = artistText,
            primaryArtistId = primaryArtistId,
            albumTitle = albumTitle,
            coverUrl = coverUrl,
            durationMs = durationMs,
            contextType = "homepage_song_block",
            contextId = sectionCode,
            contextTitle = sectionTitle
        )
        val menuActions = buildList {
            add(
                HomeSongMenuActionUiModel(
                    key = "insert_next",
                    label = "下一首播放",
                    action = HomeAction.InsertNext(playlistItem)
                )
            )
            add(
                HomeSongMenuActionUiModel(
                    key = "open_song_detail",
                    label = "查看歌曲详情",
                    action = HomeAction.OpenContent(
                        HomeContentTarget.Song(songId = songId)
                    )
                )
            )
            if (!primaryArtistId.isNullOrBlank()) {
                add(
                    HomeSongMenuActionUiModel(
                        key = "open_artist",
                        label = "查看歌手",
                        action = HomeAction.OpenContent(
                            HomeContentTarget.Artist(artistId = primaryArtistId)
                        )
                    )
                )
            }
            if (!albumId.isNullOrBlank()) {
                add(
                    HomeSongMenuActionUiModel(
                        key = "open_album",
                        label = "查看专辑",
                        action = HomeAction.OpenContent(
                            HomeContentTarget.Album(albumId = albumId)
                        )
                    )
                )
            }
        }
        return MappedHomeSongResource(
            resourceId = songId,
            playlistItem = playlistItem,
            songCard = HomeSongCardUiModel(
                metadataLine = buildSongMetadataLine(
                    artistText = artistText,
                    albumTitle = albumTitle
                ),
                recommendReason = recommendReason,
                durationMs = durationMs,
                menuActions = menuActions
            )
        )
    }

    private fun JsonObject.isDailyRecommendedShortcut(title: String): Boolean {
        if (!title.contains("每日推荐")) {
            return false
        }
        return listOfNotNull(
            stringValue("action"),
            stringValue("targetUrl"),
            stringValue("resourceUrl"),
            objectValue("action").stringValue("url"),
            objectValue("action").stringValue("orpheus"),
            objectValue("action").stringValue("orpheusUrl")
        ).any { candidate ->
            candidate.contains("songrcmd", ignoreCase = true)
        }
    }

    private fun JsonObject.toHomeContentTarget(
        defaultTargetType: HomeDetailType? = null
    ): HomeContentTarget {
        val routeTarget = resolveContentTarget(defaultTargetType = defaultTargetType)
        if (routeTarget != null) {
            return routeTarget
        }

        val uri = resolveUriCandidate()
        if (uri != null) {
            return HomeContentTarget.ExternalUri(uri = uri)
        }

        return HomeContentTarget.Unsupported()
    }

    private fun JsonObject.resolveContentTarget(
        defaultTargetType: HomeDetailType?
    ): HomeContentTarget? {
        val candidates = listOf(
            this,
            objectValue("resourceExtInfo"),
            objectValue("creativeExtInfo"),
            objectValue("action")
        )
        candidates.forEach { candidate ->
            val explicitType = candidate.toHomeDetailType()
            val explicitId = candidate.routeIdCandidate()
            if (explicitType != null && !explicitId.isNullOrBlank()) {
                return explicitType.toContentTarget(explicitId)
            }
        }

        val fallbackId = routeIdCandidate()
        if (defaultTargetType != null && !fallbackId.isNullOrBlank()) {
            return defaultTargetType.toContentTarget(fallbackId)
        }
        return null
    }

    private fun JsonObject.resolveUriCandidate(): String? {
        val directCandidates = listOf(
            stringValue("url"),
            stringValue("jumpUrl"),
            stringValue("resourceUrl"),
            stringValue("targetUrl"),
            stringValue("action")
        )
        directCandidates.firstOrNull { it.isLaunchableUri() }?.let { return it }

        val nestedCandidates = listOf(
            objectValue("action"),
            objectValue("resourceExtInfo"),
            objectValue("creativeExtInfo")
        )
        nestedCandidates.forEach { candidate ->
            listOf(
                candidate.stringValue("url"),
                candidate.stringValue("jumpUrl"),
                candidate.stringValue("resourceUrl"),
                candidate.stringValue("targetUrl"),
                candidate.stringValue("orpheusUrl"),
                candidate.stringValue("orpheus")
            ).firstOrNull { it.isLaunchableUri() }?.let { return it }
        }
        return null
    }

    private fun JsonObject.isSongResource(): Boolean {
        if (stringValue("action").equals("play_all_song_from_current_index", ignoreCase = true)) {
            return true
        }
        return stringValue("resourceType").equals("song", ignoreCase = true)
    }

    private fun JsonObject.resolveSongArtists(): List<JsonObject> {
        val extInfo = objectValue("resourceExtInfo")
        val artists = extInfo.arrayValue("artists").mapNotNull { it as? JsonObject }
        if (artists.isNotEmpty()) {
            return artists
        }
        val songDataArtists = extInfo.objectValue("songData")
            .arrayValue("artists")
            .mapNotNull { it as? JsonObject }
        if (songDataArtists.isNotEmpty()) {
            return songDataArtists
        }
        return extInfo.objectValue("song")
            .arrayValue("ar")
            .mapNotNull { it as? JsonObject }
    }

    private fun JsonObject.resolveSongAlbum(): JsonObject {
        val extInfo = objectValue("resourceExtInfo")
        return extInfo.objectValue("songData").objectValue("album")
            .takeIf { it.isNotEmpty() }
            ?: extInfo.objectValue("song").objectValue("al")
    }

    private fun JsonObject.resolveSongDurationMs(): Long {
        val extInfo = objectValue("resourceExtInfo")
        val songDataDuration = extInfo.objectValue("songData").longValue("duration")
        if (songDataDuration > 0L) {
            return songDataDuration
        }
        return extInfo.objectValue("song").longValue("dt")
    }
}

private data class MappedHomeSongResource(
    val resourceId: String,
    val playlistItem: PlaylistItem,
    val songCard: HomeSongCardUiModel
)

private data class MappedHomeSongPlaybackContext(
    val songByResourceId: Map<String, MappedHomeSongResource> = emptyMap(),
    val playbackItems: List<PlaylistItem> = emptyList(),
    val activeIndexByResourceId: Map<String, Int> = emptyMap()
)

private enum class HomeDetailType {
    ARTIST,
    PLAYLIST,
    ALBUM
}

private fun String?.isLaunchableUri(): Boolean {
    return !this.isNullOrBlank() && contains("://")
}

private fun String.toDefaultDetailType(): HomeDetailType? {
    return when {
        contains("ARTIST", ignoreCase = true) -> HomeDetailType.ARTIST
        contains("PLAYLIST", ignoreCase = true) -> HomeDetailType.PLAYLIST
        contains("ALBUM", ignoreCase = true) -> HomeDetailType.ALBUM
        else -> null
    }
}

private fun JsonObject.toHomeDetailType(): HomeDetailType? {
    val numericType = intValue("targetType")
        .takeIf { it > 0 }
        ?: intValue("resourceType").takeIf { it > 0 }
    if (numericType != null) {
        return when (numericType) {
            10 -> HomeDetailType.ALBUM
            100 -> HomeDetailType.ARTIST
            1000 -> HomeDetailType.PLAYLIST
            else -> null
        }
    }

    val stringType = stringValue("targetType")
        ?: stringValue("resourceType")
        ?: stringValue("type")
        ?: stringValue("resourceTypeExt")
    return when {
        stringType.equals("album", ignoreCase = true) -> HomeDetailType.ALBUM
        stringType.equals("artist", ignoreCase = true) -> HomeDetailType.ARTIST
        stringType.equals("playlist", ignoreCase = true) -> HomeDetailType.PLAYLIST
        else -> null
    }
}

private fun JsonObject.routeIdCandidate(): String? {
    return listOf(
        stringValue("targetId"),
        stringValue("resourceId"),
        stringValue("id"),
        stringValue("target"),
        stringValue("targetUrlId")
    ).firstOrNull { !it.isNullOrBlank() }
}

private fun HomeDetailType.toContentTarget(id: String): HomeContentTarget {
    return when (this) {
        HomeDetailType.ARTIST -> HomeContentTarget.Artist(artistId = id)
        HomeDetailType.PLAYLIST -> HomeContentTarget.Playlist(playlistId = id)
        HomeDetailType.ALBUM -> HomeContentTarget.Album(albumId = id)
    }
}

private fun JsonObject.objectValue(key: String): JsonObject {
    return (this[key] as? JsonObject) ?: emptyJsonObject
}

private fun JsonObject.arrayValue(key: String): JsonArray {
    return this[key]?.jsonArray ?: emptyJsonArray
}

private fun JsonObject.stringValue(key: String): String? {
    return (this[key] as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
}

private fun JsonObject.intValue(key: String): Int {
    return this[key]?.jsonPrimitive?.intOrNull
        ?: stringValue(key)?.toIntOrNull()
        ?: 0
}

private fun JsonObject.longValue(key: String): Long {
    return this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        ?: stringValue(key)?.toLongOrNull()
        ?: 0L
}

private fun MutableList<String>.addIfNotBlank(value: String?) {
    if (!value.isNullOrBlank()) {
        add(value)
    }
}

private fun buildSongMetadataLine(
    artistText: String,
    albumTitle: String?
): String {
    return listOfNotNull(
        artistText.takeIf { it.isNotBlank() },
        albumTitle?.takeIf { it.isNotBlank() }
    ).joinToString(separator = " · ").ifBlank { "单曲推荐" }
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())
