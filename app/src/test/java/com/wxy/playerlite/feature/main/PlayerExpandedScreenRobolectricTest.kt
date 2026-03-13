package com.wxy.playerlite.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerExpandedScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backButton_shouldSitCloserToTopEdge() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerExpandedScreen(
                    onBack = {},
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }

        val bounds = composeRule
            .onNodeWithTag("player_expanded_back_button")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected player back button to stay close to top edge, but top was ${bounds.top}",
            bounds.top < 4f
        )
    }

    @Test
    fun topEndAction_shouldAlignWithBackButtonTopEdge() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerExpandedScreen(
                    onBack = {},
                    topEndContent = {
                        Box(modifier = Modifier.testTag("player_expanded_top_end_action"))
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }

        val backBounds = composeRule
            .onNodeWithTag("player_expanded_back_button")
            .fetchSemanticsNode()
            .boundsInRoot
        val actionBounds = composeRule
            .onNodeWithTag("player_expanded_top_end_action")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected top-end action to align with back button top, but got ${actionBounds.top} vs ${backBounds.top}",
            kotlin.math.abs(actionBounds.top - backBounds.top) < 1f
        )
    }
}
