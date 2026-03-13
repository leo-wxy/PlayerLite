package com.wxy.playerlite.feature.user

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoginScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultScreen_shouldShowBrandedHeroAndPrimaryActionHierarchy() {
        composeRule.setContent {
            PlayerLiteTheme {
                LoginScreen(
                    state = LoginUiState(),
                    onLoginMethodSelected = {},
                    onPhoneChanged = {},
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onSubmitLogin = {},
                    onSkip = {},
                    onLogout = {}
                )
            }
        }

        composeRule.onNodeWithTag("login_hero_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("login_status_chip").assertIsDisplayed()
        composeRule.onNodeWithTag("login_form_card").assertIsDisplayed()
        composeRule.onNodeWithTag("login_scroll_content").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("login_primary_button").assertIsDisplayed()
        composeRule.onNodeWithTag("login_skip_button").assertIsDisplayed()
    }
}
