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
                        selectedTab = UserCenterTab.ARTISTS,
                        artistsState = UserCenterTabContentState.Content(
                            items = listOf(
                                UserCenterCollectionItemUiModel(
                                    id = "artist-1",
                                    title = "yama",
                                    subtitle = "真昼",
                                    imageUrl = null,
                                    meta = "68 张专辑"
                                )
                            )
                        )
                    ),
                    onTabSelected = {},
                    onRetryCurrentTab = {},
                    onContentClick = {},
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("user_center_profile_header").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_info_card").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_scroll_content").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("user_center_tabs").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_secondary_action").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_avatar").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_title").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_content_item_artist-1").assertIsDisplayed()
    }

    @Test
    fun loggedOutState_shouldShowLoginEntry() {
        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(),
                    contentState = UserCenterUiState(),
                    onTabSelected = {},
                    onRetryCurrentTab = {},
                    onContentClick = {},
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
