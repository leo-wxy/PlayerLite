package com.wxy.playerlite.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
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
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.player.LyricLine
import com.wxy.playerlite.feature.player.ParsedLyrics
import com.wxy.playerlite.feature.player.model.PlayerLyricUiState
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.search.SearchRouteTarget
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeOverviewScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setHomeWithMiniPlayerOverlay(
        playerState: PlayerUiState,
        onOpenPlayer: () -> Unit = {},
        onTogglePlayback: () -> Unit = {},
        onOpenPlaylist: () -> Unit = {},
        onSkipPrevious: () -> Unit = {},
        onSkipNext: () -> Unit = {}
    ) {
        val effectivePlayerState = playerState.copy(hasSelection = true)
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    HomeOverviewScreen(
                        playerState = effectivePlayerState,
                        overviewState = HomeOverviewUiState(
                            isLoading = false,
                            sections = emptyList(),
                            searchKeywords = listOf("默认热搜")
                        ),
                        onSearchClick = {},
                        onRetry = {},
                        onItemClick = {}
                    )
                    MainShellMiniPlayerOverlay(
                        playerState = effectivePlayerState,
                        onOpenPlayer = onOpenPlayer,
                        onTogglePlayback = onTogglePlayback,
                        onOpenPlaylist = onOpenPlaylist,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext
                    )
                }
            }
        }
    }

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
                    onItemClick = {}
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
    fun homeSearchBox_shouldAlignWithHeroCardWidthBaseline() {
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
                            )
                        ),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {}
                )
            }
        }

        val searchBounds = composeRule
            .onNodeWithTag("home_search_box_container")
            .fetchSemanticsNode()
            .boundsInRoot
        val bannerBounds = composeRule
            .onNodeWithTag("home_banner_card_banner-1")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected search box and hero card to share the same left baseline, search=$searchBounds banner=$bannerBounds",
            kotlin.math.abs(searchBounds.left - bannerBounds.left) < 1f
        )
        assertTrue(
            "Expected search box and hero card to share the same right baseline, search=$searchBounds banner=$bannerBounds",
            kotlin.math.abs(searchBounds.right - bannerBounds.right) < 1f
        )
    }

    @Test
    fun shortcutCards_shouldExposeCompactIconAlongsideLabel() {
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
                    onItemClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_compact_card_icon_shortcut-1", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun songSections_shouldRenderCompactRowsWithMoreAction() {
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
                                code = "HOMEPAGE_BLOCK_STYLE_SONG",
                                title = "Songs You Might Like",
                                layout = HomeSectionLayout.HORIZONTAL_LIST,
                                items = listOf(
                                    HomeSectionItemUiModel(
                                        id = "song-1",
                                        title = "Neon Horizon",
                                        subtitle = "Luna Wave",
                                        imageUrl = null,
                                        action = ContentEntryAction.OpenDetail(
                                            SearchRouteTarget.Song(songId = "song-1")
                                        )
                                    )
                                )
                            )
                        ),
                        searchKeywords = listOf("默认热搜")
                    ),
                    onSearchClick = {},
                    onRetry = {},
                    onItemClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_song_row_song-1").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("home_song_more_song-1").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun miniPlayerBar_shouldRenderParsedTrackInfoAndIndependentControls() {
        setHomeWithMiniPlayerOverlay(
            playerState = PlayerUiState(
                selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                statusText = "正在播放"
            )
        )

        composeRule.onNodeWithTag("home_mini_player_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("home_mini_player_progress_line", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_mini_player_artwork", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_mini_player_body").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("home_mini_player_song_area", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_mini_player_play_pause_button").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("home_mini_player_playlist_button").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("home_mini_player_title", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_mini_player_title", useUnmergedTree = true)
            .assertTextEquals("尘大师 Lightly - 陈奕迅")
        composeRule.onAllNodesWithTag("home_mini_player_artist", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun miniPlayerBar_shouldPreferRuntimeTrackMetadataOverFileNameParsing() {
        setHomeWithMiniPlayerOverlay(
            playerState = PlayerUiState(
                selectedFileName = "raw-cache-file-name.mp3",
                currentTrackTitle = "尘大师 Lightly",
                currentTrackArtist = "陈奕迅",
                currentCoverUrl = "https://example.com/lightly.jpg",
                statusText = "正在播放"
            )
        )

        composeRule.onNodeWithTag("home_mini_player_title", useUnmergedTree = true)
            .assertTextEquals("尘大师 Lightly - 陈奕迅")
        composeRule.onAllNodesWithTag("home_mini_player_artist", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("raw-cache-file-name").assertCountEquals(0)
    }

    @Test
    fun miniPlayerBar_shouldPreferCurrentLyricInSingleLine() {
        setHomeWithMiniPlayerOverlay(
            playerState = PlayerUiState(
                selectedFileName = "周杰伦 - 夜曲.mp3",
                currentTrackTitle = "夜曲",
                currentTrackArtist = "周杰伦",
                lyricUiState = PlayerLyricUiState.Content(
                    lyrics = ParsedLyrics(
                        songId = "1973665667",
                        lines = listOf(
                            LyricLine(timestampMs = 1_000L, text = "第一句"),
                            LyricLine(timestampMs = 3_000L, text = "第二句")
                        ),
                        rawText = "[00:01.00]第一句\n[00:03.00]第二句"
                    )
                ),
                seekPositionMs = 3_500L,
                durationMs = 120_000L,
                statusText = "正在播放"
            )
        )

        composeRule.onNodeWithTag("home_mini_player_title", useUnmergedTree = true)
            .assertTextEquals("第二句")
        composeRule.onAllNodesWithTag("home_mini_player_artist", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("周杰伦 - 夜曲.mp3").assertCountEquals(0)
    }

    @Test
    fun miniPlayerBar_shouldRenderRuntimeCoverInsteadOfPlaceholder() {
        setHomeWithMiniPlayerOverlay(
            playerState = PlayerUiState(
                selectedFileName = "raw-cache-file-name.mp3",
                currentTrackTitle = "尘大师 Lightly",
                currentTrackArtist = "陈奕迅",
                currentCoverUrl = "https://example.com/lightly.jpg",
                statusText = "正在播放"
            )
        )

        composeRule.onNodeWithTag("home_mini_player_artwork_image", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag("home_mini_player_artwork_placeholder", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun miniPlayerBar_withoutAnyCover_shouldRenderPlaceholderArtwork() {
        setHomeWithMiniPlayerOverlay(
            playerState = PlayerUiState(
                selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                statusText = "正在播放"
            )
        )

        composeRule.onNodeWithTag("home_mini_player_artwork_placeholder", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag("home_mini_player_artwork_image", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun miniPlayerBar_bodyClick_shouldOpenPlayer() {
        var openPlayerCount = 0

        setHomeWithMiniPlayerOverlay(
            playerState = PlayerUiState(
                selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                statusText = "已暂停"
            ),
            onOpenPlayer = { openPlayerCount += 1 }
        )

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

        setHomeWithMiniPlayerOverlay(
            playerState = PlayerUiState(
                selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                statusText = "正在播放"
            ),
            onOpenPlayer = { openPlayerCount += 1 },
            onTogglePlayback = { togglePlaybackCount += 1 },
            onOpenPlaylist = { openPlaylistCount += 1 }
        )

        composeRule.onNodeWithTag("home_mini_player_play_pause_button").performClick()
        composeRule.onNodeWithTag("home_mini_player_playlist_button").performClick()

        composeRule.runOnIdle {
            assertEquals(0, openPlayerCount)
            assertEquals(1, togglePlaybackCount)
            assertEquals(1, openPlaylistCount)
        }
    }

    @Test
    fun miniPlayerBar_playlistButton_shouldDispatchOpenPlaylistRequestWithoutOpeningPlayer() {
        var openPlayerCount = 0
        var openPlaylistCount = 0

        setHomeWithMiniPlayerOverlay(
            playerState = PlayerUiState(
                selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                statusText = "正在播放"
            ),
            onOpenPlayer = { openPlayerCount += 1 },
            onOpenPlaylist = { openPlaylistCount += 1 }
        )

        composeRule.onNodeWithTag("home_mini_player_playlist_button").performClick()
        composeRule.runOnIdle {
            assertEquals(0, openPlayerCount)
            assertEquals(1, openPlaylistCount)
        }
    }

    @Test
    fun miniPlayerBar_swipeLeft_shouldDispatchNextTrackWithoutOpeningPlayer() {
        var openPlayerCount = 0
        var skipNextCount = 0

        setHomeWithMiniPlayerOverlay(
            playerState = PlayerUiState(
                selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                statusText = "正在播放",
                playlistItems = listOf(
                    PlaylistItem(id = "track-0", uri = "file:///current.mp3", displayName = "陈奕迅 - 尘大师 Lightly.mp3"),
                    PlaylistItem(id = "track-1", uri = "file:///next.mp3", displayName = "下一首")
                ),
                activePlaylistIndex = 0
            ),
            onOpenPlayer = { openPlayerCount += 1 },
            onSkipNext = { skipNextCount += 1 }
        )

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

        setHomeWithMiniPlayerOverlay(
            playerState = PlayerUiState(
                selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                statusText = "正在播放",
                playlistItems = listOf(
                    PlaylistItem(id = "track-0", uri = "file:///prev.mp3", displayName = "上一首"),
                    PlaylistItem(id = "track-1", uri = "file:///current.mp3", displayName = "陈奕迅 - 尘大师 Lightly.mp3")
                ),
                activePlaylistIndex = 1
            ),
            onOpenPlayer = { openPlayerCount += 1 },
            onSkipPrevious = { skipPreviousCount += 1 }
        )

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

        setHomeWithMiniPlayerOverlay(
            playerState = PlayerUiState(
                selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                statusText = "正在播放"
            ),
            onOpenPlayer = { openPlayerCount += 1 },
            onSkipPrevious = { skipPreviousCount += 1 },
            onSkipNext = { skipNextCount += 1 }
        )

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

        setHomeWithMiniPlayerOverlay(
            playerState = PlayerUiState(
                selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                statusText = "正在播放",
                playlistItems = listOf(
                    PlaylistItem(id = "track-0", uri = "file:///current.mp3", displayName = "陈奕迅 - 尘大师 Lightly.mp3")
                ),
                activePlaylistIndex = 0
            ),
            onOpenPlayer = { openPlayerCount += 1 },
            onSkipPrevious = { skipPreviousCount += 1 },
            onSkipNext = { skipNextCount += 1 }
        )

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
        setHomeWithMiniPlayerOverlay(
            playerState = PlayerUiState(
                selectedFileName = "陈奕迅 - 一首为了验证首页迷你播放条标题过长时需要跑马灯展示效果的超长歌曲名称.mp3",
                statusText = "正在播放"
            )
        )

        composeRule.onNodeWithTag("home_mini_player_song_area", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_mini_player_title", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("一首为了验证首页迷你播放条标题过长时需要跑马灯展示效果的超长歌曲名称 - 陈奕迅").assertIsDisplayed()
    }

    @Test
    fun miniPlayerBar_shouldUseMoreCompactSizing() {
        setHomeWithMiniPlayerOverlay(
            playerState = PlayerUiState(
                currentTrackTitle = "尘大师 Lightly",
                currentTrackArtist = "陈奕迅",
                currentCoverUrl = "https://example.com/lightly.jpg",
                statusText = "正在播放"
            )
        )

        val barBounds = composeRule
            .onNodeWithTag("home_mini_player_bar")
            .fetchSemanticsNode()
            .boundsInRoot
        val cardBounds = composeRule
            .onNodeWithTag("home_play_entry_card")
            .fetchSemanticsNode()
            .boundsInRoot
        val rootBounds = composeRule
            .onNodeWithTag("home_discovery_list")
            .fetchSemanticsNode()
            .boundsInRoot
        val artworkBounds = composeRule
            .onNodeWithTag("home_mini_player_artwork", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val songAreaBounds = composeRule
            .onNodeWithTag("home_mini_player_song_area", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val playPauseBounds = composeRule
            .onNodeWithTag("home_mini_player_play_pause_button")
            .fetchSemanticsNode()
            .boundsInRoot
        val playlistBounds = composeRule
            .onNodeWithTag("home_mini_player_playlist_button")
            .fetchSemanticsNode()
            .boundsInRoot
        val progressBounds = composeRule
            .onNodeWithTag("home_mini_player_progress_line", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val barHeightDp = with(composeRule.density) { barBounds.height.toDp() }
        val artworkWidthDp = with(composeRule.density) { artworkBounds.width.toDp() }
        val playButtonWidthDp = with(composeRule.density) { playPauseBounds.width.toDp() }
        val playlistButtonWidthDp = with(composeRule.density) { playlistBounds.width.toDp() }
        val cardWidthRatio = cardBounds.width / rootBounds.width
        val progressWidthRatio = progressBounds.width / barBounds.width
        val barCenterY = barBounds.center.y
        val artworkCenterOffset = kotlin.math.abs(artworkBounds.center.y - barCenterY)
        val playButtonCenterOffset = kotlin.math.abs(playPauseBounds.center.y - barCenterY)
        val verticalCenterTolerancePx = with(composeRule.density) { 3.dp.toPx() }
        val progressBottomDeltaPx = kotlin.math.abs(progressBounds.bottom - barBounds.bottom)
        val progressBottomTolerancePx = with(composeRule.density) { 1.dp.toPx() }
        val progressHeightDp = with(composeRule.density) { progressBounds.height.toDp() }
        val cardLeftInsetDp = with(composeRule.density) { (cardBounds.left - rootBounds.left).toDp() }
        val cardRightInsetDp = with(composeRule.density) { (rootBounds.right - cardBounds.right).toDp() }
        val artworkLeftInsetDp = with(composeRule.density) {
            (artworkBounds.left - barBounds.left).toDp()
        }
        val songAreaLeftInsetDp = with(composeRule.density) {
            (songAreaBounds.left - barBounds.left).toDp()
        }
        val playlistRightInsetDp = with(composeRule.density) {
            (barBounds.right - playlistBounds.right).toDp()
        }
        val horizontalInsetDeltaPx = kotlin.math.abs(
            (artworkBounds.left - barBounds.left) - (barBounds.right - playlistBounds.right)
        )
        val horizontalInsetTolerancePx = with(composeRule.density) { 2.dp.toPx() }

        assertTrue(
            "Expected mini player bar to keep the current slimmer height baseline, but was $barHeightDp",
            barHeightDp in 56.dp..62.dp
        )
        assertTrue(
            "Expected minibar card to stay close to the page horizontal baseline instead of shrinking too much, but ratio was $cardWidthRatio",
            cardWidthRatio in 0.89f..0.93f
        )
        assertTrue(
            "Expected minibar card left inset to stay close to the page gutter, but inset was $cardLeftInsetDp",
            cardLeftInsetDp in 14.dp..18.dp
        )
        assertTrue(
            "Expected minibar card right inset to stay close to the page gutter, but inset was $cardRightInsetDp",
            cardRightInsetDp in 14.dp..18.dp
        )
        assertTrue(
            "Expected artwork to read as a fuller square cover inside the minibar, but was $artworkWidthDp",
            artworkWidthDp in 30.dp..34.dp
        )
        assertTrue(
            "Expected play button to stay visually dominant like the reference minibar, but was $playButtonWidthDp",
            playButtonWidthDp in 32.dp..36.dp
        )
        assertTrue(
            "Expected playlist button to stay readable inside the minibar, but was $playlistButtonWidthDp",
            playlistButtonWidthDp in 26.dp..30.dp
        )
        assertTrue(
            "Expected top progress line to span almost the full minibar width, but ratio was $progressWidthRatio",
            progressWidthRatio >= 0.96f
        )
        assertTrue(
            "Expected minibar progress line bottom to align with the minibar bottom edge, progress=$progressBounds bar=$barBounds",
            progressBottomDeltaPx <= progressBottomTolerancePx
        )
        assertTrue(
            "Expected minibar progress line to stay slim but readable, but height was $progressHeightDp",
            progressHeightDp in 3.dp..5.dp
        )
        assertTrue(
            "Expected play button to remain inside compact bar, play=$playPauseBounds bar=$barBounds",
            playPauseBounds.bottom <= barBounds.bottom
        )
        assertTrue(
            "Expected playlist button to remain inside compact bar, playlist=$playlistBounds bar=$barBounds",
            playlistBounds.bottom <= barBounds.bottom
        )
        assertTrue(
            "Expected artwork block to stay vertically centered in minibar, offset=$artworkCenterOffset",
            artworkCenterOffset <= verticalCenterTolerancePx
        )
        assertTrue(
            "Expected artwork to leave a bit more breathing room from the left edge, but inset was $artworkLeftInsetDp",
            artworkLeftInsetDp in 10.dp..14.dp
        )
        assertTrue(
            "Expected song text block to shift right together with the larger artwork, but inset was $songAreaLeftInsetDp",
            songAreaLeftInsetDp in 46.dp..58.dp
        )
        assertTrue(
            "Expected playlist button to leave matching breathing room on the right edge, but inset was $playlistRightInsetDp",
            playlistRightInsetDp in 10.dp..14.dp
        )
        assertTrue(
            "Expected left artwork inset and right playlist inset to stay visually balanced, delta=$horizontalInsetDeltaPx",
            horizontalInsetDeltaPx <= horizontalInsetTolerancePx
        )
        assertTrue(
            "Expected song text block to stay to the right of artwork, artwork=$artworkBounds song=$songAreaBounds",
            songAreaBounds.left > artworkBounds.right
        )
        assertTrue(
            "Expected primary play button to stay vertically centered in minibar, offset=$playButtonCenterOffset",
            playButtonCenterOffset <= verticalCenterTolerancePx
        )
    }
}
