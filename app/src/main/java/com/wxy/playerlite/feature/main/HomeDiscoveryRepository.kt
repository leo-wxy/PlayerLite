package com.wxy.playerlite.feature.main

import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.feature.search.SearchRouteTarget
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal interface HomeDiscoveryRepository {
    suspend fun fetchHomeOverview(): HomeOverviewContent
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

internal data class HomeOverviewContent(
    val sections: List<HomeSectionUiModel>,
    val searchKeywords: List<String>
)

internal data class HomeSectionUiModel(
    val code: String,
    val title: String,
    val layout: HomeSectionLayout,
    val items: List<HomeSectionItemUiModel>
)

internal data class HomeSectionItemUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val badge: String? = null,
    val action: ContentEntryAction = ContentEntryAction.Unsupported()
)

internal enum class HomeSectionLayout {
    BANNER,
    ICON_GRID,
    HORIZONTAL_LIST
}

internal object HomeDefaults {
    val fallbackSearchKeywords: List<String> = listOf(
        "搜索你喜欢的音乐",
        "搜索热门歌单",
        "搜索新歌热榜"
    )
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
                    action = banner.toContentEntryAction()
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
        val items = arrayValue("creatives")
            .flatMap { creative ->
                creative.jsonObject.arrayValue("resources").mapNotNull { resource ->
                    resource.jsonObject.toResourceItem(
                        defaultTargetType = code.toDefaultDetailType()
                    )
                }
            }
        return HomeSectionUiModel(
            code = code,
            title = title,
            layout = layout,
            items = items
        )
    }

    private fun JsonObject.toResourceItem(defaultTargetType: HomeDetailType?): HomeSectionItemUiModel? {
        val uiElement = objectValue("uiElement")
        val title = uiElement.objectValue("mainTitle").stringValue("title") ?: return null
        val subTitle = uiElement.objectValue("subTitle").stringValue("title").orEmpty()
        val labels = uiElement.arrayValue("labelTexts").mapNotNull { it.jsonPrimitive.contentOrNull }
        return HomeSectionItemUiModel(
            id = stringValue("resourceId") ?: title,
            title = title,
            subtitle = subTitle.ifBlank { labels.joinToString(separator = " · ") },
            imageUrl = uiElement.objectValue("image").stringValue("imageUrl"),
            badge = labels.firstOrNull(),
            action = toContentEntryAction(defaultTargetType = defaultTargetType)
        )
    }

    private fun JsonObject.toContentEntryAction(
        defaultTargetType: HomeDetailType? = null
    ): ContentEntryAction {
        val routeTarget = resolveRouteTarget(defaultTargetType = defaultTargetType)
        if (routeTarget != null) {
            return ContentEntryAction.OpenDetail(routeTarget)
        }

        val uri = resolveUriCandidate()
        if (uri != null) {
            return ContentEntryAction.OpenUri(uri = uri)
        }

        return ContentEntryAction.Unsupported()
    }

    private fun JsonObject.resolveRouteTarget(
        defaultTargetType: HomeDetailType?
    ): SearchRouteTarget? {
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
                return explicitType.toRouteTarget(explicitId)
            }
        }

        val fallbackId = routeIdCandidate()
        if (defaultTargetType != null && !fallbackId.isNullOrBlank()) {
            return defaultTargetType.toRouteTarget(fallbackId)
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
}

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

private fun HomeDetailType.toRouteTarget(id: String): SearchRouteTarget {
    return when (this) {
        HomeDetailType.ARTIST -> SearchRouteTarget.Artist(artistId = id)
        HomeDetailType.PLAYLIST -> SearchRouteTarget.Playlist(playlistId = id)
        HomeDetailType.ALBUM -> SearchRouteTarget.Album(albumId = id)
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

private fun MutableList<String>.addIfNotBlank(value: String?) {
    if (!value.isNullOrBlank()) {
        add(value)
    }
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())
