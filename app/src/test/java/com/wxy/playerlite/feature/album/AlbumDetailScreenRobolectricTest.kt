package com.wxy.playerlite.feature.album

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumDetailScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentState_shouldShowHeroDynamicInfoAndTracksTabContent() {
        composeRule.setContent {
            PlayerLiteTheme {
                AlbumDetailScreen(
                    state = AlbumDetailUiState(
                        contentState = AlbumContentUiState.Content(
                            AlbumDetailContent(
                                albumId = "32311",
                                title = "神的游戏",
                                artistText = "张悬",
                                description = "专辑简介",
                                coverUrl = null,
                                company = "索尼音乐",
                                publishTimeText = "2012-08-10",
                                trackCount = 9,
                                tracks = listOf(
                                    AlbumTrackRow(
                                        trackId = "326696",
                                        title = "疯狂的阳光",
                                        artistText = "张悬",
                                        albumTitle = "神的游戏",
                                        coverUrl = null,
                                        durationMs = 235146L
                                    )
                                )
                            )
                        ),
                        dynamicState = AlbumDynamicUiState.Content(
                            AlbumDynamicInfo(
                                commentCount = 1990,
                                shareCount = 8542,
                                subscribedCount = 66888
                            )
                        )
                    ),
                    heroAccentColor = Color(0xFF3F51B5),
                    topBarContentColor = Color.White,
                    headerCommentCountText = "1990",
                    onBack = {},
                    onRetry = {},
                    onLoadMore = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("album_detail_hero_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("album_detail_cover").assertIsDisplayed()
        composeRule.onNodeWithTag("album_dynamic_meta_section").assertIsDisplayed()
        composeRule.onNodeWithTag("album_hero_play_all_button").assertIsDisplayed()
        composeRule.onNodeWithText("播放全部").assertIsDisplayed()
        scrollOuterToTag("album_tab_pager")
        composeRule.onNodeWithTag("album_tracks_list").assertIsDisplayed()
        composeRule.onNodeWithTag("album_tracks_list")
            .performScrollToNode(hasTestTag("album_track_326696"))
        composeRule.onNodeWithTag("album_track_326696").assertIsDisplayed()
    }

    @Test
    fun selectingDescriptionTab_shouldShowFullDescriptionPanel() {
        val longDescription = "专辑简介第一段。专辑简介第二段。专辑简介第三段。"

        composeRule.setContent {
            PlayerLiteTheme {
                AlbumDetailScreen(
                    state = AlbumDetailUiState(
                        contentState = AlbumContentUiState.Content(
                            AlbumDetailContent(
                                albumId = "32311",
                                title = "神的游戏",
                                artistText = "张悬",
                                description = longDescription,
                                coverUrl = null,
                                company = "索尼音乐",
                                publishTimeText = "2012-08-10",
                                trackCount = 9,
                                tracks = emptyList()
                            )
                        ),
                        dynamicState = AlbumDynamicUiState.Empty
                    ),
                    heroAccentColor = Color(0xFF3F51B5),
                    topBarContentColor = Color.White,
                    headerCommentCountText = null,
                    onBack = {},
                    onRetry = {},
                    onLoadMore = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        scrollOuterToTag("album_sticky_tabs_header")
        composeRule.onNodeWithTag("album_tab_description", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("album_description_list").assertIsDisplayed()
        composeRule.onNodeWithTag("album_description_panel").assertIsDisplayed()
        composeRule.onNodeWithText(longDescription).assertIsDisplayed()
    }

    @Test
    fun loadingMoreTracks_shouldShowPagingFooter() {
        composeRule.setContent {
            PlayerLiteTheme {
                AlbumDetailScreen(
                    state = AlbumDetailUiState(
                        contentState = AlbumContentUiState.Content(
                            content = AlbumDetailContent(
                                albumId = "32311",
                                title = "神的游戏",
                                artistText = "张悬",
                                description = "专辑简介",
                                coverUrl = null,
                                company = "索尼音乐",
                                publishTimeText = "2012-08-10",
                                trackCount = 9,
                                tracks = listOf(
                                    AlbumTrackRow(
                                        trackId = "326696",
                                        title = "疯狂的阳光",
                                        artistText = "张悬",
                                        albumTitle = "神的游戏",
                                        coverUrl = null,
                                        durationMs = 235146L
                                    )
                                )
                            ),
                            isLoadingMore = true
                        ),
                        dynamicState = AlbumDynamicUiState.Content(
                            AlbumDynamicInfo(
                                commentCount = 1990,
                                shareCount = 8542,
                                subscribedCount = 66888
                            )
                        )
                    ),
                    heroAccentColor = Color(0xFF3F51B5),
                    topBarContentColor = Color.White,
                    headerCommentCountText = "1990",
                    onBack = {},
                    onRetry = {},
                    onLoadMore = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        scrollOuterToTag("album_tab_pager")
        composeRule.onNodeWithTag("album_tracks_list")
            .performScrollToNode(hasTestTag("album_tracks_load_more_loading"))
        composeRule.onNodeWithTag("album_tracks_load_more_loading").assertIsDisplayed()
    }

    @Test
    fun loadMoreFailure_shouldShowPagingErrorFooter() {
        composeRule.setContent {
            PlayerLiteTheme {
                AlbumDetailScreen(
                    state = AlbumDetailUiState(
                        contentState = AlbumContentUiState.Content(
                            content = AlbumDetailContent(
                                albumId = "32311",
                                title = "神的游戏",
                                artistText = "张悬",
                                description = "专辑简介",
                                coverUrl = null,
                                company = "索尼音乐",
                                publishTimeText = "2012-08-10",
                                trackCount = 9,
                                tracks = listOf(
                                    AlbumTrackRow(
                                        trackId = "326696",
                                        title = "疯狂的阳光",
                                        artistText = "张悬",
                                        albumTitle = "神的游戏",
                                        coverUrl = null,
                                        durationMs = 235146L
                                    )
                                )
                            ),
                            loadMoreErrorMessage = "下一页加载失败"
                        ),
                        dynamicState = AlbumDynamicUiState.Content(
                            AlbumDynamicInfo(
                                commentCount = 1990,
                                shareCount = 8542,
                                subscribedCount = 66888
                            )
                        )
                    ),
                    heroAccentColor = Color(0xFF3F51B5),
                    topBarContentColor = Color.White,
                    headerCommentCountText = "1990",
                    onBack = {},
                    onRetry = {},
                    onLoadMore = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        scrollOuterToTag("album_tab_pager")
        composeRule.onNodeWithTag("album_tracks_list")
            .performScrollToNode(hasTestTag("album_tracks_load_more_error"))
        composeRule.onNodeWithTag("album_tracks_load_more_error").assertIsDisplayed()
    }

    @Test
    fun playAllAndTrackClick_shouldInvokeCallbacks() {
        var playAllClicks = 0
        var clickedTrackIndex = -1

        composeRule.setContent {
            PlayerLiteTheme {
                AlbumDetailScreen(
                    state = AlbumDetailUiState(
                        contentState = AlbumContentUiState.Content(
                            AlbumDetailContent(
                                albumId = "32311",
                                title = "神的游戏",
                                artistText = "张悬",
                                description = "专辑简介",
                                coverUrl = null,
                                company = "索尼音乐",
                                publishTimeText = "2012-08-10",
                                trackCount = 9,
                                tracks = listOf(
                                    AlbumTrackRow(
                                        trackId = "326696",
                                        title = "疯狂的阳光",
                                        artistText = "张悬",
                                        albumTitle = "神的游戏",
                                        coverUrl = null,
                                        durationMs = 235146L
                                    )
                                )
                            )
                        ),
                        dynamicState = AlbumDynamicUiState.Content(
                            AlbumDynamicInfo(
                                commentCount = 1990,
                                shareCount = 8542,
                                subscribedCount = 66888
                            )
                        )
                    ),
                    heroAccentColor = Color(0xFF3F51B5),
                    topBarContentColor = Color.White,
                    headerCommentCountText = "1990",
                    onBack = {},
                    onRetry = {},
                    onLoadMore = {},
                    onPlayAll = { playAllClicks++ },
                    onTrackClick = { clickedTrackIndex = it }
                )
            }
        }

        composeRule.onNodeWithTag("album_hero_play_all_button").performClick()
        scrollOuterToTag("album_tab_pager")
        composeRule.onNodeWithTag("album_tracks_list")
            .performScrollToNode(hasTestTag("album_track_326696"))
        composeRule.onNodeWithTag("album_track_326696").performClick()

        assertEquals(1, playAllClicks)
        assertEquals(0, clickedTrackIndex)
    }

    private fun scrollOuterToTag(tag: String) {
        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag(tag))
    }
}
