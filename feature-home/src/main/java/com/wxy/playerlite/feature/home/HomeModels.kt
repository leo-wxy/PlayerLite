package com.wxy.playerlite.feature.home

import com.wxy.playerlite.core.playlist.PlaylistItem

const val defaultUnsupportedHomeContentMessage = "当前内容暂不支持打开"
const val defaultOpenHomeLinkFailedMessage = "当前内容暂时无法打开"

sealed interface HomeAction {
    data class OpenContent(
        val target: HomeContentTarget
    ) : HomeAction

    data class ReplaceQueueAndOpenPlayer(
        val items: List<PlaylistItem>,
        val activeIndex: Int
    ) : HomeAction

    data class InsertNext(
        val item: PlaylistItem
    ) : HomeAction
}

sealed interface HomeContentTarget {
    data object DailyRecommendedSongs : HomeContentTarget

    data class Artist(
        val artistId: String
    ) : HomeContentTarget

    data class Playlist(
        val playlistId: String
    ) : HomeContentTarget

    data class Album(
        val albumId: String
    ) : HomeContentTarget

    data class ExternalUri(
        val uri: String,
        val fallbackMessage: String = defaultOpenHomeLinkFailedMessage
    ) : HomeContentTarget

    data class Unsupported(
        val message: String = defaultUnsupportedHomeContentMessage
    ) : HomeContentTarget
}

data class HomeOverviewContent(
    val sections: List<HomeSectionUiModel>,
    val searchKeywords: List<String>
)

data class HomeSectionUiModel(
    val code: String,
    val title: String,
    val layout: HomeSectionLayout,
    val items: List<HomeSectionItemUiModel>
)

data class HomeSongMenuActionUiModel(
    val key: String,
    val label: String,
    val action: HomeAction
)

data class HomeSongCardUiModel(
    val metadataLine: String,
    val recommendReason: String? = null,
    val durationMs: Long = 0L,
    val menuActions: List<HomeSongMenuActionUiModel> = emptyList()
)

data class HomeSectionItemUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val badge: String? = null,
    val action: HomeAction = HomeAction.OpenContent(
        HomeContentTarget.Unsupported()
    ),
    val songCard: HomeSongCardUiModel? = null
)

enum class HomeSectionLayout {
    BANNER,
    ICON_GRID,
    HORIZONTAL_LIST
}

object HomeDefaults {
    val fallbackSearchKeywords: List<String> = listOf(
        "搜索你喜欢的音乐",
        "搜索热门歌单",
        "搜索新歌热榜"
    )
}

data class HomeOverviewUiState(
    val isLoading: Boolean = true,
    val sections: List<HomeSectionUiModel> = emptyList(),
    val searchKeywords: List<String> = HomeDefaults.fallbackSearchKeywords,
    val currentSearchKeywordIndex: Int = 0,
    val errorMessage: String? = null
) {
    val currentSearchKeyword: String
        get() {
            if (searchKeywords.isEmpty()) {
                return ""
            }
            return searchKeywords[currentSearchKeywordIndex.mod(searchKeywords.size)]
        }
}
