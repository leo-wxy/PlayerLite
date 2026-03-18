package com.wxy.playerlite.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainBottomBarRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mainBottomBar_shouldRenderAsCenteredCompactCapsule() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainBottomBar(
                        selectedTab = MainTab.HOME,
                        onTabSelected = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("main_bottom_bar_container").assertIsDisplayed()
        composeRule.onNodeWithTag("main_bottom_bar_home").assertIsDisplayed()
        composeRule.onNodeWithTag("main_bottom_bar_user").assertIsDisplayed()

        val bottomBarRootBounds = composeRule
            .onNodeWithTag("main_bottom_bar_root")
            .fetchSemanticsNode()
            .boundsInRoot
        val screenBounds = composeRule
            .onRoot()
            .fetchSemanticsNode()
            .boundsInRoot
        val containerBounds = composeRule
            .onNodeWithTag("main_bottom_bar_container")
            .fetchSemanticsNode()
            .boundsInRoot
        val selectedItemBounds = composeRule
            .onNodeWithTag("main_bottom_bar_home")
            .fetchSemanticsNode()
            .boundsInRoot
        val selectedLabelBounds = composeRule
            .onNodeWithText("首页", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected floating bottom bar to be narrower than the root width, root=$bottomBarRootBounds container=$containerBounds",
            containerBounds.width < bottomBarRootBounds.width
        )
        val containerHeightDp = with(composeRule.density) { containerBounds.height.toDp() }
        assertTrue(
            "Expected floating bottom bar height to match the 72dp target, but was $containerHeightDp",
            containerHeightDp in 70.dp..74.dp
        )
        val selectedIndicatorNodes = composeRule
            .onAllNodesWithTag("main_bottom_bar_home_indicator", useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(
            "Expected selected tab to avoid background highlight nodes entirely",
            selectedIndicatorNodes.isEmpty()
        )
        val labelBottomInsetDp = with(composeRule.density) {
            (containerBounds.bottom - selectedLabelBounds.bottom).toDp()
        }
        val containerBottomInsetDp = with(composeRule.density) {
            (screenBounds.bottom - containerBounds.bottom).toDp()
        }
        assertTrue(
            "Expected selected label to keep breathing room from the bottom edge, but inset was $labelBottomInsetDp",
            labelBottomInsetDp >= 10.dp
        )
        assertTrue(
            "Expected floating bottom bar to lift off the bottom edge instead of being clipped, but inset was $containerBottomInsetDp",
            containerBottomInsetDp >= 18.dp
        )
    }
}
