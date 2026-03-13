package com.wxy.playerlite.feature.main

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeOverviewScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun discoveryItems_shouldExposeClickActionAcrossLayouts() {
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
                                title = "",
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

        composeRule.onNodeWithTag("home_banner_card_banner-1").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("home_discovery_card_playlist-1").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("home_discovery_list")
            .performScrollToNode(hasTestTag("home_compact_card_shortcut-1"))
        composeRule.onNodeWithTag("home_compact_card_shortcut-1").assertIsDisplayed().assertHasClickAction()
    }
}
