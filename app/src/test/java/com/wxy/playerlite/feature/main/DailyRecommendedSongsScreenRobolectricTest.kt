package com.wxy.playerlite.feature.main

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
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
                    onItemClick = {}
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
                    onItemClick = { clickedIndex = it }
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
        composeRule.onNodeWithText("Song 1").assertIsDisplayed()
        composeRule.onNodeWithText("Artist 1 · Album 1 · 超80%人播放").assertIsDisplayed()

        composeRule.onNodeWithTag("detail_back_button").performClick()
        composeRule.onNodeWithTag("daily_recommended_hero_primary_action").performClick()
        composeRule.onNodeWithTag("daily_recommended_row_song-1").performClick()

        composeRule.runOnIdle {
            assertEquals(1, backClicks)
            assertEquals(1, playAllClicks)
            assertEquals(0, clickedIndex)
        }
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
                    onItemClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("daily_recommended_hero_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_recommended_loading_state").assertIsDisplayed()
        composeRule.onAllNodesWithTag("daily_recommended_refresh_button").assertCountEquals(0)
    }
}
