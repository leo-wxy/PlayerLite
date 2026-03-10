package com.wxy.playerlite.feature.player.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Rule
import org.junit.Test

class LoginEntryButtonUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loggedInWithAvatar_shouldShowAvatarEntry() {
        composeRule.setContent {
            PlayerLiteTheme {
                LoginEntryButton(
                    enabled = true,
                    isLoggedIn = true,
                    avatarUrl = "https://example.com/avatar.jpg",
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("login_entry_button_root").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("账户头像入口", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun loggedInWithoutAvatar_shouldShowFallbackAccountIcon() {
        composeRule.setContent {
            PlayerLiteTheme {
                LoginEntryButton(
                    enabled = true,
                    isLoggedIn = true,
                    avatarUrl = null,
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("login_entry_button_root").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("账户中心", useUnmergedTree = true).assertIsDisplayed()
    }
}
