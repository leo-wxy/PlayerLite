package com.wxy.playerlite.feature.main

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DailyRecommendedSongsScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loggedOutState_shouldRenderLoginGuideAndDispatchLoginClick() {
        var loginClicks = 0

        composeRule.setContent {
            PlayerLiteTheme {
                DailyRecommendedSongsScreen(
                    state = DailyRecommendedSongsUiState(
                        isLoggedIn = false,
                        contentState = DailyRecommendedSongsContentState.Idle
                    ),
                    onBack = {},
                    onLoginClick = { loginClicks += 1 },
                    onRetry = {},
                    onPlayAll = {},
                    onItemClick = {},
                    onItemInsertNext = {},
                    onItemOpenDetail = {},
                    onItemOpenArtist = {},
                    onItemOpenAlbum = {}
                )
            }
        }

        composeRule.onNodeWithTag("daily_recommended_hero_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_recommended_login_state").assertIsDisplayed()
        composeRule.onNodeWithText("登录后查看每日推荐").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_recommended_login_button")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, loginClicks)
        }
    }

    @Test
    fun contentState_shouldRenderRecommendationReasonAndPlaybackCallbacks() {
        var playAllClicks = 0
        var clickedIndex = -1
        var backClicks = 0
        var insertNextClicks = 0
        var detailClicks = 0
        var artistClicks = 0
        var albumClicks = 0

        composeRule.setContent {
            PlayerLiteTheme {
                DailyRecommendedSongsScreen(
                    state = DailyRecommendedSongsUiState(
                        isLoggedIn = true,
                        contentState = DailyRecommendedSongsContentState.Content(
                            items = listOf(
                                DailyRecommendedSongUiModel(
                                    id = "song-1",
                                    songId = "song-1",
                                    title = "Song 1",
                                    artistText = "Artist 1",
                                    primaryArtistId = "artist-1",
                                    albumId = "album-1",
                                    albumTitle = "Album 1",
                                    coverUrl = null,
                                    durationMs = 1000L,
                                    recommendReason = "超80%人播放"
                                )
                            )
                        )
                    ),
                    onBack = { backClicks += 1 },
                    onLoginClick = {},
                    onRetry = {},
                    onPlayAll = { playAllClicks += 1 },
                    onItemClick = { clickedIndex = it },
                    onItemInsertNext = { insertNextClicks += 1 },
                    onItemOpenDetail = { detailClicks += 1 },
                    onItemOpenArtist = { artistClicks += 1 },
                    onItemOpenAlbum = { albumClicks += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("daily_recommended_hero_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_recommended_hero_cover").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_recommended_collapsing_top_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_back_button")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag("daily_recommended_hero_primary_action")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onAllNodesWithTag("daily_recommended_hero_secondary_action").assertCountEquals(0)
        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("daily_recommended_tracks_section"))
        composeRule.onNodeWithTag("daily_recommended_tracks_section").assertIsDisplayed()
        composeRule.onNodeWithText("今日推荐歌曲").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_recommended_row_song-1")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag("daily_recommended_row_more_song-1")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("Song 1").assertIsDisplayed()
        composeRule.onNodeWithText("Artist 1 · Album 1 · 超80%人播放").assertIsDisplayed()

        composeRule.onNodeWithTag("detail_back_button").performClick()
        composeRule.onNodeWithTag("daily_recommended_hero_primary_action").performClick()
        composeRule.onNodeWithTag("daily_recommended_row_song-1").performClick()
        composeRule.onNodeWithTag("daily_recommended_row_more_song-1").performClick()
        composeRule.onNodeWithText("下一首播放").performClick()
        composeRule.onNodeWithTag("daily_recommended_row_more_song-1").performClick()
        composeRule.onNodeWithText("查看歌曲详情").performClick()
        composeRule.onNodeWithTag("daily_recommended_row_more_song-1").performClick()
        composeRule.onNodeWithText("查看歌手").performClick()
        composeRule.onNodeWithTag("daily_recommended_row_more_song-1").performClick()
        composeRule.onNodeWithText("查看专辑").performClick()

        composeRule.runOnIdle {
            assertEquals(1, backClicks)
            assertEquals(1, playAllClicks)
            assertEquals(0, clickedIndex)
            assertEquals(1, insertNextClicks)
            assertEquals(1, detailClicks)
            assertEquals(1, artistClicks)
            assertEquals(1, albumClicks)
        }
    }

    @Test
    fun contentState_withoutArtistOrAlbumTarget_shouldHideUnsupportedMenuActions() {
        composeRule.setContent {
            PlayerLiteTheme {
                DailyRecommendedSongsScreen(
                    state = DailyRecommendedSongsUiState(
                        isLoggedIn = true,
                        contentState = DailyRecommendedSongsContentState.Content(
                            items = listOf(
                                DailyRecommendedSongUiModel(
                                    id = "song-2",
                                    songId = "song-2",
                                    title = "Song 2",
                                    artistText = "Artist 2",
                                    albumTitle = "Album 2",
                                    coverUrl = null,
                                    durationMs = 2000L,
                                    recommendReason = null
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onLoginClick = {},
                    onRetry = {},
                    onPlayAll = {},
                    onItemClick = {},
                    onItemInsertNext = {},
                    onItemOpenDetail = {},
                    onItemOpenArtist = {},
                    onItemOpenAlbum = {}
                )
            }
        }

        composeRule.onNodeWithTag("daily_recommended_row_more_song-2").performClick()
        composeRule.onNodeWithText("下一首播放").assertIsDisplayed()
        composeRule.onNodeWithText("查看歌曲详情").assertIsDisplayed()
        composeRule.onAllNodesWithText("查看歌手").assertCountEquals(0)
        composeRule.onAllNodesWithText("查看专辑").assertCountEquals(0)
    }

    @Test
    fun loadingState_shouldHideRetryAction() {
        composeRule.setContent {
            PlayerLiteTheme {
                DailyRecommendedSongsScreen(
                    state = DailyRecommendedSongsUiState(
                        isLoggedIn = true,
                        contentState = DailyRecommendedSongsContentState.Loading
                    ),
                    onBack = {},
                    onLoginClick = {},
                    onRetry = {},
                    onPlayAll = {},
                    onItemClick = {},
                    onItemInsertNext = {},
                    onItemOpenDetail = {},
                    onItemOpenArtist = {},
                    onItemOpenAlbum = {}
                )
            }
        }

        composeRule.onNodeWithTag("daily_recommended_hero_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_recommended_loading_state").assertIsDisplayed()
        composeRule.onAllNodesWithTag("daily_recommended_refresh_button").assertCountEquals(0)
    }
}
