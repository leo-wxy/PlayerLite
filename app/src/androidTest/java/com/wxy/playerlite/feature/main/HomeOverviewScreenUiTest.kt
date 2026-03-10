package com.wxy.playerlite.feature.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.user.model.UserSessionUiState
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Rule
import org.junit.Test

class HomeOverviewScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun screen_shouldShowPlayEntryAndUserEntry() {
        composeRule.setContent {
            PlayerLiteTheme {
                HomeOverviewScreen(
                    playerState = PlayerUiState(
                        selectedFileName = "晴天.mp3",
                        statusText = "点击底部按钮进入播放页"
                    ),
                    userState = UserSessionUiState(
                        isLoggedIn = true,
                        title = "Wucy",
                        summary = "Lv.10"
                    ),
                    onOpenPlayer = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_overview_play_entry").assertIsDisplayed()
        composeRule.onNodeWithTag("home_overview_cover").assertIsDisplayed()
    }
}
