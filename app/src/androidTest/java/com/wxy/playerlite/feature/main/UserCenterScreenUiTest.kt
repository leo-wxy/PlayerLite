package com.wxy.playerlite.feature.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("user_center_avatar").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_title").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_future_hint").assertIsDisplayed()
    }

    @Test
    fun loggedOutState_shouldShowLoginEntry() {
        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(),
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("user_center_login_button").assertIsDisplayed()
    }
}
