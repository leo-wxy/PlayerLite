package com.wxy.playerlite.feature.search

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchResultTypeChipRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resultMode_shouldKeepTypeChipsAtFixedHeight() {
        composeRule.setContent {
            SearchFeatureTheme {
                SearchScreen(
                    state = SearchUiState(
                        query = "周杰伦",
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

        composeRule.onNodeWithTag("search_result_type_song")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(SEARCH_RESULT_TYPE_CHIP_HEIGHT)
        composeRule.onNodeWithTag("search_result_type_album")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(SEARCH_RESULT_TYPE_CHIP_HEIGHT)
        composeRule.onNodeWithTag("search_result_type_artist")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(SEARCH_RESULT_TYPE_CHIP_HEIGHT)
    }

    @Test
    fun resultMode_shouldCenterChipLabelWithinChipBounds() {
        composeRule.setContent {
            SearchFeatureTheme {
                SearchScreen(
                    state = SearchUiState(
                        query = "周杰伦",
                        pageMode = SearchPageMode.RESULT,
                        selectedResultType = SearchResultType.ALBUM,
                        availableResultTypes = listOf(
                            SearchResultType.SONG,
                            SearchResultType.ALBUM,
                            SearchResultType.ARTIST
                        ),
                        resultStatesByType = mapOf(
                            SearchResultType.ALBUM to SearchResultUiState.Empty
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

        val chipBounds = composeRule
            .onNodeWithTag("search_result_type_album")
            .fetchSemanticsNode()
            .boundsInRoot
        val labelBounds = composeRule
            .onNodeWithTag("search_result_type_label_album", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        val chipCenterX = (chipBounds.left + chipBounds.right) / 2f
        val chipCenterY = (chipBounds.top + chipBounds.bottom) / 2f
        val labelCenterX = (labelBounds.left + labelBounds.right) / 2f
        val labelCenterY = (labelBounds.top + labelBounds.bottom) / 2f

        assertTrue(
            "Expected chip label to stay horizontally centered, but centers were $labelCenterX vs $chipCenterX",
            abs(labelCenterX - chipCenterX) < 1f
        )
        assertTrue(
            "Expected chip label to stay vertically centered, but centers were $labelCenterY vs $chipCenterY",
            abs(labelCenterY - chipCenterY) < 1f
        )
    }
}
