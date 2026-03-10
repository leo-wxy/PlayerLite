package com.wxy.playerlite.feature.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Rule
import org.junit.Test

class PlayerExpandedScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun screen_shouldShowBackButton() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerExpandedScreen(
                    onBack = {}
                ) {}
            }
        }

        composeRule.onNodeWithTag("player_expanded_back_button").assertIsDisplayed()
    }
}
