package com.wxy.playerlite.feature.main

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeOverviewUiStateTest {
    @Test
    fun currentSearchKeyword_shouldFollowCurrentIndex() {
        val state = HomeOverviewUiState(
            isLoading = false,
            sections = emptyList(),
            searchKeywords = listOf("默认热搜", "热搜A"),
            currentSearchKeywordIndex = 1
        )

        assertEquals("热搜A", state.currentSearchKeyword)
    }

    @Test
    fun overviewContent_shouldPreserveSectionOrderFromRepository() {
        val content = HomeOverviewContent(
            sections = listOf(
                HomeSectionUiModel(
                    code = "HOMEPAGE_BANNER",
                    title = "",
                    layout = HomeSectionLayout.BANNER,
                    items = listOf(
                        HomeSectionItemUiModel(
                            id = "banner-1",
                            title = "独家策划",
                            subtitle = "",
                            imageUrl = null
                        )
                    )
                ),
                HomeSectionUiModel(
                    code = "HOMEPAGE_BLOCK_PLAYLIST_RCMD",
                    title = "推荐歌单",
                    layout = HomeSectionLayout.HORIZONTAL_LIST,
                    items = listOf(
                        HomeSectionItemUiModel(
                            id = "playlist-1",
                            title = "歌单 A",
                            subtitle = "晚安歌单",
                            imageUrl = null
                        )
                    )
                )
            ),
            searchKeywords = listOf("默认热搜")
        )

        assertEquals(
            listOf("HOMEPAGE_BANNER", "HOMEPAGE_BLOCK_PLAYLIST_RCMD"),
            content.sections.map { it.code }
        )
    }
}
