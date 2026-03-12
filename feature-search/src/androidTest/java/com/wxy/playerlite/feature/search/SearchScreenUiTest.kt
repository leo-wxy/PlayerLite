package com.wxy.playerlite.feature.search

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals

class SearchScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hotMode_shouldShowSearchInputAndHotList() {
        composeRule.setContent {
            SearchFeatureTheme {
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
                    onResultTypeSelected = {},
                    onResultClick = {},
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
            SearchFeatureTheme {
                SearchScreen(
                    state = SearchUiState(
                        query = "小美满",
                        pageMode = SearchPageMode.RESULT,
                        selectedResultType = SearchResultType.SONG,
                        availableResultTypes = listOf(
                            SearchResultType.SONG,
                            SearchResultType.ALBUM,
                            SearchResultType.ARTIST
                        ),
                        historyKeywords = emptyList(),
                        hotState = SearchHotUiState.Content(emptyList()),
                        resultStatesByType = mapOf(
                            SearchResultType.SONG to SearchResultUiState.Content(
                                listOf(
                                    SearchResultUiModel.Song(
                                        id = "1",
                                        title = "小美满",
                                        artistText = "周深",
                                        albumTitle = "小美满",
                                        coverUrl = null,
                                        routeTarget = SearchRouteTarget.Song(songId = "1")
                                    )
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
                    onResultTypeSelected = {},
                    onResultClick = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onAllNodesWithTag("search_history_pinned").assertCountEquals(0)
        composeRule.onNodeWithTag("search_result_type_pinned").assertIsDisplayed()
        composeRule.onNodeWithTag("search_result_type_song").assertIsDisplayed()
        composeRule.onNodeWithTag("search_result_type_album").assertIsDisplayed()
        composeRule.onNodeWithTag("search_result_list").assertIsDisplayed()
        composeRule.onNodeWithTag("search_result_card_1").assertIsDisplayed()
    }

    @Test
    fun resultMode_shouldEmitTypeSwitchAndResultClick() {
        var selectedType: SearchResultType? = null
        var clickedTarget: SearchRouteTarget? = null

        composeRule.setContent {
            SearchFeatureTheme {
                SearchScreen(
                    state = SearchUiState(
                        query = "周杰伦",
                        pageMode = SearchPageMode.RESULT,
                        selectedResultType = SearchResultType.SONG,
                        availableResultTypes = listOf(SearchResultType.SONG, SearchResultType.ALBUM),
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
                    onSuggestionClick = {},
                    onHotKeywordClick = {},
                    onResultTypeSelected = { selectedType = it },
                    onResultClick = { clickedTarget = it },
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("search_result_pager").performTouchInput {
            swipeLeft()
        }
        composeRule.onNodeWithTag("search_result_card_song-1").performClick()

        assertEquals(SearchResultType.ALBUM, selectedType)
        assertEquals(SearchRouteTarget.Song(songId = "song-1"), clickedTarget)
    }

    @Test
    fun resultMode_shouldExposeAllDocumentedTypeTabs() {
        composeRule.setContent {
            SearchFeatureTheme {
                SearchScreen(
                    state = SearchUiState(
                        query = "周杰伦",
                        pageMode = SearchPageMode.RESULT,
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
                    onSuggestionClick = {},
                    onHotKeywordClick = {},
                    onResultTypeSelected = {},
                    onResultClick = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("search_result_type_row")
            .performScrollToNode(hasTestTag("search_result_type_voice"))
        composeRule.onNodeWithTag("search_result_type_voice").assertIsDisplayed()
    }

    @Test
    fun resultMode_shouldAutoScrollTypeRowWhenSelectedTypeIsOffscreen() {
        composeRule.setContent {
            SearchFeatureTheme {
                SearchScreen(
                    state = SearchUiState(
                        query = "周杰伦",
                        pageMode = SearchPageMode.RESULT,
                        selectedResultType = SearchResultType.VOICE,
                        resultStatesByType = mapOf(
                            SearchResultType.VOICE to SearchResultUiState.Content(
                                listOf(
                                    SearchResultUiModel.Generic(
                                        id = "voice-1",
                                        resultType = SearchResultType.VOICE,
                                        title = "晚安故事",
                                        subtitle = "声音 · 主播",
                                        tertiary = "120 次播放",
                                        coverUrl = null,
                                        routeTarget = SearchRouteTarget.Generic(
                                            resultType = SearchResultType.VOICE,
                                            targetId = "voice-1"
                                        )
                                    )
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
                    onResultTypeSelected = {},
                    onResultClick = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("search_result_type_voice").assertIsDisplayed()
    }
}
