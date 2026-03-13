package com.wxy.playerlite.feature.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MusicDetailScaffoldRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backButton_shouldRemainFixedAfterListScroll() {
        composeRule.setContent {
            PlayerLiteTheme {
                MusicDetailScaffold(
                    heroTestTag = "detail_test_hero",
                    onBack = {},
                    heroContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .testTag("detail_test_hero_content")
                        )
                    }
                ) {
                    items(count = 40) { index ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .testTag("detail_body_item_$index")
                        )
                    }
                }
            }
        }

        val initialBounds = composeRule
            .onNodeWithTag("detail_back_button")
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("detail_body_item_39"))

        composeRule.onNodeWithTag("detail_back_button").assertIsDisplayed()

        val scrolledBounds = composeRule
            .onNodeWithTag("detail_back_button")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected detail back button top to stay fixed, but moved from ${initialBounds.top} to ${scrolledBounds.top}",
            abs(initialBounds.top - scrolledBounds.top) < 1f
        )
        assertTrue(
            "Expected detail back button left to stay fixed, but moved from ${initialBounds.left} to ${scrolledBounds.left}",
            abs(initialBounds.left - scrolledBounds.left) < 1f
        )
    }
}
