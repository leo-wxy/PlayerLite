package com.wxy.playerlite.feature.main

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Rule
import org.junit.Test

class HomeOverviewScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun screen_shouldShowSearchEntryAndPlayEntry() {
        composeRule.setContent {
            PlayerLiteTheme {
                HomeOverviewScreen(
                    playerState = PlayerUiState(
                        selectedFileName = "晴天.mp3",
                        statusText = "点击底部按钮进入播放页"
                    ),
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = listOf(
                            HomeSectionUiModel(
                                code = "HOMEPAGE_BANNER",
                                title = "推荐 Banner",
                                layout = HomeSectionLayout.BANNER,
                                items = listOf(
                                    HomeSectionItemUiModel(
                                        id = "banner-1",
                                        title = "今日推荐",
                                        subtitle = "每日精选",
                                        imageUrl = null,
                                        badge = "精选"
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
                            ),
                            HomeSectionUiModel(
                                code = "HOMEPAGE_SHORTCUT",
                                title = "快捷入口",
                                layout = HomeSectionLayout.ICON_GRID,
                                items = listOf(
                                    HomeSectionItemUiModel(
                                        id = "shortcut-1",
                                        title = "每日推荐",
                                        subtitle = "",
                                        imageUrl = null
                                    )
                                )
                            )
                        ),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {},
                    onOpenPlayer = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_overview_play_entry").assertIsDisplayed()
        composeRule.onNodeWithTag("home_search_box").assertIsDisplayed()
        composeRule.onNodeWithTag("home_search_box_container").assertIsDisplayed()
        composeRule.onNodeWithTag("home_banner_card_banner-1").assertIsDisplayed()
        composeRule.onNodeWithTag("home_discovery_card_playlist-1").assertIsDisplayed()
        composeRule.onNodeWithTag("home_compact_card_shortcut-1")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(76.dp)
        composeRule.onNodeWithTag("home_play_entry_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_discovery_list").assertIsDisplayed()
        composeRule.onAllNodesWithText("推荐").assertCountEquals(0)
    }
}
