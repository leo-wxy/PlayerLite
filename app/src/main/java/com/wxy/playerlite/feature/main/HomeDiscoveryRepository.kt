package com.wxy.playerlite.feature.main

import com.wxy.playerlite.network.core.JsonHttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
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
    val badge: String? = null
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
                    badge = banner.stringValue("typeTitle")
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
                    resource.jsonObject.toResourceItem()
                }
            }
        return HomeSectionUiModel(
            code = code,
            title = title,
            layout = layout,
            items = items
        )
    }

    private fun JsonObject.toResourceItem(): HomeSectionItemUiModel? {
        val uiElement = objectValue("uiElement")
        val title = uiElement.objectValue("mainTitle").stringValue("title") ?: return null
        val subTitle = uiElement.objectValue("subTitle").stringValue("title").orEmpty()
        val labels = uiElement.arrayValue("labelTexts").mapNotNull { it.jsonPrimitive.contentOrNull }
        return HomeSectionItemUiModel(
            id = stringValue("resourceId") ?: title,
            title = title,
            subtitle = subTitle.ifBlank { labels.joinToString(separator = " · ") },
            imageUrl = uiElement.objectValue("image").stringValue("imageUrl"),
            badge = labels.firstOrNull()
        )
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

private fun MutableList<String>.addIfNotBlank(value: String?) {
    if (!value.isNullOrBlank()) {
        add(value)
    }
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())
