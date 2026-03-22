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
import org.junit.Assert.assertEquals
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
                        ),
                        dynamicState = PlaylistDynamicUiState.Content(
                            PlaylistDynamicInfo(
                                commentCount = 9527,
                                isSubscribed = true,
                                playCount = 22334455L
                            )
                        )
                    ),
                    onBack = {},
                    onRetry = {},
                    onLoadMore = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("playlist_detail_hero_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_detail_cover").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_dynamic_meta_section").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_play_all_button").assertIsDisplayed()
        composeRule.onNodeWithText("播放全部").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_creator_meta").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_track_count_meta").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("playlist_tracks_list"))
        composeRule.onNodeWithTag("playlist_tracks_list")
            .performScrollToNode(hasTestTag("playlist_tracks_section"))
        composeRule.onNodeWithTag("playlist_tracks_section").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_tracks_list")
            .performScrollToNode(hasTestTag("playlist_track_1973665667"))
        composeRule.onNodeWithTag("playlist_track_1973665667").assertIsDisplayed()
    }

    @Test
    fun headerShouldNotShowDescriptionPreview_andDescriptionTabShouldBeAvailable() {
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
                    onLoadMore = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        composeRule.onAllNodesWithTag("playlist_description_preview").assertCountEquals(0)
        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("playlist_tab_description"))
        composeRule.onNodeWithTag("playlist_tab_description").assertIsDisplayed()
    }

    @Test
    fun tabsHeader_shouldShowTracksAndDescriptionTabs() {
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
                    onLoadMore = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("playlist_sticky_tabs_header"))
        composeRule.onNodeWithTag("playlist_sticky_tabs_header").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_tab_tracks").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_tab_description").assertIsDisplayed()
    }

    @Test
    fun dynamicMeta_shouldFormatPlayCountWithWRule() {
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
                        dynamicState = PlaylistDynamicUiState.Content(
                            PlaylistDynamicInfo(
                                commentCount = 9527,
                                isSubscribed = true,
                                playCount = 22334455L
                            )
                        ),
                        tracksState = PlaylistTracksUiState.Empty
                    ),
                    onBack = {},
                    onRetry = {},
                    onLoadMore = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("playlist_dynamic_meta_section").assertIsDisplayed()
        composeRule.onNodeWithText("9527").assertIsDisplayed()
        composeRule.onNodeWithText("2233.4w").assertIsDisplayed()
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
                    onLoadMore = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("playlist_detail_hero_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("playlist_tracks_list"))
        composeRule.onNodeWithTag("playlist_tracks_list")
            .performScrollToNode(hasTestTag("playlist_tracks_error"))
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
                    onLoadMore = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("playlist_tracks_list"))
        composeRule.onNodeWithTag("playlist_tracks_list")
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
                    onLoadMore = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("playlist_tracks_list"))
        composeRule.onNodeWithTag("playlist_tracks_list")
            .performScrollToNode(hasTestTag("playlist_tracks_load_more_error"))
        composeRule.onNodeWithTag("playlist_tracks_load_more_error").assertIsDisplayed()
    }

    @Test
    fun endReached_shouldShowPagingEndFooter() {
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
                                trackCount = 1,
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
                            endReached = true
                        )
                    ),
                    onBack = {},
                    onRetry = {},
                    onLoadMore = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("playlist_tracks_list"))
        composeRule.onNodeWithTag("playlist_tracks_list")
            .performScrollToNode(hasTestTag("playlist_tracks_load_more_end"))
        composeRule.onNodeWithTag("playlist_tracks_load_more_end").assertIsDisplayed()
    }

    @Test
    fun playAllAndTrackClick_shouldInvokeCallbacks() {
        var playAllClicks = 0
        var clickedTrackIndex = -1

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
                        dynamicState = PlaylistDynamicUiState.Content(
                            PlaylistDynamicInfo(
                                commentCount = 9527,
                                isSubscribed = true,
                                playCount = 22334455L
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
                    onLoadMore = {},
                    onPlayAll = { playAllClicks++ },
                    onTrackClick = { clickedTrackIndex = it }
                )
            }
        }

        composeRule.onNodeWithTag("playlist_play_all_button").performClick()
        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("playlist_tracks_list"))
        composeRule.onNodeWithTag("playlist_tracks_list")
            .performScrollToNode(hasTestTag("playlist_track_1973665667"))
        composeRule.onNodeWithTag("playlist_track_1973665667").performClick()

        assertEquals(1, playAllClicks)
        assertEquals(0, clickedTrackIndex)
    }
}
