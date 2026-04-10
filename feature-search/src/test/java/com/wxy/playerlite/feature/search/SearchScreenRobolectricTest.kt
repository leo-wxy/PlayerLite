package com.wxy.playerlite.feature.search

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hotMode_shouldKeepStandaloneBackActionAndLightweightSearchField() {
        composeRule.setContent {
            SearchFeatureTheme {
                SearchScreen(
                    state = SearchUiState(
                        pageMode = SearchPageMode.HOT,
                        historyKeywords = listOf("After Hours", "Lofi Hip Hop"),
                        hotState = SearchHotUiState.Content(
                            items = listOf(
                                SearchHotKeywordUiModel(keyword = "Taylor Swift", score = 1234),
                                SearchHotKeywordUiModel(keyword = "Classic Rock", score = 900)
                            )
                        )
                    ),
                    onBack = {},
                    onQueryChanged = {},
                    onSubmitSearch = {},
                    onHistoryKeywordClick = {},
                    onRemoveHistoryKeyword = {},
                    onClearHistory = {},
                    onSuggestionClick = {},
                    onHotKeywordClick = {},
                    onResultTypeSelected = {},
                    onResultClick = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("search_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("search_input_container").assertIsDisplayed()
        composeRule.onNodeWithTag("search_hot_board").assertIsDisplayed()
    }

    @Test
    fun hotMode_shouldExposeClearAllAndSingleHistoryRemoveActions() {
        var clearedCount = 0
        var removedKeyword: String? = null

        composeRule.setContent {
            SearchFeatureTheme {
                SearchScreen(
                    state = SearchUiState(
                        pageMode = SearchPageMode.HOT,
                        historyKeywords = listOf("After Hours", "Lofi Hip Hop"),
                        hotState = SearchHotUiState.Content(
                            items = listOf(
                                SearchHotKeywordUiModel(keyword = "Taylor Swift", score = 1234)
                            )
                        )
                    ),
                    onBack = {},
                    onQueryChanged = {},
                    onSubmitSearch = {},
                    onHistoryKeywordClick = {},
                    onRemoveHistoryKeyword = { removedKeyword = it },
                    onClearHistory = { clearedCount += 1 },
                    onSuggestionClick = {},
                    onHotKeywordClick = {},
                    onResultTypeSelected = {},
                    onResultClick = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("search_history_clear_all").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("清空").assertIsDisplayed()
        composeRule.onNodeWithTag("search_history_remove_1").assertIsDisplayed().performClick()

        assertEquals(1, clearedCount)
        assertEquals("Lofi Hip Hop", removedKeyword)
    }

    @Test
    fun hotMode_historyChip_shouldKeepKeywordCloserToLeftEdgeWithoutLeadingIcon() {
        composeRule.setContent {
            SearchFeatureTheme {
                SearchScreen(
                    state = SearchUiState(
                        pageMode = SearchPageMode.HOT,
                        historyKeywords = listOf("After Hours"),
                        hotState = SearchHotUiState.Content(
                            items = listOf(
                                SearchHotKeywordUiModel(keyword = "Taylor Swift", score = 1234)
                            )
                        )
                    ),
                    onBack = {},
                    onQueryChanged = {},
                    onSubmitSearch = {},
                    onHistoryKeywordClick = {},
                    onRemoveHistoryKeyword = {},
                    onClearHistory = {},
                    onSuggestionClick = {},
                    onHotKeywordClick = {},
                    onResultTypeSelected = {},
                    onResultClick = {},
                    onRetry = {}
                )
            }
        }

        val chipBounds = composeRule
            .onNodeWithTag("search_history_chip_0")
            .fetchSemanticsNode()
            .boundsInRoot
        val textBounds = composeRule
            .onNodeWithText("After Hours")
            .fetchSemanticsNode()
            .boundsInRoot
        val leftInsetDp = with(composeRule.density) { (textBounds.left - chipBounds.left).toDp() }

        assertTrue(
            "Expected history keyword text to sit close to the left edge without a leading icon, but inset was $leftInsetDp",
            leftInsetDp <= 18.dp
        )
        assertTrue(
            "Expected history keyword text to keep visible width instead of collapsing beside the remove button, chip=$chipBounds text=$textBounds",
            textBounds.width > chipBounds.width * 0.35f
        )
    }

    @Test
    fun resultMode_shouldRenderUnderlineTabsAndCompactResultRowsWithoutFakeMoreAction() {
        composeRule.setContent {
            SearchFeatureTheme {
                SearchScreen(
                    state = SearchUiState(
                        query = "无人知晓",
                        pageMode = SearchPageMode.RESULT,
                        selectedResultType = SearchResultType.SONG,
                        availableResultTypes = listOf(
                            SearchResultType.SONG,
                            SearchResultType.ALBUM,
                            SearchResultType.ARTIST
                        ),
                        resultStatesByType = mapOf(
                            SearchResultType.SONG to SearchResultUiState.Content(
                                listOf(
                                    SearchResultUiModel.Song(
                                        id = "song-1",
                                        title = "无人知晓",
                                        artistText = "田馥甄",
                                        albumTitle = "无人知晓",
                                        coverUrl = null,
                                        routeTarget = SearchRouteTarget.Song(songId = "song-1")
                                    )
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onQueryChanged = {},
                    onSubmitSearch = {},
                    onHistoryKeywordClick = {},
                    onRemoveHistoryKeyword = {},
                    onClearHistory = {},
                    onSuggestionClick = {},
                    onHotKeywordClick = {},
                    onResultTypeSelected = {},
                    onResultClick = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onAllNodesWithTag("search_result_type_song_indicator", useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onAllNodesWithTag("search_result_type_album_indicator", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onNodeWithTag("search_result_card_song-1").assertIsDisplayed()
        composeRule.onAllNodesWithTag("search_result_more_song-1").assertCountEquals(0)
    }

    @Test
    fun resultMode_selectedTypeWithoutCachedState_shouldRenderLoadingInsteadOfBlankPage() {
        composeRule.setContent {
            SearchFeatureTheme {
                SearchScreen(
                    state = SearchUiState(
                        query = "周杰伦",
                        pageMode = SearchPageMode.RESULT,
                        lastSubmittedQuery = "周杰伦",
                        selectedResultType = SearchResultType.ALBUM,
                        availableResultTypes = listOf(
                            SearchResultType.SONG,
                            SearchResultType.ALBUM
                        ),
                        resultStatesByType = mapOf(
                            SearchResultType.SONG to SearchResultUiState.Content(
                                listOf(
                                    SearchResultUiModel.Song(
                                        id = "song-1",
                                        title = "晴天",
                                        artistText = "周杰伦",
                                        albumTitle = "叶惠美",
                                        coverUrl = null,
                                        routeTarget = SearchRouteTarget.Song(songId = "song-1")
                                    )
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onQueryChanged = {},
                    onSubmitSearch = {},
                    onHistoryKeywordClick = {},
                    onRemoveHistoryKeyword = {},
                    onClearHistory = {},
                    onSuggestionClick = {},
                    onHotKeywordClick = {},
                    onResultTypeSelected = {},
                    onResultClick = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("search_result_pager").assertIsDisplayed()
        composeRule.onNodeWithText("搜索中").assertIsDisplayed()
    }

    @Test
    fun suggestMode_shouldKeepSuggestionCardsRenderableAfterThemeTokenRefresh() {
        composeRule.setContent {
            SearchFeatureTheme {
                SearchScreen(
                    state = SearchUiState(
                        query = "海阔天空",
                        pageMode = SearchPageMode.SUGGEST,
                        suggestState = SearchSuggestUiState.Content(
                            listOf(
                                SearchSuggestionUiModel(keyword = "海阔天空"),
                                SearchSuggestionUiModel(keyword = "海阔天空 beyond")
                            )
                        )
                    ),
                    onBack = {},
                    onQueryChanged = {},
                    onSubmitSearch = {},
                    onHistoryKeywordClick = {},
                    onRemoveHistoryKeyword = {},
                    onClearHistory = {},
                    onSuggestionClick = {},
                    onHotKeywordClick = {},
                    onResultTypeSelected = {},
                    onResultClick = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("search_suggestion_list").assertIsDisplayed()
        composeRule.onNodeWithTag("search_suggestion_item_0").assertIsDisplayed()
        composeRule.onNodeWithTag("search_suggestion_item_1").assertIsDisplayed()
    }
}
