package com.wxy.playerlite.feature.search

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.unit.dp

class SearchScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hotMode_shouldShowSearchInputAndHotList() {
        composeRule.setContent {
            PlayerLiteTheme {
                SearchScreen(
                    state = SearchUiState(
                        query = "",
                        pageMode = SearchPageMode.HOT,
                        historyKeywords = listOf("周深", "小美满"),
                        hotState = SearchHotUiState.Content(
                            listOf(
                                SearchHotKeywordUiModel(keyword = "海屿你", score = 10, iconType = 4),
                                SearchHotKeywordUiModel(keyword = "小半", score = 9, iconType = 0)
                            )
                        )
                    ),
                    onBack = {},
                    onQueryChanged = {},
                    onSubmitSearch = {},
                    onHistoryKeywordClick = {},
                    onSuggestionClick = {},
                    onHotKeywordClick = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("search_top_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("search_input").assertIsDisplayed()
        composeRule.onNodeWithTag("search_back_button").assertHeightIsAtLeast(40.dp)
        composeRule.onNodeWithTag("search_input_container").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("search_history_pinned").assertIsDisplayed()
        composeRule.onNodeWithTag("search_history_section").assertIsDisplayed()
        composeRule.onNodeWithTag("search_history_chip_0").assertIsDisplayed()
        composeRule.onNodeWithTag("search_hot_scroll_container").assertIsDisplayed()
        composeRule.onNodeWithTag("search_hot_section").assertIsDisplayed()
        composeRule.onNodeWithTag("search_hot_board").assertIsDisplayed()
        composeRule.onNodeWithTag("search_hot_rank_0").assertIsDisplayed()
        composeRule.onNodeWithTag("search_hot_list").assertIsDisplayed()
    }

    @Test
    fun resultMode_shouldHidePinnedHistoryAndShowResultList() {
        composeRule.setContent {
            PlayerLiteTheme {
                SearchScreen(
                    state = SearchUiState(
                        query = "小美满",
                        pageMode = SearchPageMode.RESULT,
                        historyKeywords = emptyList(),
                        hotState = SearchHotUiState.Content(emptyList()),
                        resultState = SearchResultUiState.Content(
                            listOf(
                                SearchResultItemUiModel(
                                    id = "1",
                                    title = "小美满",
                                    subtitle = "周深 · 小美满",
                                    coverUrl = null
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onQueryChanged = {},
                    onSubmitSearch = {},
                    onHistoryKeywordClick = {},
                    onSuggestionClick = {},
                    onHotKeywordClick = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onAllNodesWithTag("search_history_pinned").assertCountEquals(0)
        composeRule.onNodeWithTag("search_result_list").assertIsDisplayed()
        composeRule.onNodeWithTag("search_result_card_1").assertIsDisplayed()
    }
}
