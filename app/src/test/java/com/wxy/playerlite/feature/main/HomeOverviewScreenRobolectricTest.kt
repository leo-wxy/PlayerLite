package com.wxy.playerlite.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.ui.components.PlaylistBottomSheet
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeOverviewScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun discoveryItems_shouldExposeClickActionAcrossLayouts() {
        composeRule.setContent {
            PlayerLiteTheme {
                HomeOverviewScreen(
                    playerState = PlayerUiState(
                        selectedFileName = "晴天.mp3",
                        statusText = "点击底部按钮进入播放页"
                    ),
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = listOf(
                            HomeSectionUiModel(
                                code = "HOMEPAGE_BANNER",
                                title = "",
                                layout = HomeSectionLayout.BANNER,
                                items = listOf(
                                    HomeSectionItemUiModel(
                                        id = "banner-1",
                                        title = "今日推荐",
                                        subtitle = "每日精选",
                                        imageUrl = null,
                                        badge = "精选"
                                    )
                                )
                            ),
                            HomeSectionUiModel(
                                code = "HOMEPAGE_BLOCK_PLAYLIST_RCMD",
                                title = "推荐歌单",
                                layout = HomeSectionLayout.HORIZONTAL_LIST,
                                items = listOf(
                                    HomeSectionItemUiModel(
                                        id = "playlist-1",
                                        title = "歌单 A",
                                        subtitle = "晚安歌单",
                                        imageUrl = null
                                    )
                                )
                            ),
                            HomeSectionUiModel(
                                code = "HOMEPAGE_SHORTCUT",
                                title = "快捷入口",
                                layout = HomeSectionLayout.ICON_GRID,
                                items = listOf(
                                    HomeSectionItemUiModel(
                                        id = "shortcut-1",
                                        title = "每日推荐",
                                        subtitle = "",
                                        imageUrl = null
                                    )
                                )
                            )
                        ),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {},
                    onOpenPlayer = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_banner_card_banner-1").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("home_discovery_card_playlist-1").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("home_discovery_list")
            .performScrollToNode(hasTestTag("home_compact_card_shortcut-1"))
        composeRule.onNodeWithTag("home_compact_card_shortcut-1").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun miniPlayerBar_shouldRenderParsedTrackInfoAndIndependentControls() {
        composeRule.setContent {
            PlayerLiteTheme {
                HomeOverviewScreen(
                    playerState = PlayerUiState(
                        selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                        statusText = "正在播放"
                    ),
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = emptyList(),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {},
                    onOpenPlayer = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_mini_player_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("home_mini_player_artwork", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_mini_player_body").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("home_mini_player_song_area", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_mini_player_play_pause_button").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("home_mini_player_playlist_button").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("home_mini_player_title", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_mini_player_artist", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("尘大师 Lightly").assertIsDisplayed()
        composeRule.onNodeWithText("陈奕迅").assertIsDisplayed()
    }

    @Test
    fun miniPlayerBar_shouldPreferRuntimeTrackMetadataOverFileNameParsing() {
        composeRule.setContent {
            PlayerLiteTheme {
                HomeOverviewScreen(
                    playerState = PlayerUiState(
                        selectedFileName = "raw-cache-file-name.mp3",
                        currentTrackTitle = "尘大师 Lightly",
                        currentTrackArtist = "陈奕迅",
                        currentCoverUrl = "https://example.com/lightly.jpg",
                        statusText = "正在播放"
                    ),
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = emptyList(),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {},
                    onOpenPlayer = {}
                )
            }
        }

        composeRule.onNodeWithText("尘大师 Lightly").assertIsDisplayed()
        composeRule.onNodeWithText("陈奕迅").assertIsDisplayed()
        composeRule.onAllNodesWithText("raw-cache-file-name").assertCountEquals(0)
    }

    @Test
    fun miniPlayerBar_shouldRenderRuntimeCoverInsteadOfPlaceholder() {
        composeRule.setContent {
            PlayerLiteTheme {
                HomeOverviewScreen(
                    playerState = PlayerUiState(
                        selectedFileName = "raw-cache-file-name.mp3",
                        currentTrackTitle = "尘大师 Lightly",
                        currentTrackArtist = "陈奕迅",
                        currentCoverUrl = "https://example.com/lightly.jpg",
                        statusText = "正在播放"
                    ),
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = emptyList(),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {},
                    onOpenPlayer = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_mini_player_artwork_image", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag("home_mini_player_artwork_placeholder", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun miniPlayerBar_withoutAnyCover_shouldRenderPlaceholderArtwork() {
        composeRule.setContent {
            PlayerLiteTheme {
                HomeOverviewScreen(
                    playerState = PlayerUiState(
                        selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                        statusText = "正在播放"
                    ),
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = emptyList(),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {},
                    onOpenPlayer = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_mini_player_artwork_placeholder", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag("home_mini_player_artwork_image", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun miniPlayerBar_bodyClick_shouldOpenPlayer() {
        var openPlayerCount = 0

        composeRule.setContent {
            PlayerLiteTheme {
                HomeOverviewScreen(
                    playerState = PlayerUiState(
                        selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                        statusText = "已暂停"
                    ),
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = emptyList(),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {},
                    onOpenPlayer = { openPlayerCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("home_mini_player_body").performClick()
        composeRule.runOnIdle {
            assertEquals(1, openPlayerCount)
        }
    }

    @Test
    fun miniPlayerBar_localButtons_shouldDispatchIndependentCallbacks() {
        var openPlayerCount = 0
        var togglePlaybackCount = 0
        var openPlaylistCount = 0

        composeRule.setContent {
            PlayerLiteTheme {
                HomeOverviewScreen(
                    playerState = PlayerUiState(
                        selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                        statusText = "正在播放"
                    ),
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = emptyList(),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {},
                    onOpenPlayer = { openPlayerCount += 1 },
                    onTogglePlayback = { togglePlaybackCount += 1 },
                    onOpenPlaylist = { openPlaylistCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("home_mini_player_play_pause_button").performClick()
        composeRule.onNodeWithTag("home_mini_player_playlist_button").performClick()

        composeRule.runOnIdle {
            assertEquals(0, openPlayerCount)
            assertEquals(1, togglePlaybackCount)
            assertEquals(1, openPlaylistCount)
        }
    }

    @Test
    fun miniPlayerBar_playlistButton_shouldOpenSharedPlaylistSheet() {
        var showPlaylistSheet by mutableStateOf(false)

        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    HomeOverviewScreen(
                        playerState = PlayerUiState(
                            selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                            statusText = "正在播放"
                        ),
                        overviewState = HomeOverviewUiState(
                            isLoading = false,
                            sections = emptyList(),
                            searchKeywords = listOf("默认热搜")
                        ),
                        onSearchClick = {},
                        onRetry = {},
                        onItemClick = {},
                        onOpenPlayer = {},
                        onTogglePlayback = {},
                        onOpenPlaylist = { showPlaylistSheet = true }
                    )

                    PlaylistBottomSheet(
                        visible = showPlaylistSheet,
                        items = listOf(
                            PlaylistItem(
                                id = "track-1",
                                uri = "file:///lightly.mp3",
                                displayName = "陈奕迅 - 尘大师 Lightly.mp3"
                            )
                        ),
                        activeIndex = 0,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorder = true,
                        onDismiss = { showPlaylistSheet = false },
                        onShowOriginalOrderInShuffleChange = {},
                        onSelect = {},
                        onRemove = {},
                        onMove = { _, _ -> },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home_mini_player_playlist_button").performClick()
        composeRule.onNodeWithText("播放列表").assertIsDisplayed()
    }

    @Test
    fun miniPlayerBar_swipeLeft_shouldDispatchNextTrackWithoutOpeningPlayer() {
        var openPlayerCount = 0
        var skipNextCount = 0

        composeRule.setContent {
            PlayerLiteTheme {
                HomeOverviewScreen(
                    playerState = PlayerUiState(
                        selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                        statusText = "正在播放",
                        playlistItems = listOf(
                            PlaylistItem(id = "track-0", uri = "file:///current.mp3", displayName = "陈奕迅 - 尘大师 Lightly.mp3"),
                            PlaylistItem(id = "track-1", uri = "file:///next.mp3", displayName = "下一首")
                        ),
                        activePlaylistIndex = 0
                    ),
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = emptyList(),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {},
                    onOpenPlayer = { openPlayerCount += 1 },
                    onSkipNext = { skipNextCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("home_mini_player_song_area", useUnmergedTree = true).performTouchInput {
            swipeLeft()
        }

        composeRule.runOnIdle {
            assertEquals(0, openPlayerCount)
            assertEquals(1, skipNextCount)
        }
    }

    @Test
    fun miniPlayerBar_swipeRight_shouldDispatchPreviousTrackWithoutOpeningPlayer() {
        var openPlayerCount = 0
        var skipPreviousCount = 0

        composeRule.setContent {
            PlayerLiteTheme {
                HomeOverviewScreen(
                    playerState = PlayerUiState(
                        selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                        statusText = "正在播放",
                        playlistItems = listOf(
                            PlaylistItem(id = "track-0", uri = "file:///prev.mp3", displayName = "上一首"),
                            PlaylistItem(id = "track-1", uri = "file:///current.mp3", displayName = "陈奕迅 - 尘大师 Lightly.mp3")
                        ),
                        activePlaylistIndex = 1
                    ),
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = emptyList(),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {},
                    onOpenPlayer = { openPlayerCount += 1 },
                    onSkipPrevious = { skipPreviousCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("home_mini_player_song_area", useUnmergedTree = true).performTouchInput {
            swipeRight()
        }

        composeRule.runOnIdle {
            assertEquals(0, openPlayerCount)
            assertEquals(1, skipPreviousCount)
        }
    }

    @Test
    fun miniPlayerBar_shortSwipe_shouldNotChangeTrackOrOpenPlayer() {
        var openPlayerCount = 0
        var skipPreviousCount = 0
        var skipNextCount = 0

        composeRule.setContent {
            PlayerLiteTheme {
                HomeOverviewScreen(
                    playerState = PlayerUiState(
                        selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                        statusText = "正在播放"
                    ),
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = emptyList(),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {},
                    onOpenPlayer = { openPlayerCount += 1 },
                    onSkipPrevious = { skipPreviousCount += 1 },
                    onSkipNext = { skipNextCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("home_mini_player_song_area", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(-20f, 0f))
            up()
        }

        composeRule.runOnIdle {
            assertEquals(0, openPlayerCount)
            assertEquals(0, skipPreviousCount)
            assertEquals(0, skipNextCount)
        }
    }

    @Test
    fun miniPlayerBar_swipeWithoutAdjacentTrack_shouldBounceBackWithoutDispatching() {
        var openPlayerCount = 0
        var skipPreviousCount = 0
        var skipNextCount = 0

        composeRule.setContent {
            PlayerLiteTheme {
                HomeOverviewScreen(
                    playerState = PlayerUiState(
                        selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                        statusText = "正在播放",
                        playlistItems = listOf(
                            PlaylistItem(id = "track-0", uri = "file:///current.mp3", displayName = "陈奕迅 - 尘大师 Lightly.mp3")
                        ),
                        activePlaylistIndex = 0
                    ),
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = emptyList(),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {},
                    onOpenPlayer = { openPlayerCount += 1 },
                    onSkipPrevious = { skipPreviousCount += 1 },
                    onSkipNext = { skipNextCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("home_mini_player_song_area", useUnmergedTree = true).performTouchInput {
            swipeLeft()
        }

        composeRule.runOnIdle {
            assertEquals(0, openPlayerCount)
            assertEquals(0, skipPreviousCount)
            assertEquals(0, skipNextCount)
        }
    }

    @Test
    fun miniPlayerBar_longTitle_shouldRenderInSongArea() {
        composeRule.setContent {
            PlayerLiteTheme {
                HomeOverviewScreen(
                    playerState = PlayerUiState(
                        selectedFileName = "陈奕迅 - 一首为了验证首页迷你播放条标题过长时需要跑马灯展示效果的超长歌曲名称.mp3",
                        statusText = "正在播放"
                    ),
                    overviewState = HomeOverviewUiState(
                        isLoading = false,
                        sections = emptyList(),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {},
                    onOpenPlayer = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_mini_player_song_area", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_mini_player_title", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("一首为了验证首页迷你播放条标题过长时需要跑马灯展示效果的超长歌曲名称").assertIsDisplayed()
    }
}
