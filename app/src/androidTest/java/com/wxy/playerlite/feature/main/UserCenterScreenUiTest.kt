package com.wxy.playerlite.feature.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.wxy.playerlite.feature.user.model.UserSessionUiState
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Rule
import org.junit.Test

class UserCenterScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loggedInState_shouldShowProfileInfo() {
        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(
                        isLoggedIn = true,
                        title = "Wucy",
                        summary = "在线账户 · Lv.10",
                        avatarUrl = "https://example.com/avatar.jpg"
                    ),
                    contentState = UserCenterUiState(
                        likedPlaylistId = "liked-playlist",
                        playlistsState = UserCenterPlaylistsState.Content(
                            items = listOf(
                                UserCenterCollectionItemUiModel(
                                    id = "playlist-1",
                                    title = "夜间循环",
                                    subtitle = "Wucy",
                                    imageUrl = null,
                                    meta = "18 首"
                                )
                            )
                        )
                    ),
                    onRetryPlaylists = {},
                    onContentClick = {},
                    onOpenLikedSongs = {},
                    onOpenRecentSongs = {},
                    onOpenLocalSongs = {},
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("user_center_profile_header").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_quick_entries").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_scroll_content").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("user_center_playlists_section_header").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_secondary_action").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_avatar").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_title").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_content_item_playlist-1").assertIsDisplayed()
    }

    @Test
    fun loggedOutState_shouldShowLoginEntry() {
        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(),
                    contentState = UserCenterUiState(),
                    onRetryPlaylists = {},
                    onContentClick = {},
                    onOpenLikedSongs = {},
                    onOpenRecentSongs = {},
                    onOpenLocalSongs = {},
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("user_center_profile_header").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_info_card").assertIsDisplayed()
        composeRule.onAllNodesWithTag("user_center_tabs").assertCountEquals(0)
        composeRule.onNodeWithTag("user_center_scroll_content").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("user_center_primary_action").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_login_button").assertIsDisplayed()
    }
}
