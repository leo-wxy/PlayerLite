package com.wxy.playerlite.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.core.playlist.PlaylistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeOverviewScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeSearchBox_shouldTriggerSearchCallback() {
        var clicked = false

        composeRule.setContent {
            MaterialTheme {
                HomeOverviewScreen(
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = emptyList(),
                        searchKeywords = listOf("默认热搜")
                    ),
                    bottomContentPadding = 0.dp,
                    onSearchClick = { clicked = true },
                    onRetry = {},
                    onAction = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_search_box_container")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertTrue(clicked)
    }

    @Test
    fun discoveryItems_shouldExposeClickActionAcrossLayouts() {
        composeRule.setContent {
            MaterialTheme {
                HomeOverviewScreen(
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
                    bottomContentPadding = 0.dp,
                    onSearchClick = {},
                    onRetry = {},
                    onAction = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_banner_card_banner-1").assertHasClickAction()
        composeRule.onNodeWithTag("home_discovery_card_playlist-1").assertHasClickAction()
        composeRule.onNodeWithTag("home_discovery_list")
            .performScrollToNode(hasTestTag("home_compact_card_shortcut-1"))
        composeRule.onNodeWithTag("home_compact_card_shortcut-1").assertHasClickAction()
    }

    @Test
    fun songSection_shouldRenderThreeItemsPerColumnAndDispatchRowAndMenuActions() {
        var capturedAction: HomeAction? = null
        val section = buildSongSection(songCount = 4)

        composeRule.setContent {
            MaterialTheme {
                HomeOverviewScreen(
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = listOf(section),
                        searchKeywords = listOf("默认热搜")
                    ),
                    bottomContentPadding = 0.dp,
                    onSearchClick = {},
                    onRetry = {},
                    onAction = { capturedAction = it }
                )
            }
        }

        composeRule.onNodeWithTag("home_song_column_0").assertIsDisplayed()
        composeRule.onNodeWithTag("home_song_column_1").assertIsDisplayed()
        composeRule.onNodeWithTag("home_song_row_song-1")
            .assertHasClickAction()
            .performClick()
        assertTrue(capturedAction is HomeAction.ReplaceQueueAndOpenPlayer)

        composeRule.onNodeWithTag("home_song_row_more_song-1").performClick()
        composeRule.onNodeWithText("下一首播放").performClick()
        assertEquals(
            HomeAction.InsertNext(
                item = PlaylistItem(
                    id = "song-1",
                    displayName = "Song 1",
                    songId = "song-1",
                    title = "Song 1",
                    artistText = "Artist 1",
                    albumTitle = "Album 1"
                )
            ),
            capturedAction
        )
    }

    private fun buildSongSection(songCount: Int): HomeSectionUiModel {
        return HomeSectionUiModel(
            code = "HOMEPAGE_BLOCK_STYLE_SONG",
            title = "猜你喜欢",
            layout = HomeSectionLayout.HORIZONTAL_LIST,
            items = (1..songCount).map { index ->
                val songId = "song-$index"
                val songName = "Song $index"
                HomeSectionItemUiModel(
                    id = songId,
                    title = songName,
                    subtitle = "Artist $index · Album $index",
                    imageUrl = "http://example.com/$songId.jpg",
                    action = HomeAction.ReplaceQueueAndOpenPlayer(
                        items = (1..songCount).map { queueIndex ->
                            PlaylistItem(
                                id = "song-$queueIndex",
                                displayName = "Song $queueIndex",
                                songId = "song-$queueIndex",
                                title = "Song $queueIndex",
                                artistText = "Artist $queueIndex",
                                albumTitle = "Album $queueIndex",
                                coverUrl = "http://example.com/song-$queueIndex.jpg"
                            )
                        },
                        activeIndex = index - 1
                    ),
                    songCard = HomeSongCardUiModel(
                        metadataLine = "Artist $index · Album $index",
                        recommendReason = "超${70 + index}%人播放",
                        durationMs = 180_000L + (index * 1_000L),
                        menuActions = listOf(
                            HomeSongMenuActionUiModel(
                                key = "insert_next",
                                label = "下一首播放",
                                action = HomeAction.InsertNext(
                                    item = PlaylistItem(
                                        id = songId,
                                        displayName = songName,
                                        songId = songId,
                                        title = songName,
                                        artistText = "Artist $index",
                                        albumTitle = "Album $index"
                                    )
                                )
                            )
                        )
                    )
                )
            }
        )
    }
}
