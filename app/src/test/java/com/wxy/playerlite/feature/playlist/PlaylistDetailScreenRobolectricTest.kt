package com.wxy.playerlite.feature.playlist

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistDetailScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentState_shouldShowHeroAndTrackList() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlaylistDetailScreen(
                    state = PlaylistDetailUiState(
                        headerState = PlaylistHeaderUiState.Content(
                            PlaylistHeaderContent(
                                playlistId = "3778678",
                                title = "热歌榜",
                                creatorName = "网易云音乐",
                                description = "云音乐热歌榜",
                                coverUrl = null,
                                trackCount = 200,
                                playCount = 13755319296L,
                                subscribedCount = 12882840L
                            )
                        ),
                        tracksState = PlaylistTracksUiState.Content(
                            listOf(
                                PlaylistTrackRow(
                                    trackId = "1973665667",
                                    title = "海屿你",
                                    artistText = "马也_Crabbit",
                                    albumTitle = "海屿你",
                                    coverUrl = null,
                                    durationMs = 295940L
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onRetry = {},
                    onLoadMore = {}
                )
            }
        }

        composeRule.onNodeWithTag("playlist_detail_hero_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_detail_cover").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_tracks_section").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_creator_meta").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_track_count_meta").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("playlist_track_1973665667"))
        composeRule.onNodeWithTag("playlist_track_1973665667").assertIsDisplayed()
    }

    @Test
    fun clickingHeroDescriptionPreview_shouldShowFullDescriptionSheet() {
        val longDescription = "热歌榜简介第一段。热歌榜简介第二段。热歌榜简介第三段。"

        composeRule.setContent {
            PlayerLiteTheme {
                PlaylistDetailScreen(
                    state = PlaylistDetailUiState(
                        headerState = PlaylistHeaderUiState.Content(
                            PlaylistHeaderContent(
                                playlistId = "3778678",
                                title = "热歌榜",
                                creatorName = "网易云音乐",
                                description = longDescription,
                                coverUrl = null,
                                trackCount = 200,
                                playCount = 13755319296L,
                                subscribedCount = 12882840L
                            )
                        ),
                        tracksState = PlaylistTracksUiState.Empty
                    ),
                    onBack = {},
                    onRetry = {},
                    onLoadMore = {}
                )
            }
        }

        composeRule.onNodeWithTag("playlist_description_preview").assertIsDisplayed().performClick()
        composeRule.onAllNodesWithTag("playlist_description_card").assertCountEquals(0)

        composeRule.onNodeWithTag("playlist_description_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_description_sheet_scroll").assertIsDisplayed()
        composeRule.onNodeWithText(longDescription).assertIsDisplayed()
    }

    @Test
    fun tracksError_shouldKeepHeroAndShowRetry() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlaylistDetailScreen(
                    state = PlaylistDetailUiState(
                        headerState = PlaylistHeaderUiState.Content(
                            PlaylistHeaderContent(
                                playlistId = "3778678",
                                title = "热歌榜",
                                creatorName = "网易云音乐",
                                description = "云音乐热歌榜",
                                coverUrl = null,
                                trackCount = 200,
                                playCount = 13755319296L,
                                subscribedCount = 12882840L
                            )
                        ),
                        tracksState = PlaylistTracksUiState.Error("歌曲列表加载失败")
                    ),
                    onBack = {},
                    onRetry = {},
                    onLoadMore = {}
                )
            }
        }

        composeRule.onNodeWithTag("playlist_detail_hero_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_tracks_error").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
    }

    @Test
    fun loadingMoreTracks_shouldShowPagingFooter() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlaylistDetailScreen(
                    state = PlaylistDetailUiState(
                        headerState = PlaylistHeaderUiState.Content(
                            PlaylistHeaderContent(
                                playlistId = "3778678",
                                title = "热歌榜",
                                creatorName = "网易云音乐",
                                description = "云音乐热歌榜",
                                coverUrl = null,
                                trackCount = 200,
                                playCount = 13755319296L,
                                subscribedCount = 12882840L
                            )
                        ),
                        tracksState = PlaylistTracksUiState.Content(
                            items = listOf(
                                PlaylistTrackRow(
                                    trackId = "1973665667",
                                    title = "海屿你",
                                    artistText = "马也_Crabbit",
                                    albumTitle = "海屿你",
                                    coverUrl = null,
                                    durationMs = 295940L
                                )
                            ),
                            isLoadingMore = true
                        )
                    ),
                    onBack = {},
                    onRetry = {},
                    onLoadMore = {}
                )
            }
        }

        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("playlist_tracks_load_more_loading"))
        composeRule.onNodeWithTag("playlist_tracks_load_more_loading").assertIsDisplayed()
    }

    @Test
    fun loadMoreFailure_shouldShowPagingErrorFooter() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlaylistDetailScreen(
                    state = PlaylistDetailUiState(
                        headerState = PlaylistHeaderUiState.Content(
                            PlaylistHeaderContent(
                                playlistId = "3778678",
                                title = "热歌榜",
                                creatorName = "网易云音乐",
                                description = "云音乐热歌榜",
                                coverUrl = null,
                                trackCount = 200,
                                playCount = 13755319296L,
                                subscribedCount = 12882840L
                            )
                        ),
                        tracksState = PlaylistTracksUiState.Content(
                            items = listOf(
                                PlaylistTrackRow(
                                    trackId = "1973665667",
                                    title = "海屿你",
                                    artistText = "马也_Crabbit",
                                    albumTitle = "海屿你",
                                    coverUrl = null,
                                    durationMs = 295940L
                                )
                            ),
                            loadMoreErrorMessage = "下一页加载失败"
                        )
                    ),
                    onBack = {},
                    onRetry = {},
                    onLoadMore = {}
                )
            }
        }

        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("playlist_tracks_load_more_error"))
        composeRule.onNodeWithTag("playlist_tracks_load_more_error").assertIsDisplayed()
    }
}
