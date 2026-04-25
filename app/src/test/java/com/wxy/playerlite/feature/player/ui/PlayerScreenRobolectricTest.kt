package com.wxy.playerlite.feature.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.player.LyricLine
import com.wxy.playerlite.feature.player.ParsedLyrics
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.PlayerAudioQualityCatalogUiState
import com.wxy.playerlite.feature.player.model.PlayerLyricUiState
import com.wxy.playerlite.feature.player.model.PlayerMoreActionsPage
import com.wxy.playerlite.feature.player.model.PlayerOrientationMode
import com.wxy.playerlite.feature.player.model.PlayerTopTab
import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.model.SongAudioQualityCatalog
import com.wxy.playerlite.playback.model.SongAudioQualityOption
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class PlayerScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activePlayback_shouldUseSongTopBarAndReferenceInspiredInfoLayout() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_top_bar").assertIsDisplayed()
        composeRule.onAllNodesWithTag("player_screen_top_bar_surface").assertCountEquals(0)
        composeRule.onNodeWithTag("player_screen_top_tabs").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_top_tab_song").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_top_tab_lyrics").assertIsDisplayed()
        composeRule.onAllNodesWithTag("player_screen_top_tab_indicator_song", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithTag("player_screen_top_tab_indicator_lyrics", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag("player_screen_top_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_top_share_button").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回首页").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("分享当前歌曲").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("收起播放器").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("更多操作").assertCountEquals(1)
        composeRule.onNodeWithText("歌曲").assertIsDisplayed()
        composeRule.onNodeWithText("歌词").assertIsDisplayed()
        val songTabTextBounds = composeRule
            .onNodeWithText("歌曲")
            .fetchSemanticsNode()
            .boundsInRoot
        val lyricsTabTextBounds = composeRule
            .onNodeWithText("歌词")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "Expected song and lyrics labels to stay vertically aligned when the selection changes, but got song=${songTabTextBounds.center.y}, lyrics=${lyricsTabTextBounds.center.y}",
            kotlin.math.abs(songTabTextBounds.center.y - lyricsTabTextBounds.center.y) < 2f
        )
        composeRule.onNodeWithTag("player_screen_song_page").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_visual_section").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_cover_card").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_bottom_section").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_info_section").assertIsDisplayed()
        composeRule.onNodeWithText("歌词待补充").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_favorite_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_song_detail_tool_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_more_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_playlist_button").assertIsDisplayed()

        composeRule.onAllNodesWithText("Audio Info").assertCountEquals(0)
        composeRule.onAllNodesWithText("PLAYER LITE").assertCountEquals(0)
        composeRule.onAllNodesWithText("Local Audio Deck").assertCountEquals(0)
        composeRule.onAllNodesWithTag("login_entry_button_root").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_song_wiki_button").assertCountEquals(0)

        assertVerticalOrder(
            upperTag = "player_screen_title",
            lowerTag = "player_screen_artist"
        )
        assertVerticalOrder(
            upperTag = "player_screen_artist",
            lowerTag = "player_screen_lyric_placeholder"
        )
        assertVerticalOrder(
            upperTag = "player_screen_info_section",
            lowerTag = "player_screen_progress_section"
        )
        assertVerticalOrder(
            upperTag = "player_screen_progress_section",
            lowerTag = "player_screen_controls_section"
        )

        val backBounds = composeRule
            .onNodeWithTag("player_screen_top_back_button")
            .fetchSemanticsNode()
            .boundsInRoot
        val topBarBounds = composeRule
            .onNodeWithTag("player_screen_top_bar")
            .fetchSemanticsNode()
            .boundsInRoot
        val titleBounds = composeRule
            .onNodeWithTag("player_screen_top_tabs")
            .fetchSemanticsNode()
            .boundsInRoot
        val shareBounds = composeRule
            .onNodeWithTag("player_screen_top_share_button")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "Expected title to share the same top-bar baseline with back/share actions, but got title=${titleBounds.center.y}, back=${backBounds.center.y}, share=${shareBounds.center.y}",
            kotlin.math.abs(titleBounds.center.y - backBounds.center.y) < 4f &&
                kotlin.math.abs(titleBounds.center.y - shareBounds.center.y) < 4f
        )
        assertTrue(
            "Expected top tabs to stay absolutely centered in the top bar, but got tabs=${titleBounds.center.x}, bar=${topBarBounds.center.x}",
            kotlin.math.abs(titleBounds.center.x - topBarBounds.center.x) < 1.5f
        )
    }

    @Test
    fun activePlayback_narrowWidthTopTabs_shouldStayHorizontal() {
        composeRule.setContent {
            Box(
                modifier = Modifier.size(width = 320.dp, height = 720.dp)
            ) {
                PlayerLiteTheme {
                    PlayerScreen(
                        fileName = "夜曲",
                        artistText = "周杰伦",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 12_000L,
                        currentDurationText = "00:12",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        modifier = Modifier.fillMaxSize(),
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("player_screen_top_tabs").assertIsDisplayed()
        val songTabTextBounds = composeRule
            .onNodeWithText("歌曲")
            .fetchSemanticsNode()
            .boundsInRoot
        val lyricsTabTextBounds = composeRule
            .onNodeWithText("歌词")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected song and lyrics labels to remain on the same row on narrow width, but got song=${songTabTextBounds.center.y}, lyrics=${lyricsTabTextBounds.center.y}",
            abs(songTabTextBounds.center.y - lyricsTabTextBounds.center.y) < 2f
        )
        assertTrue(
            "Expected lyrics tab to stay to the right of song tab on narrow width, but got song=$songTabTextBounds lyrics=$lyricsTabTextBounds",
            lyricsTabTextBounds.center.x > songTabTextBounds.center.x
        )
    }

    @Test
    fun activePlayback_topTabs_shouldUseReadableVisualTokens() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    selectedTopTab = PlayerTopTab.SONG,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectTopTab = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        val songTabNode = composeRule
            .onNodeWithTag("player_screen_top_tab_song")
            .fetchSemanticsNode()
        val lyricsTabNode = composeRule
            .onNodeWithTag("player_screen_top_tab_lyrics")
            .fetchSemanticsNode()
        val songTabTextSize = songTabNode.config.getOrNull(PlayerTopTabTextSizeSpKey)
            ?: error("Missing song tab text size semantics")
        val lyricsTabTextSize = lyricsTabNode.config.getOrNull(PlayerTopTabTextSizeSpKey)
            ?: error("Missing lyrics tab text size semantics")
        val songTabFontWeight = songTabNode.config.getOrNull(PlayerTopTabFontWeightKey)
            ?: error("Missing song tab font weight semantics")
        val lyricsTabFontWeight = lyricsTabNode.config.getOrNull(PlayerTopTabFontWeightKey)
            ?: error("Missing lyrics tab font weight semantics")
        val songTabHorizontalPadding = songTabNode.config.getOrNull(PlayerTopTabHorizontalPaddingDpKey)
            ?: error("Missing song tab horizontal padding semantics")
        val lyricsTabHorizontalPadding = lyricsTabNode.config.getOrNull(PlayerTopTabHorizontalPaddingDpKey)
            ?: error("Missing lyrics tab horizontal padding semantics")
        val songTabIndicatorHeight = songTabNode.config.getOrNull(PlayerTopTabIndicatorHeightDpKey)
            ?: error("Missing song tab indicator height semantics")
        val lyricsTabIndicatorHeight = lyricsTabNode.config.getOrNull(PlayerTopTabIndicatorHeightDpKey)
            ?: error("Missing lyrics tab indicator height semantics")

        assertTrue(
            "Expected song tab text size to remain readable, but got $songTabTextSize",
            songTabTextSize >= 18f
        )
        assertTrue(
            "Expected lyrics tab text size to remain readable, but got $lyricsTabTextSize",
            lyricsTabTextSize >= 18f
        )
        assertEquals(
            500,
            songTabFontWeight
        )
        assertEquals(
            400,
            lyricsTabFontWeight
        )
        assertTrue(
            "Expected song tab horizontal padding to remain comfortable, but got $songTabHorizontalPadding",
            songTabHorizontalPadding >= 8f
        )
        assertTrue(
            "Expected lyrics tab horizontal padding to remain comfortable, but got $lyricsTabHorizontalPadding",
            lyricsTabHorizontalPadding >= 8f
        )
        assertEquals(
            2f,
            songTabIndicatorHeight,
            0.01f
        )
        assertEquals(
            2f,
            lyricsTabIndicatorHeight,
            0.01f
        )
    }

    @Test
    fun activePlayback_narrowWidthTopTabs_shouldStayCenteredAndClearEdgeActions() {
        composeRule.setContent {
            Box(
                modifier = Modifier.size(width = 320.dp, height = 720.dp)
            ) {
                PlayerLiteTheme {
                    PlayerScreen(
                        fileName = "夜曲",
                        artistText = "周杰伦",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        selectedTopTab = PlayerTopTab.SONG,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 12_000L,
                        currentDurationText = "00:12",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        modifier = Modifier.fillMaxSize(),
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectTopTab = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        val topBarBounds = composeRule
            .onNodeWithTag("player_screen_top_bar")
            .fetchSemanticsNode()
            .boundsInRoot
        val topTabsBounds = composeRule
            .onNodeWithTag("player_screen_top_tabs")
            .fetchSemanticsNode()
            .boundsInRoot
        val backBounds = composeRule
            .onNodeWithTag("player_screen_top_back_button")
            .fetchSemanticsNode()
            .boundsInRoot
        val actionsBounds = composeRule
            .onNodeWithTag("player_screen_top_actions")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected top tabs overlay to stay centered in the top bar, but got topBar=${topBarBounds.center.x}, tabs=${topTabsBounds.center.x}",
            abs(topBarBounds.center.x - topTabsBounds.center.x) < 1f
        )
        assertTrue(
            "Expected top tabs overlay to avoid the back button on narrow width, but got backRight=${backBounds.right}, tabsLeft=${topTabsBounds.left}",
            topTabsBounds.left - backBounds.right >= 4f
        )
        assertTrue(
            "Expected top tabs overlay to avoid trailing actions on narrow width, but got tabsRight=${topTabsBounds.right}, actionsLeft=${actionsBounds.left}",
            actionsBounds.left - topTabsBounds.right >= 4f
        )
    }

    @Test
    fun activePlayback_longTitle_shouldExposeSingleLineMarqueeConfiguration() {
        val longTitle = "这是一首名字很长很长很长很长很长的歌曲用来验证播放页标题跑马灯配置不会回归"

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = longTitle,
                    artistText = "测试歌手",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 8_000L,
                    currentDurationText = "00:08",
                    durationMs = 180_000L,
                    totalDurationText = "03:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        val titleNode = composeRule.onNodeWithTag("player_screen_title").fetchSemanticsNode()
        assertEquals(longTitle, titleNode.config.getOrElse(SemanticsProperties.Text) { emptyList() }.single().text)
        assertTrue(titleNode.config.getOrElse(PlayerTitleMarqueeEnabledKey) { false })
        assertTrue(titleNode.config.getOrElse(PlayerTitleSingleLineKey) { false })
    }

    @Test
    fun activePlayback_shouldExposeSongDetailActionAndInvokeCallback() {
        var detailClicks = 0

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {},
                    onOpenSongDetail = { detailClicks += 1 }
                )
            }
        }

        composeRule.onAllNodesWithTag("player_screen_song_detail_button").assertCountEquals(0)
        composeRule.onNodeWithTag("player_screen_song_detail_tool_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_song_detail_tool_button").performClick()

        composeRule.runOnIdle {
            assertEquals(1, detailClicks)
        }
    }

    @Test
    fun landscapeViewport_shouldUseDedicatedLandscapeLayoutAndExposeOrientationButton() {
        var orientationToggleCount = 0
        var requestedOrientationMode: PlayerOrientationMode? = null

        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 540.dp)) {
                    PlayerScreen(
                        fileName = "那天雨停了",
                        artistText = "田馥甄",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        orientationMode = PlayerOrientationMode.LANDSCAPE_LOCKED,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 2_500L,
                        currentDurationText = "00:02",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {},
                        onCycleOrientationMode = { targetMode ->
                            orientationToggleCount += 1
                            requestedOrientationMode = targetMode
                        }
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag("player_screen_landscape_root").assertCountEquals(1)
        composeRule.onNodeWithTag("player_screen_landscape_visual_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_landscape_controls_panel").assertIsDisplayed()
        composeRule.onAllNodesWithTag("player_screen_top_bar").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_top_tabs").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_landscape_visual_reflection").assertCountEquals(1)
        composeRule.onAllNodesWithTag("player_screen_landscape_controls_reflection").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_landscape_cover_backdrop").assertCountEquals(0)
        composeRule.onNodeWithTag("player_screen_landscape_overlay_actions").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_orientation_mode_button").assertIsDisplayed()
        composeRule.onAllNodesWithTag("player_screen_bottom_section").assertCountEquals(0)

        composeRule.onNodeWithTag("player_screen_orientation_mode_button").performClick()
        composeRule.runOnIdle {
            assertEquals(1, orientationToggleCount)
            assertEquals(PlayerOrientationMode.PORTRAIT_LOCKED, requestedOrientationMode)
        }
    }

    @Test
    fun landscapeViewport_shouldKeepSelectedLyricsTabAndActionsReachable() {
        var selectedTopTab by mutableStateOf(PlayerTopTab.LYRICS)

        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 540.dp)) {
                    PlayerScreen(
                        fileName = "那天雨停了",
                        artistText = "田馥甄",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        selectedTopTab = selectedTopTab,
                        orientationMode = PlayerOrientationMode.LANDSCAPE_LOCKED,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 12_000L,
                        currentDurationText = "00:12",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectTopTab = { selectedTopTab = it },
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag("player_screen_landscape_root").assertCountEquals(1)
        composeRule.onNodeWithTag("player_screen_lyrics_page").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_orientation_mode_button").assertIsDisplayed()
        composeRule.onAllNodesWithTag("player_screen_top_tabs").assertCountEquals(0)
    }

    @Test
    fun portraitLock_shouldKeepPortraitSongLayoutEvenOnWideViewport() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 180.dp)) {
                    PlayerScreen(
                        fileName = "那天雨停了",
                        artistText = "田馥甄",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        orientationMode = PlayerOrientationMode.PORTRAIT_LOCKED,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 12_000L,
                        currentDurationText = "00:12",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag("player_screen_landscape_root").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_landscape_visual_reflection").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_landscape_controls_reflection").assertCountEquals(0)
        composeRule.onNodeWithTag("player_screen_song_page").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_bottom_section").assertIsDisplayed()
    }

    @Test
    fun landscapeViewport_shouldRenderSubtleVisualReflectionOnly() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 540.dp)) {
                    PlayerScreen(
                        fileName = "那天雨停了",
                        artistText = "田馥甄",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        orientationMode = PlayerOrientationMode.LANDSCAPE_LOCKED,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 12_000L,
                        currentDurationText = "00:12",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag("player_screen_landscape_visual_reflection").assertCountEquals(1)
        composeRule.onAllNodesWithTag("player_screen_landscape_visual_projection").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_landscape_controls_reflection").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_cover_art_scrim").assertCountEquals(1)
    }

    @Test
    fun landscapeViewport_shouldRenderSubtleVisualReflectionFootprint() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 540.dp)) {
                    PlayerScreen(
                        fileName = "那天雨停了",
                        artistText = "田馥甄",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        orientationMode = PlayerOrientationMode.LANDSCAPE_LOCKED,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 12_000L,
                        currentDurationText = "00:12",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        val coverFrameBounds = composeRule
            .onNodeWithTag("player_screen_landscape_cover_frame")
            .fetchSemanticsNode()
            .boundsInRoot
        val reflectionBounds = composeRule
            .onNodeWithTag("player_screen_landscape_visual_reflection")
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.onNodeWithTag("player_screen_landscape_cover_frame").assertIsDisplayed()
        composeRule.onAllNodesWithTag("player_screen_landscape_visual_reflection").assertCountEquals(1)
        composeRule.onAllNodesWithTag("player_screen_landscape_visual_projection").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_landscape_controls_reflection").assertCountEquals(0)
        assertTrue(
            "Expected the landscape visual reflection to stay aligned with the cover bottom edge, but got cover=$coverFrameBounds reflection=$reflectionBounds",
            kotlin.math.abs(reflectionBounds.center.x - coverFrameBounds.center.x) <= 2f
        )
        assertTrue(
            "Expected the landscape visual reflection to begin immediately below the cover frame, but got cover=$coverFrameBounds reflection=$reflectionBounds",
            reflectionBounds.top >= coverFrameBounds.bottom - 2f &&
                reflectionBounds.top <= coverFrameBounds.bottom + 6f
        )
        assertTrue(
            "Expected the landscape visual reflection to stay short relative to the cover frame, but got cover=$coverFrameBounds reflection=$reflectionBounds",
            reflectionBounds.height <= coverFrameBounds.height * 0.28f
        )
    }

    @Test
    fun landscapeViewport_shouldRenderPosterInspiredHeroAndStageLayers() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 540.dp)) {
                    PlayerScreen(
                        fileName = "那天雨停了",
                        artistText = "田馥甄",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        orientationMode = PlayerOrientationMode.LANDSCAPE_LOCKED,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 12_000L,
                        currentDurationText = "00:12",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("player_screen_info_section").assertExists()
        composeRule.onAllNodesWithTag("player_screen_landscape_stage_glow").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_landscape_stage_outline_front").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_landscape_stage_outline_back").assertCountEquals(0)
        val visualSectionBounds = composeRule
            .onNodeWithTag("player_screen_visual_section")
            .fetchSemanticsNode()
            .boundsInRoot
        val visualPanelBounds = composeRule
            .onNodeWithTag("player_screen_landscape_visual_panel")
            .fetchSemanticsNode()
            .boundsInRoot
        val controlsPanelBounds = composeRule
            .onNodeWithTag("player_screen_landscape_controls_panel")
            .fetchSemanticsNode()
            .boundsInRoot
        val coverFrameBounds = composeRule
            .onNodeWithTag("player_screen_landscape_cover_frame")
            .fetchSemanticsNode()
            .boundsInRoot
        val reflectionBounds = composeRule
            .onNodeWithTag("player_screen_landscape_visual_reflection")
            .fetchSemanticsNode()
            .boundsInRoot
        val coverCardBounds = composeRule
            .onNodeWithTag("player_screen_cover_card")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "Expected right-side visual panel to remain substantial after shrinking the cover composition, but got visual=$visualPanelBounds controls=$controlsPanelBounds",
            visualPanelBounds.width >= controlsPanelBounds.width * 0.82f
        )
        assertTrue(
            "Expected landscape cover frame to keep a strong presence without filling the entire stage, but got cover=$coverFrameBounds visual=$visualSectionBounds",
            coverFrameBounds.width >= visualSectionBounds.width * 0.82f
        )
        assertTrue(
            "Expected cover frame to occupy most of the right-side panel while keeping more breathing room, but got cover=$coverFrameBounds panel=$visualPanelBounds",
            coverFrameBounds.width >= visualPanelBounds.width * 0.82f
        )
        assertTrue(
            "Expected landscape cover frame to stay close to a square while keeping the border, but got cover=$coverFrameBounds",
            kotlin.math.abs(coverFrameBounds.width - coverFrameBounds.height) <= visualSectionBounds.width * 0.06f
        )
        assertTrue(
            "Expected cover frame to stay horizontally centered in the remaining right-side space, but got cover=$coverFrameBounds panel=$visualPanelBounds",
            kotlin.math.abs(coverFrameBounds.center.x - visualPanelBounds.center.x) <= visualPanelBounds.width * 0.01f
        )
        assertTrue(
            "Expected the landscape cover frame itself to stay vertically centered in the right-side space, but got cover=$coverFrameBounds reflection=$reflectionBounds panel=$visualPanelBounds",
            kotlin.math.abs(coverFrameBounds.center.y - visualPanelBounds.center.y) <=
                visualPanelBounds.height * 0.035f
        )
        assertTrue(
            "Expected cover art to nearly fill the right-side host after removing the frame, but got cover=$coverCardBounds host=$coverFrameBounds",
            coverCardBounds.width >= coverFrameBounds.width * 0.985f
        )
        assertTrue(
            "Expected the reflection to stay directly below the landscape cover frame, but got cover=$coverFrameBounds reflection=$reflectionBounds",
            reflectionBounds.top >= coverFrameBounds.bottom - 2f &&
                reflectionBounds.top <= coverFrameBounds.bottom + 6f
        )
        composeRule.onAllNodesWithTag("player_screen_landscape_cover_backdrop").assertCountEquals(0)
    }

    @Test
    fun landscapeViewport_shouldHidePlaceholderLyricSummary() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 540.dp)) {
                    PlayerScreen(
                        fileName = "那天雨停了",
                        artistText = "田馥甄",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        lyricUiState = PlayerLyricUiState.Placeholder,
                        orientationMode = PlayerOrientationMode.LANDSCAPE_LOCKED,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 12_000L,
                        currentDurationText = "00:12",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("player_screen_info_section").assertExists()
        composeRule.onAllNodesWithTag("player_screen_lyric_placeholder").assertCountEquals(0)
    }

    @Test
    fun landscapeViewport_shouldShowActualLyricSummary() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 540.dp)) {
                    PlayerScreen(
                        fileName = "夜曲",
                        artistText = "周杰伦",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        lyricUiState = demoLyricUiState(),
                        orientationMode = PlayerOrientationMode.LANDSCAPE_LOCKED,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 12_000L,
                        currentDurationText = "00:12",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onRetryLyrics = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("player_screen_info_section").assertExists()
        composeRule.onNodeWithTag("player_screen_lyric_summary").assertIsDisplayed()
    }

    @Test
    fun landscapeViewport_shouldHidePassiveStatusChip() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 540.dp)) {
                    PlayerScreen(
                        fileName = "那天雨停了",
                        artistText = "田馥甄",
                        status = "Idle",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        orientationMode = PlayerOrientationMode.LANDSCAPE_LOCKED,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 12_000L,
                        currentDurationText = "00:12",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag("player_screen_status_chip").assertCountEquals(0)
    }

    @Test
    fun landscapeViewport_shouldKeepInfoBelowStatusAreaAndDockPlaybackControls() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 540.dp)) {
                    PlayerScreen(
                        fileName = "那天雨停了",
                        artistText = "田馥甄",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        orientationMode = PlayerOrientationMode.LANDSCAPE_LOCKED,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 12_000L,
                        currentDurationText = "00:12",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        val overlayBounds = composeRule
            .onNodeWithTag("player_screen_landscape_overlay_actions")
            .fetchSemanticsNode()
            .boundsInRoot
        val controlsPanelBounds = composeRule
            .onNodeWithTag("player_screen_landscape_controls_panel")
            .fetchSemanticsNode()
            .boundsInRoot
        val infoGroupBounds = composeRule
            .onNodeWithTag("player_screen_landscape_info_group")
            .fetchSemanticsNode()
            .boundsInRoot
        val bottomGroupBounds = composeRule
            .onNodeWithTag("player_screen_landscape_bottom_group")
            .fetchSemanticsNode()
            .boundsInRoot
        val progressBandBounds = composeRule
            .onNodeWithTag("player_screen_landscape_progress_band")
            .fetchSemanticsNode()
            .boundsInRoot
        val progressBounds = composeRule
            .onNodeWithTag("player_screen_progress_section")
            .fetchSemanticsNode()
            .boundsInRoot
        val controlsBounds = composeRule
            .onNodeWithTag("player_screen_controls_section")
            .fetchSemanticsNode()
            .boundsInRoot
        val controlsAnchorBounds = composeRule
            .onNodeWithTag("player_screen_landscape_controls_anchor")
            .fetchSemanticsNode()
            .boundsInRoot
        val visualAnchorBounds = composeRule
            .onNodeWithTag("player_screen_landscape_visual_anchor")
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.onNodeWithTag("player_screen_artist").assertExists()
        assertTrue(infoGroupBounds.top >= overlayBounds.bottom + 16f)
        assertTrue(infoGroupBounds.center.y <= controlsPanelBounds.top + (controlsPanelBounds.height * 0.30f))
        assertTrue(progressBandBounds.top >= infoGroupBounds.bottom + 24f)
        assertTrue(
            "Expected the progress band to stay closer to playback controls in landscape, but got progressBand=$progressBandBounds controls=$controlsBounds",
            progressBandBounds.bottom >= controlsBounds.top - 10f
        )
        assertTrue(
            "Expected compact landscape playback controls to stay visually tight, but got controls=$controlsBounds",
            controlsBounds.height <= 72f
        )
        assertTrue(
            "Expected playback controls to dock near the landscape visual baseline, but got controls=$controlsBounds visualAnchor=$visualAnchorBounds",
            controlsBounds.bottom >= visualAnchorBounds.bottom - 12f
        )
        assertTrue(
            "Expected playback controls to sit noticeably lower in the landscape panel, but got controls=$controlsBounds panel=$controlsPanelBounds",
            controlsBounds.bottom >= controlsPanelBounds.bottom - 18f
        )
        assertTrue(
            "Expected the bottom control group to sit near the bottom edge of the landscape panel, but got bottomGroup=$bottomGroupBounds panel=$controlsPanelBounds",
            bottomGroupBounds.bottom >= controlsPanelBounds.bottom - 64f
        )
        assertTrue(bottomGroupBounds.bottom >= controlsAnchorBounds.bottom - 1f)
        assertTrue(progressBounds.top >= progressBandBounds.top)
    }

    @Test
    fun landscapeViewport_shouldHideSecondaryToolActionsAndInlineStatus() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 540.dp)) {
                    PlayerScreen(
                        fileName = "那天雨停了",
                        artistText = "田馥甄",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        orientationMode = PlayerOrientationMode.LANDSCAPE_LOCKED,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 12_000L,
                        currentDurationText = "00:12",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag("player_screen_song_detail_tool_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_favorite_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_more_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_combined_status_row").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_playlist_button", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun landscapeViewport_shouldRemoveDecorativeBackdropLayers() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 540.dp)) {
                    PlayerScreen(
                        fileName = "那天雨停了",
                        artistText = "田馥甄",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        orientationMode = PlayerOrientationMode.LANDSCAPE_LOCKED,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 12_000L,
                        currentDurationText = "00:12",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag("player_screen_landscape_cover_backdrop").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_landscape_stage_glow").assertCountEquals(0)
    }

    @Test
    fun emptyPlayback_shouldShowSinglePickAudioEntryOnly() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "",
                    status = "尚未选择音频",
                    hasSelection = false,
                    playlistItems = emptyList(),
                    activePlaylistIndex = -1,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = 0,
                    isSeekSupported = false,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = false,
                    seekValueMs = 0L,
                    currentDurationText = "00:00",
                    durationMs = 0L,
                    totalDurationText = "00:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_empty_state").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_pick_audio_button").assertIsDisplayed()
        composeRule.onAllNodesWithTag("player_screen_progress_section").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_controls_section").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_song_wiki_button").assertCountEquals(0)
        composeRule.onAllNodesWithText("Audio Info").assertCountEquals(0)
        composeRule.onAllNodesWithTag("login_entry_button_root").assertCountEquals(0)
    }

    @Test
    fun activePlayback_controls_shouldKeepModeAndPlaylistActionsReachable() {
        var showPlaylistSheet by mutableStateOf(false)
        var playbackMode by mutableStateOf(PlaybackMode.LIST_LOOP)
        var playbackState by mutableStateOf(AUDIO_TRACK_PLAYSTATE_PLAYING)
        var detailClicks = 0

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "周杰伦 - 夜曲.mp3",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = showPlaylistSheet,
                    isPreparing = false,
                    playbackState = playbackState,
                    isSeekSupported = true,
                    playbackMode = playbackMode,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {
                        showPlaylistSheet = true
                    },
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {
                        playbackState = com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
                    },
                    onResume = {},
                    onCyclePlaybackMode = {
                        playbackMode = PlaybackMode.SINGLE_LOOP
                    },
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {},
                    onOpenSongDetail = { detailClicks += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_toggle_button").performClick()
        composeRule.onNodeWithTag("player_screen_playback_mode_button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("player_screen_playlist_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_song_detail_tool_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_song_detail_tool_button").performClick()
        composeRule.onNodeWithContentDescription("播放").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, detailClicks)
        }
    }

    @Test
    fun activePlayback_moreActionsSheet_shouldExposeEntriesAndInlineSpeedOptions() {
        var showMoreActionsSheet by mutableStateOf(false)
        var showAudioEffectPage by mutableStateOf(false)
        var moreActionsPage by mutableStateOf(PlayerMoreActionsPage.ROOT)
        var playbackSpeed by mutableStateOf(1.0f)
        var audioEffectPreset by mutableStateOf(AudioEffectPreset.OFF)

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "周杰伦 - 夜曲.mp3",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    showMoreActionsSheet = showMoreActionsSheet,
                    showAudioEffectPage = showAudioEffectPage,
                    moreActionsPage = moreActionsPage,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackSpeed = playbackSpeed,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    audioEffectPreset = audioEffectPreset,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {},
                    onMoreClick = {
                        showMoreActionsSheet = true
                        moreActionsPage = PlayerMoreActionsPage.ROOT
                    },
                    onDismissMoreActionsSheet = {
                        showMoreActionsSheet = false
                        moreActionsPage = PlayerMoreActionsPage.ROOT
                    },
                    onDismissAudioEffectPage = {
                        showAudioEffectPage = false
                    },
                    onShowPlaybackSpeedSettings = {
                        moreActionsPage = PlayerMoreActionsPage.SPEED
                    },
                    onShowAudioEffectSettings = {
                        showMoreActionsSheet = false
                        showAudioEffectPage = true
                    },
                    onReturnToMoreActionsRoot = {
                        moreActionsPage = PlayerMoreActionsPage.ROOT
                    },
                    onSelectPlaybackSpeed = { playbackSpeed = it },
                    onSelectAudioEffectPreset = { audioEffectPreset = it }
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_more_button").performClick()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_more_actions_sheet_surface").assertIsDisplayed()
        composeRule.onNodeWithText("音效设置").assertIsDisplayed()
        composeRule.onNodeWithText("倍速设置").assertIsDisplayed()

        composeRule.onNodeWithTag(
            "player_more_actions_entry_playback_speed",
            useUnmergedTree = true
        ).assert(hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(PlayerMoreActionsPage.SPEED, moreActionsPage)
        composeRule.onNodeWithTag("player_more_actions_speed_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("player_more_actions_speed_value").assertTextEquals("1.0X")
        composeRule.onNodeWithTag("player_more_actions_speed_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue("Expected speed slider progress action to succeed", setProgress(10f))
            }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_more_actions_speed_value").assertTextEquals("1.5X")
    }

    @Test
    fun activePlayback_audioEffectSelection_shouldUseBottomSheetAndUpdateCombinedStatusRow() {
        var showMoreActionsSheet by mutableStateOf(false)
        var showAudioEffectPage by mutableStateOf(false)
        var showAudioQualitySheet by mutableStateOf(false)
        var moreActionsPage by mutableStateOf(PlayerMoreActionsPage.ROOT)
        var playbackSpeed by mutableStateOf(1.0f)
        var audioEffectPreset by mutableStateOf(AudioEffectPreset.OFF)

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "周杰伦 - 夜曲.mp3",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    showMoreActionsSheet = showMoreActionsSheet,
                    showAudioEffectPage = showAudioEffectPage,
                    showAudioQualitySheet = showAudioQualitySheet,
                    moreActionsPage = moreActionsPage,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackSpeed = playbackSpeed,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    appliedAudioQuality = PlaybackAudioQuality.EXHIGH,
                    audioQualityCatalogUiState = PlayerAudioQualityCatalogUiState.Content(
                        SongAudioQualityCatalog(
                            songId = "347230",
                            options = listOf(
                                SongAudioQualityOption(
                                    quality = PlaybackAudioQuality.EXHIGH,
                                    rawKey = "exhigh",
                                    bitRate = 320_000,
                                    sampleRate = 44_100,
                                    sizeBytes = 8_798_445L
                                ),
                                SongAudioQualityOption(
                                    quality = PlaybackAudioQuality.HIRES,
                                    rawKey = "hires",
                                    bitRate = 999_000,
                                    sampleRate = 96_000,
                                    sizeBytes = 24_000_000L
                                )
                            )
                        )
                    ),
                    audioEffectPreset = audioEffectPreset,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {},
                    onMoreClick = {
                        showMoreActionsSheet = true
                        moreActionsPage = PlayerMoreActionsPage.ROOT
                    },
                    onDismissMoreActionsSheet = {
                        showMoreActionsSheet = false
                        moreActionsPage = PlayerMoreActionsPage.ROOT
                    },
                    onDismissAudioEffectPage = {
                        showAudioEffectPage = false
                    },
                    onDismissAudioQualitySheet = {
                        showAudioQualitySheet = false
                    },
                    onShowPlaybackSpeedSettings = {
                        moreActionsPage = PlayerMoreActionsPage.SPEED
                    },
                    onShowAudioEffectSettings = {
                        showMoreActionsSheet = false
                        showAudioQualitySheet = false
                        showAudioEffectPage = true
                    },
                    onShowAudioQualitySettings = {
                        showMoreActionsSheet = false
                        showAudioEffectPage = false
                        showAudioQualitySheet = true
                    },
                    onReturnToMoreActionsRoot = {
                        moreActionsPage = PlayerMoreActionsPage.ROOT
                    },
                    onSelectPlaybackSpeed = { playbackSpeed = it },
                    onSelectAudioEffectPreset = {
                        audioEffectPreset = it
                        showAudioEffectPage = false
                    },
                    onSelectAudioQuality = {}
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_audio_quality_name").assertTextEquals("极高")
        composeRule.onAllNodesWithTag("player_screen_audio_effect_name").assertCountEquals(0)

        composeRule.onNodeWithTag("player_screen_more_button").performClick()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(
            "player_more_actions_entry_audio_effect",
            useUnmergedTree = true
        ).assert(hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(showAudioEffectPage)
        assertFalse(showAudioQualitySheet)
        composeRule.onAllNodesWithTag("player_more_actions_sheet_surface").assertCountEquals(0)
        composeRule.onNodeWithTag("player_screen_audio_effect_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag(
            "player_screen_audio_effect_option_bright",
            useUnmergedTree = true
        ).assert(hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onAllNodesWithTag("player_more_actions_sheet_surface").assertCountEquals(0)
        composeRule.onAllNodesWithTag("player_screen_audio_effect_sheet").assertCountEquals(0)
        composeRule.onNodeWithTag("player_screen_audio_quality_name").assertTextEquals("极高")
        composeRule.onNodeWithTag("player_screen_audio_effect_name").assertTextEquals("清亮高频")
    }

    @Test
    fun combinedStatusRow_clickingQualityAndEffectSegmentsShouldToggleSheetsMutuallyExclusive() {
        var showAudioEffectPage by mutableStateOf(false)
        var showAudioQualitySheet by mutableStateOf(false)

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "周杰伦 - 夜曲.mp3",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    showMoreActionsSheet = false,
                    showAudioEffectPage = showAudioEffectPage,
                    showAudioQualitySheet = showAudioQualitySheet,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    appliedAudioQuality = PlaybackAudioQuality.HIRES,
                    audioQualityCatalogUiState = PlayerAudioQualityCatalogUiState.Content(
                        SongAudioQualityCatalog(
                            songId = "347230",
                            options = listOf(
                                SongAudioQualityOption(
                                    quality = PlaybackAudioQuality.EXHIGH,
                                    rawKey = "exhigh",
                                    bitRate = 320_000,
                                    sampleRate = 44_100,
                                    sizeBytes = 8_798_445L
                                ),
                                SongAudioQualityOption(
                                    quality = PlaybackAudioQuality.HIRES,
                                    rawKey = "hires",
                                    bitRate = 999_000,
                                    sampleRate = 96_000,
                                    sizeBytes = 24_000_000L
                                )
                            )
                        )
                    ),
                    audioEffectPreset = AudioEffectPreset.WARM,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {},
                    onDismissAudioEffectPage = {
                        showAudioEffectPage = false
                    },
                    onDismissAudioQualitySheet = {
                        showAudioQualitySheet = false
                    },
                    onShowAudioEffectSettings = {
                        showAudioEffectPage = true
                        showAudioQualitySheet = false
                    },
                    onShowAudioQualitySettings = {
                        showAudioQualitySheet = true
                        showAudioEffectPage = false
                    },
                    onSelectAudioEffectPreset = {},
                    onSelectAudioQuality = {}
                )
            }
        }

        composeRule.onNodeWithTag(
            "player_screen_audio_quality_name",
            useUnmergedTree = true
        ).assert(hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("player_screen_audio_quality_sheet").assertIsDisplayed()
        composeRule.onAllNodesWithTag("player_screen_audio_effect_sheet").assertCountEquals(0)

        composeRule.onNodeWithTag(
            "player_screen_audio_effect_name",
            useUnmergedTree = true
        ).assert(hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(showAudioEffectPage)
        assertFalse(showAudioQualitySheet)
        composeRule.onAllNodesWithTag("player_screen_audio_quality_sheet").assertCountEquals(0)
        composeRule.onNodeWithTag("player_screen_audio_effect_sheet").assertIsDisplayed()
    }

    @Test
    fun combinedStatusRow_shouldShowPreferredQualityWhilePreparingQualitySwitch() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "周杰伦 - 夜曲.mp3",
                    status = "切换音质中",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = true,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    preferredAudioQuality = PlaybackAudioQuality.LOSSLESS,
                    appliedAudioQuality = null,
                    audioQualityCatalogUiState = PlayerAudioQualityCatalogUiState.Content(
                        SongAudioQualityCatalog(
                            songId = "347230",
                            options = listOf(
                                SongAudioQualityOption(
                                    quality = PlaybackAudioQuality.LOSSLESS,
                                    rawKey = "sq",
                                    bitRate = 810_381,
                                    sampleRate = 44_100,
                                    sizeBytes = 25_048_891L
                                ),
                                SongAudioQualityOption(
                                    quality = PlaybackAudioQuality.EXHIGH,
                                    rawKey = "h",
                                    bitRate = 320_000,
                                    sampleRate = 44_100,
                                    sizeBytes = 8_798_445L
                                )
                            )
                        )
                    ),
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_audio_quality_name").assertTextEquals("无损...")
    }

    @Test
    fun audioQualitySheet_shouldDescribeRequestedAndAppliedQualitiesSeparatelyWhenFallbackApplied() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "周杰伦 - 夜曲.mp3",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    showAudioQualitySheet = true,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    preferredAudioQuality = PlaybackAudioQuality.HIRES,
                    appliedAudioQuality = PlaybackAudioQuality.LOSSLESS,
                    audioQualityCatalogUiState = PlayerAudioQualityCatalogUiState.Content(
                        SongAudioQualityCatalog(
                            songId = "347230",
                            options = listOf(
                                SongAudioQualityOption(
                                    quality = PlaybackAudioQuality.HIRES,
                                    rawKey = "hr",
                                    bitRate = 999_000,
                                    sampleRate = 96_000,
                                    sizeBytes = 24_000_000L
                                ),
                                SongAudioQualityOption(
                                    quality = PlaybackAudioQuality.LOSSLESS,
                                    rawKey = "sq",
                                    bitRate = 810_381,
                                    sampleRate = 44_100,
                                    sizeBytes = 25_048_891L
                                ),
                                SongAudioQualityOption(
                                    quality = PlaybackAudioQuality.EXHIGH,
                                    rawKey = "h",
                                    bitRate = 320_000,
                                    sampleRate = 44_100,
                                    sizeBytes = 8_798_445L
                                )
                            )
                        )
                    ),
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {},
                    onDismissAudioQualitySheet = {},
                    onSelectAudioQuality = {}
                )
            }
        }

        composeRule.onAllNodesWithText("已选择", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("当前实际使用", substring = true).assertCountEquals(1)
    }

    @Test
    fun activePlayback_shouldUseSquareCoverCardWithoutOverlayCopy() {
        
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "倒数",
                    artistText = "G.E.M. 邓紫棋",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 229_333L,
                    totalDurationText = "03:49",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_cover_card").assertIsDisplayed()
        composeRule.onAllNodesWithText("酷狗音乐").assertCountEquals(0)
        composeRule.onAllNodesWithText("星耀计划").assertCountEquals(0)
        val coverBounds = composeRule
            .onNodeWithTag("player_screen_cover_card")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "Expected square cover card, but was $coverBounds",
            kotlin.math.abs(coverBounds.width - coverBounds.height) < 4f
        )
    }

    @Test
    fun activePlayback_withCover_shouldRenderMixedBackdropLayers() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "无人知晓",
                    artistText = "田馥甄",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 2_000L,
                    currentDurationText = "00:02",
                    durationMs = 288_000L,
                    totalDurationText = "04:48",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_backdrop_base").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_backdrop_cover_blur").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_backdrop_scrim").assertIsDisplayed()
        val visualSectionBounds = composeRule
            .onNodeWithTag("player_screen_visual_section")
            .fetchSemanticsNode()
            .boundsInRoot
        val coverCardBounds = composeRule
            .onNodeWithTag("player_screen_cover_card")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "Expected cover artwork to fill the visual section without a padded gray frame, but visual=$visualSectionBounds cover=$coverCardBounds",
            kotlin.math.abs(visualSectionBounds.width - coverCardBounds.width) < 2f &&
                kotlin.math.abs(visualSectionBounds.height - coverCardBounds.height) < 2f
        )
    }

    @Test
    fun preparingPlayback_shouldShowBufferingIndicatorAndKeepLyricPlaceholder() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "倒数",
                    artistText = "G.E.M. 邓紫棋",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = true,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 18_000L,
                    currentDurationText = "00:18",
                    durationMs = 229_333L,
                    totalDurationText = "03:49",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_buffering_indicator").assertIsDisplayed()
        composeRule.onNodeWithText("缓冲中...", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_lyric_placeholder").assertIsDisplayed()
    }

    @Test
    fun lyricTopTabClick_shouldSwitchToImmersiveLyricsPageWithoutBottomPlaybackChrome() {
        var selectedTopTab by mutableStateOf(PlayerTopTab.SONG)

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    lyricUiState = demoLyricUiState(),
                    selectedTopTab = selectedTopTab,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 2_500L,
                    currentDurationText = "00:02",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onRetryLyrics = {},
                    onSelectTopTab = { selectedTopTab = it },
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_lyric_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_lyric_summary").assertTextEquals("“第一句”")
        val songTabBoundsBefore = composeRule
            .onNodeWithText("歌曲")
            .fetchSemanticsNode()
            .boundsInRoot
        val lyricsTabBoundsBefore = composeRule
            .onNodeWithText("歌词")
            .fetchSemanticsNode()
            .boundsInRoot
        composeRule.onNode(hasText("歌词") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(PlayerTopTab.LYRICS, selectedTopTab)
        }
        val songTabBoundsAfter = composeRule
            .onNodeWithText("歌曲")
            .fetchSemanticsNode()
            .boundsInRoot
        val lyricsTabBoundsAfter = composeRule
            .onNodeWithText("歌词")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "Expected song tab label to stay vertically stable when selection changes, but before=${songTabBoundsBefore.center.y}, after=${songTabBoundsAfter.center.y}",
            kotlin.math.abs(songTabBoundsBefore.center.y - songTabBoundsAfter.center.y) < 2f
        )
        assertTrue(
            "Expected lyrics tab label to stay vertically stable when selection changes, but before=${lyricsTabBoundsBefore.center.y}, after=${lyricsTabBoundsAfter.center.y}",
            kotlin.math.abs(lyricsTabBoundsBefore.center.y - lyricsTabBoundsAfter.center.y) < 2f
        )
        composeRule.onAllNodesWithTag("player_screen_top_tab_indicator_lyrics", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithTag("player_screen_top_tab_indicator_song", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag("player_screen_lyrics_page").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_lyrics_viewport").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_bottom_section").assertIsNotDisplayed()
        composeRule.onNodeWithTag("player_screen_info_section").assertIsNotDisplayed()
        composeRule.onNodeWithTag("player_screen_progress_section").assertIsNotDisplayed()
        composeRule.onNodeWithTag("player_screen_controls_section").assertIsNotDisplayed()
        composeRule.onNodeWithTag("player_screen_title").assertIsNotDisplayed()
        composeRule.onNodeWithTag("player_screen_artist").assertIsNotDisplayed()
        composeRule.onNodeWithTag("player_screen_lyrics_line_active_0").assertIsDisplayed()
    }

    @Test
    fun lyricsPageSwipe_shouldAutoScrollAndReturnToSongPageWithoutBottomPlaybackChrome() {
        var seekValueMs by mutableStateOf(1_500L)
        var selectedTopTab by mutableStateOf(PlayerTopTab.SONG)

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    lyricUiState = demoLongLyricUiState(),
                    selectedTopTab = selectedTopTab,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = seekValueMs,
                    currentDurationText = "00:18",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onRetryLyrics = {},
                    onSelectTopTab = { selectedTopTab = it },
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.runOnIdle {
            seekValueMs = 18_500L
        }
        composeRule.onNodeWithTag("player_screen_lyric_summary").assertTextEquals("“第18句”")
        composeRule.onNodeWithTag("player_screen_content_pager").performTouchInput {
            swipeLeft()
        }
        composeRule.waitUntil(timeoutMillis = 3_000) {
            selectedTopTab == PlayerTopTab.LYRICS
        }
        composeRule.onNodeWithTag("player_screen_lyrics_page").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_lyrics_viewport").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_progress_section").assertIsNotDisplayed()
        composeRule.onNodeWithTag("player_screen_lyrics_line_active_17").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_content_pager").performTouchInput {
            swipeRight()
        }
        composeRule.waitUntil(timeoutMillis = 3_000) {
            selectedTopTab == PlayerTopTab.SONG
        }
        composeRule.onNodeWithTag("player_screen_song_page").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_progress_section").assertIsDisplayed()
    }

    @Test
    fun lyricsPageInitialVisibility_shouldSnapToFarActiveLine() {
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    lyricUiState = demoLongLyricUiState(lineCount = 48),
                    selectedTopTab = PlayerTopTab.LYRICS,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 30_500L,
                    currentDurationText = "00:30",
                    durationMs = 180_000L,
                    totalDurationText = "03:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onRetryLyrics = {},
                    onSelectTopTab = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("player_screen_lyrics_page").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_lyrics_line_active_29").assertIsDisplayed()
    }

    @Test
    fun lyricsPagePlaybackFollow_shouldNotAnimateOnEverySingleLineAdvance() {
        composeRule.mainClock.autoAdvance = false
        var activeLineIndex by mutableStateOf(4)

        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 720.dp)) {
                    PlayerLyricsPage(
                        lyricUiState = demoLongLyricUiState(lineCount = 48),
                        activeLineIndex = activeLineIndex,
                        isVisible = true,
                        onRetryLyrics = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("player_screen_lyrics_list").assert(
            SemanticsMatcher.expectValue(PlayerLyricsFirstVisibleIndexKey, 1)
        )

        composeRule.runOnIdle {
            activeLineIndex = 5
        }
        repeat(20) {
            composeRule.mainClock.advanceTimeByFrame()
        }

        composeRule.onNodeWithTag("player_screen_lyrics_list").assert(
            SemanticsMatcher.expectValue(PlayerLyricsFirstVisibleIndexKey, 1)
        )
    }

    @Test
    fun lyricsPage_shouldUseMoreOpenVerticalSpacingBetweenLines() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 720.dp)) {
                    PlayerLyricsPage(
                        lyricUiState = demoLongLyricUiState(lineCount = 12),
                        activeLineIndex = 0,
                        isVisible = true,
                        onRetryLyrics = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        val firstLineBounds = composeRule
            .onNodeWithTag("player_screen_lyrics_line_active_0")
            .fetchSemanticsNode()
            .boundsInRoot
        val secondLineBounds = composeRule
            .onNodeWithTag("player_screen_lyrics_line_1")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected lyrics lines to breathe more vertically, but got first=$firstLineBounds second=$secondLineBounds",
            secondLineBounds.top - firstLineBounds.top >= 74f
        )
    }

    @Test
    fun lyricsPageVisibleCatchUp_shouldSnapToFarActiveLineWithoutLongRunningAnimation() {
        composeRule.mainClock.autoAdvance = false
        var activeLineIndex by mutableStateOf(0)

        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 720.dp)) {
                    PlayerLyricsPage(
                        lyricUiState = demoLongLyricUiState(lineCount = 48),
                        activeLineIndex = activeLineIndex,
                        isVisible = true,
                        onRetryLyrics = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeRule.mainClock.advanceTimeByFrame()

        composeRule.runOnIdle {
            activeLineIndex = 29
        }
        var reachedExpectedFirstVisible = false
        repeat(90) {
            composeRule.mainClock.advanceTimeByFrame()
            val firstVisible = composeRule
                .onAllNodesWithTag("player_screen_lyrics_list")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?.config
                ?.getOrNull(PlayerLyricsFirstVisibleIndexKey)
            if (firstVisible == 26) {
                reachedExpectedFirstVisible = true
                return@repeat
            }
        }
        val expectedFirstVisibleMatcher = SemanticsMatcher.expectValue(
            PlayerLyricsFirstVisibleIndexKey,
            26
        )
        assertTrue(
            "Expected lyrics catch-up scroll to reach firstVisible=26 within 90 frames",
            reachedExpectedFirstVisible
        )
        composeRule.onNodeWithTag("player_screen_lyrics_list").assert(expectedFirstVisibleMatcher)
        composeRule.onNodeWithTag("player_screen_lyrics_line_active_29").assertIsDisplayed()
    }

    @Test
    fun lyricError_shouldExposeRetryAction() {
        var retryCount = 0
        var selectedTopTab by mutableStateOf(PlayerTopTab.SONG)

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    lyricUiState = PlayerLyricUiState.Error("歌词加载失败"),
                    selectedTopTab = selectedTopTab,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 2_500L,
                    currentDurationText = "00:02",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onRetryLyrics = { retryCount += 1 },
                    onSelectTopTab = { selectedTopTab = it },
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.runOnIdle {
            selectedTopTab = PlayerTopTab.LYRICS
        }
        composeRule.onNodeWithTag("player_screen_lyrics_page").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_lyrics_retry_button").performClick()
        composeRule.runOnIdle {
            assertEquals(1, retryCount)
        }
    }

    @Test
    fun lyricsPage_shouldMatchSongPageVerticalRhythmWithoutExtraMetadataHeader() {
        var selectedTopTab by mutableStateOf(PlayerTopTab.SONG)

        composeRule.setContent {
            PlayerLiteTheme {
                Box(
                    modifier = Modifier
                        .size(width = 360.dp, height = 760.dp)
                        .testTag("player_screen_root")
                ) {
                    PlayerScreen(
                        fileName = "夜曲",
                        artistText = "周杰伦",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        lyricUiState = demoLongLyricUiState(),
                        selectedTopTab = selectedTopTab,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 18_500L,
                        currentDurationText = "00:18",
                        durationMs = 120_000L,
                        totalDurationText = "02:00",
                        enableEnterMotion = false,
                        modifier = Modifier.testTag("player_screen_root_content"),
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onRetryLyrics = {},
                        onSelectTopTab = { selectedTopTab = it },
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        val rootBounds = composeRule.onNodeWithTag("player_screen_root").fetchSemanticsNode().boundsInRoot
        val songTopAnchorBounds = composeRule
            .onNodeWithTag("player_screen_song_content_top_anchor")
            .fetchSemanticsNode()
            .boundsInRoot
        val controlsAnchorBounds = composeRule
            .onNodeWithTag("player_screen_song_controls_bottom_anchor")
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.runOnIdle {
            selectedTopTab = PlayerTopTab.LYRICS
        }

        composeRule.onNodeWithTag("player_screen_title").assertIsNotDisplayed()
        composeRule.onNodeWithTag("player_screen_artist").assertIsNotDisplayed()

        val lyricsViewportBounds = composeRule
            .onNodeWithTag("player_screen_lyrics_viewport")
            .fetchSemanticsNode()
            .boundsInRoot
        val songBottomInset = rootBounds.bottom - controlsAnchorBounds.bottom
        val lyricsBottomInset = rootBounds.bottom - lyricsViewportBounds.bottom

        assertTrue(
            "Expected lyrics viewport top ${lyricsViewportBounds.top} to align with song anchor top ${songTopAnchorBounds.top}",
            kotlin.math.abs(lyricsViewportBounds.top - songTopAnchorBounds.top) <= 4f
        )
        assertTrue(
            "Expected lyrics viewport bottom inset $lyricsBottomInset to align with song controls inset $songBottomInset",
            kotlin.math.abs(lyricsBottomInset - songBottomInset) <= 4f
        )
    }

    @Test
    fun compactViewport_songPageShouldKeepTopCoverGapAndBottomControlsVisible() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(
                    modifier = Modifier
                        .size(width = 360.dp, height = 760.dp)
                        .testTag("player_screen_root")
                ) {
                    PlayerScreen(
                        fileName = "爱上你",
                        artistText = "S.H.E",
                        status = "Idle",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        selectedTopTab = PlayerTopTab.SONG,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PAUSED,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 0L,
                        currentDurationText = "00:00",
                        durationMs = 240_000L,
                        totalDurationText = "04:00",
                        enableEnterMotion = false,
                        modifier = Modifier.testTag("player_screen_root_content"),
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        val topBarBounds = composeRule
            .onNodeWithTag("player_screen_top_bar")
            .fetchSemanticsNode()
            .boundsInRoot
        val coverBounds = composeRule
            .onNodeWithTag("player_screen_cover_card")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected cover top ${coverBounds.top} to stay below top bar bottom ${topBarBounds.bottom} with visible breathing room in compact viewports",
            coverBounds.top >= topBarBounds.bottom + 8f
        )
        composeRule.onNodeWithTag("player_screen_controls_section").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_playlist_button").assertIsDisplayed()
    }

    @Test
    fun lyricsPage_activeLineHighlightShouldStayWithinModerateSizeDelta() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    lyricUiState = demoLyricUiState(),
                    selectedTopTab = PlayerTopTab.LYRICS,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 3_500L,
                    currentDurationText = "00:03",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onRetryLyrics = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        val activeBounds = composeRule
            .onNodeWithTag("player_screen_lyrics_line_active_1")
            .fetchSemanticsNode()
            .boundsInRoot
        val inactiveBounds = composeRule
            .onNodeWithTag("player_screen_lyrics_line_0")
            .fetchSemanticsNode()
            .boundsInRoot
        val activeHeight = activeBounds.height
        val inactiveHeight = inactiveBounds.height

        assertTrue(
            "Expected active lyric line height $activeHeight to stay close to inactive height $inactiveHeight",
            activeHeight <= inactiveHeight * 1.12f
        )
    }

    @Test
    fun activePlayback_artistLabel_shouldNavigateWhenArtistIdExists() {
        var artistClicks = 0

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    currentArtistId = "6452",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {},
                    onArtistClick = { artistClicks += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_artist").performClick()
        composeRule.runOnIdle {
            assertEquals(1, artistClicks)
        }
    }

    @Test
    fun preparingPlayback_shouldKeepTransportControlsInteractive() {
        var previousClicks = 0
        var nextClicks = 0
        var modeClicks = 0
        var playlistClicks = 0
        val interactivePlaylist = listOf(
            PlaylistItem(
                id = "1",
                uri = "https://example.com/1.mp3",
                displayName = "薛之谦 - 无数.mp3",
                songId = "1",
                coverUrl = "https://example.com/1.jpg"
            ),
            PlaylistItem(
                id = "2",
                uri = "https://example.com/2.mp3",
                displayName = "薛之谦 - 皆可.mp3",
                songId = "2",
                coverUrl = "https://example.com/2.jpg"
            ),
            PlaylistItem(
                id = "3",
                uri = "https://example.com/3.mp3",
                displayName = "薛之谦 - 演员.mp3",
                songId = "3",
                coverUrl = "https://example.com/3.jpg"
            )
        )

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "皆可",
                    artistText = "薛之谦",
                    status = "缓冲中",
                    hasSelection = true,
                    playlistItems = interactivePlaylist,
                    activePlaylistIndex = 1,
                    showPlaylistSheet = false,
                    isPreparing = true,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 54_000L,
                    currentDurationText = "00:54",
                    durationMs = 180_000L,
                    totalDurationText = "03:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = { playlistClicks += 1 },
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = { previousClicks += 1 },
                    onNext = { nextClicks += 1 },
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = { modeClicks += 1 },
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_previous_button").performClick()
        composeRule.onNodeWithTag("player_screen_next_button").performClick()
        composeRule.onNodeWithTag("player_screen_playback_mode_button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("player_screen_playlist_button", useUnmergedTree = true).performClick()

        composeRule.runOnIdle {
            assertEquals(1, previousClicks)
            assertEquals(1, nextClicks)
            assertEquals(1, modeClicks)
            assertEquals(1, playlistClicks)
        }
    }

    @Test
    fun listLoopFirstTrack_shouldKeepSkipButtonsInteractive() {
        var previousClicks = 0
        var nextClicks = 0
        val interactivePlaylist = listOf(
            PlaylistItem(
                id = "1",
                uri = "https://example.com/1.mp3",
                displayName = "第一首",
                songId = "1"
            ),
            PlaylistItem(
                id = "2",
                uri = "https://example.com/2.mp3",
                displayName = "第二首",
                songId = "2"
            )
        )

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "第一首",
                    artistText = "测试歌手",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = interactivePlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 180_000L,
                    totalDurationText = "03:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = { previousClicks += 1 },
                    onNext = { nextClicks += 1 },
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_previous_button").performClick()
        composeRule.onNodeWithTag("player_screen_next_button").performClick()

        composeRule.runOnIdle {
            assertEquals(1, previousClicks)
            assertEquals(1, nextClicks)
        }
    }

    @Test
    fun compactScreen_shouldKeepControlsCardInsideVisibleBounds() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(
                    modifier = Modifier
                        .size(width = 320.dp, height = 560.dp)
                        .testTag("player_screen_root")
                ) {
                    PlayerScreen(
                        fileName = "倒数",
                        artistText = "G.E.M. 邓紫棋",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 18_000L,
                        currentDurationText = "00:18",
                        durationMs = 229_333L,
                        totalDurationText = "03:49",
                        enableEnterMotion = false,
                        modifier = Modifier.testTag("player_screen_root_content"),
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        assertVerticalOrder("player_screen_visual_section", "player_screen_progress_section")
        composeRule.onNodeWithTag("player_screen_controls_section").assertIsDisplayed()
        assertVerticalOrder("player_screen_progress_section", "player_screen_controls_section")
        val rootBounds = composeRule.onNodeWithTag("player_screen_root").fetchSemanticsNode().boundsInRoot
        val controlsBounds = composeRule
            .onNodeWithTag("player_screen_controls_section")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected controls card to stay inside compact screen bottom, controls=${controlsBounds.bottom} root=${rootBounds.bottom}",
            controlsBounds.bottom <= rootBounds.bottom
        )
    }

    @Test
    fun narrowScreen_shouldKeepPlaybackControlsSeparatedWithoutHorizontalOverlap() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(
                    modifier = Modifier
                        .size(width = 280.dp, height = 640.dp)
                        .testTag("player_screen_narrow_root")
                ) {
                    PlayerScreen(
                        fileName = "倒数",
                        artistText = "G.E.M. 邓紫棋",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 18_000L,
                        currentDurationText = "00:18",
                        durationMs = 229_333L,
                        totalDurationText = "03:49",
                        enableEnterMotion = false,
                        modifier = Modifier.testTag("player_screen_narrow_root_content"),
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        val modeBounds = composeRule
            .onNodeWithTag("player_screen_playback_mode_button", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val previousBounds = composeRule
            .onNodeWithTag("player_screen_previous_button")
            .fetchSemanticsNode()
            .boundsInRoot
        val nextBounds = composeRule
            .onNodeWithTag("player_screen_next_button")
            .fetchSemanticsNode()
            .boundsInRoot
        val playlistBounds = composeRule
            .onNodeWithTag("player_screen_playlist_button", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected playback mode and previous controls not to overlap, mode=$modeBounds previous=$previousBounds",
            modeBounds.right <= previousBounds.left
        )
        assertTrue(
            "Expected next and playlist controls not to overlap, next=$nextBounds playlist=$playlistBounds",
            nextBounds.right <= playlistBounds.left
        )
    }

    @Test
    fun regularScreen_shouldPlaceControlsLowerInViewport() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(
                    modifier = Modifier
                        .size(width = 360.dp, height = 760.dp)
                        .testTag("player_screen_regular_root")
                ) {
                    PlayerScreen(
                        fileName = "倒数",
                        artistText = "G.E.M. 邓紫棋",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 18_000L,
                        currentDurationText = "00:18",
                        durationMs = 229_333L,
                        totalDurationText = "03:49",
                        enableEnterMotion = false,
                        modifier = Modifier.testTag("player_screen_regular_root_content"),
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        val rootBounds = composeRule
            .onNodeWithTag("player_screen_regular_root")
            .fetchSemanticsNode()
            .boundsInRoot
        val controlsBounds = composeRule
            .onNodeWithTag("player_screen_controls_section")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected controls section to sit in lower viewport area, controlsTop=${controlsBounds.top}, rootBottom=${rootBounds.bottom}",
            controlsBounds.top >= rootBounds.bottom * 0.73f
        )
    }

    @Test
    fun regularScreen_shouldUseLargerSideTransportButtons() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(
                    modifier = Modifier
                        .size(width = 360.dp, height = 760.dp)
                        .testTag("player_screen_button_size_root")
                ) {
                    PlayerScreen(
                        fileName = "倒数",
                        artistText = "G.E.M. 邓紫棋",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 18_000L,
                        currentDurationText = "00:18",
                        durationMs = 229_333L,
                        totalDurationText = "03:49",
                        enableEnterMotion = false,
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        val previousBounds = composeRule
            .onNodeWithTag("player_screen_previous_button")
            .fetchSemanticsNode()
            .boundsInRoot
        val nextBounds = composeRule
            .onNodeWithTag("player_screen_next_button")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected previous button to keep a clear touch target after the responsive shrink, but width was ${previousBounds.width}",
            previousBounds.width >= 48f
        )
        assertTrue(
            "Expected next button to keep a clear touch target after the responsive shrink, but width was ${nextBounds.width}",
            nextBounds.width >= 48f
        )
    }

    @Test
    fun regularScreen_shouldKeepCenterToggleClearlyLargerThanSideButtons() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(
                    modifier = Modifier
                        .size(width = 360.dp, height = 760.dp)
                        .testTag("player_screen_center_button_root")
                ) {
                    PlayerScreen(
                        fileName = "皆可",
                        artistText = "薛之谦",
                        status = "正在播放",
                        hasSelection = true,
                        playlistItems = demoOnlinePlaylistWithCover,
                        activePlaylistIndex = 0,
                        showPlaylistSheet = false,
                        isPreparing = false,
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                        isSeekSupported = true,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorderPlaylist = true,
                        seekValueMs = 30_000L,
                        currentDurationText = "00:30",
                        durationMs = 180_000L,
                        totalDurationText = "03:00",
                        enableEnterMotion = false,
                        onPickAudio = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onSelectPlaylistItem = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onPlay = {},
                        onPrevious = {},
                        onNext = {},
                        onPause = {},
                        onResume = {},
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSeekValueChange = {},
                        onSeekFinished = {}
                    )
                }
            }
        }

        val previousBounds = composeRule
            .onNodeWithTag("player_screen_previous_button")
            .fetchSemanticsNode()
            .boundsInRoot
        val toggleBounds = composeRule
            .onNodeWithTag("player_screen_toggle_button")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected center toggle button to stay larger than side buttons without overpowering them, but toggle=${toggleBounds.width} previous=${previousBounds.width}",
            toggleBounds.width - previousBounds.width >= 8f
        )
    }

    @Test
    fun playlistSheet_shouldRenderWhenVisibleFlagEnabled() {
        var showPlaylistSheet by mutableStateOf(true)
        var dismissClicks = 0

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "周杰伦 - 夜曲.mp3",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = showPlaylistSheet,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 10_000L,
                    currentDurationText = "00:10",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {
                        dismissClicks += 1
                        showPlaylistSheet = false
                    },
                    onSelectPlaylistItem = {},
                    onClearPlaylist = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithText("接下来播放").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭播放列表").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("接下来播放").assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(1, dismissClicks)
        }
    }

    @Test
    fun playlistSheet_shouldExposeClearAllAction() {
        var clearClicks = 0

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "周杰伦 - 夜曲.mp3",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = true,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 10_000L,
                    currentDurationText = "00:10",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onClearPlaylist = { clearClicks += 1 },
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("playlist_sheet_clear_all")
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(1, clearClicks)
        }
    }

    @Test
    fun localPlayback_shouldExposeSongDetailAction() {
        var detailClicks = 0

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲.mp3",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoPlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 10_000L,
                    currentDurationText = "00:10",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {},
                    onOpenSongDetail = { detailClicks += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_song_detail_tool_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_song_detail_tool_button").performClick()
        composeRule.runOnIdle {
            assertEquals(1, detailClicks)
        }
    }

    @Test
    fun activePlayback_shouldRenderCoverArtWhenActiveTrackProvidesCoverUrl() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "周杰伦 - 夜曲.mp3",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onAllNodesWithTag("player_screen_cover_art", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("AUDIO").assertCountEquals(0)
    }

    @Test
    fun playlistSheet_shouldRenderTrackArtworkWhenPlaylistItemsProvideCoverUrl() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "周杰伦 - 夜曲.mp3",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = true,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule
            .onNodeWithTag("playlist_sheet_artwork_1973665667", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun playlistSheet_shouldReplaceInteractionHintsWithMetadataDrivenRows() {
        val playlistItems = listOf(
            PlaylistItem(
                id = "track-1",
                uri = "https://example.com/track-1.mp3",
                displayName = "悬日",
                title = "悬日",
                artistText = "田馥甄",
                albumTitle = "无人知晓",
                songId = "track-1",
                coverUrl = "https://example.com/track-1.jpg"
            ),
            PlaylistItem(
                id = "track-2",
                uri = "https://example.com/track-2.mp3",
                displayName = "皆可",
                title = "皆可",
                artistText = "薛之谦",
                albumTitle = "天外来物",
                songId = "track-2"
            )
        )

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "悬日",
                    artistText = "田馥甄",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = playlistItems,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = true,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("playlist_sheet_surface").assertIsDisplayed()
        composeRule.onAllNodesWithText("点击切换").assertCountEquals(0)
        composeRule.onAllNodesWithText("当前激活").assertCountEquals(0)
        composeRule.onNodeWithTag("playlist_sheet_drag_handle_track-1", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun controls_shouldKeepPlaylistModeSeekAndPlaybackCallbacksWorking() {
        var playbackState by mutableStateOf(AUDIO_TRACK_PLAYSTATE_PLAYING)
        var seekValueMs by mutableStateOf(12_000L)
        val seekChanges = mutableListOf<Long>()

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "周杰伦 - 夜曲.mp3",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = playbackState,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = seekValueMs,
                    currentDurationText = "00:12",
                    durationMs = 120_000L,
                    totalDurationText = "02:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {
                        playbackState = AUDIO_TRACK_PLAYSTATE_PAUSED
                    },
                    onResume = {
                        playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING
                    },
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = { value ->
                        seekValueMs = value
                        seekChanges += value
                    },
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue("Expected slider progress action to succeed", setProgress(90_000f))
            }

        composeRule.runOnIdle {
            assertFalse("Expected at least one seek value callback", seekChanges.isEmpty())
            assertTrue(
                "Expected seek value around 90 seconds but was $seekValueMs",
                seekValueMs in 89_000L..91_000L
            )
        }

        composeRule.onNodeWithTag("player_screen_toggle_button").performClick()
        composeRule.onNodeWithContentDescription("播放").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_toggle_button").performClick()
        composeRule.onNodeWithContentDescription("暂停").assertIsDisplayed()
    }

    @Test
    fun playbackProgress_shouldRenderCustomProgressBarStyleTrack() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "皆可",
                    artistText = "薛之谦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 45_000L,
                    currentDurationText = "00:45",
                    durationMs = 180_000L,
                    totalDurationText = "03:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_slider_track").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_slider_active_track").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_slider_thumb").assertIsDisplayed()
    }

    @Test
    fun activePlayback_shouldRenderCacheTrackOnSharedProgressBar() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 45_000L,
                    cacheProgress = PlaybackCacheProgressSnapshot(
                        cachedBytes = 7_000_000L,
                        totalBytes = 10_000_000L,
                        displayRatio = 0.7f,
                        isFullyCached = false,
                        isEstimated = false
                    ).displayRatio,
                    currentDurationText = "00:45",
                    durationMs = 180_000L,
                    totalDurationText = "03:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_slider_cached_track").assertIsDisplayed()
    }

    @Test
    fun activePlayback_fullCache_shouldStretchCachedTrackToFullSliderWidth() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 45_000L,
                    cacheProgress = 1f,
                    currentDurationText = "00:45",
                    durationMs = 180_000L,
                    totalDurationText = "03:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        val sliderTrackWidth = composeRule
            .onNodeWithTag("player_screen_slider_track")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        val cachedTrackWidth = composeRule
            .onNodeWithTag("player_screen_slider_cached_track")
            .fetchSemanticsNode()
            .boundsInRoot
            .width

        assertTrue(
            "Expected full cache track to span the whole slider, but got cached=$cachedTrackWidth track=$sliderTrackWidth",
            abs(cachedTrackWidth - sliderTrackWidth) < 2f
        )
    }

    @Test
    fun activePlayback_cacheProgressEqualToProgress_shouldKeepCachedTrackVisible() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 45_000L,
                    cacheProgress = 0.25f,
                    currentDurationText = "00:45",
                    durationMs = 180_000L,
                    totalDurationText = "03:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        val activeTrackWidth = composeRule
            .onNodeWithTag("player_screen_slider_active_track")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        val cachedTrackWidth = composeRule
            .onNodeWithTag("player_screen_slider_cached_track")
            .fetchSemanticsNode()
            .boundsInRoot
            .width

        assertTrue(
            "Expected cached track to remain present when cacheProgress equals progress, but got cached=$cachedTrackWidth active=$activeTrackWidth",
            abs(cachedTrackWidth - activeTrackWidth) < 2f
        )
    }

    @Test
    fun activePlayback_cacheProgressBelowProgress_shouldNotRaiseCachedTrackToPlaybackProgress() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylistWithCover,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 90_000L,
                    cacheProgress = 0.25f,
                    currentDurationText = "01:30",
                    durationMs = 180_000L,
                    totalDurationText = "03:00",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {}
                )
            }
        }

        val activeTrackWidth = composeRule
            .onNodeWithTag("player_screen_slider_active_track")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        val cachedTrackWidth = composeRule
            .onNodeWithTag("player_screen_slider_cached_track")
            .fetchSemanticsNode()
            .boundsInRoot
            .width

        assertTrue(
            "Expected cached track to keep its own cache ratio below playback progress, but got cached=$cachedTrackWidth active=$activeTrackWidth",
            cachedTrackWidth < activeTrackWidth
        )
    }

    private fun assertVerticalOrder(upperTag: String, lowerTag: String) {
        val upperBounds = composeRule.onNodeWithTag(upperTag).fetchSemanticsNode().boundsInRoot
        val lowerBounds = composeRule.onNodeWithTag(lowerTag).fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Expected $upperTag to appear above $lowerTag but got ${upperBounds.top} >= ${lowerBounds.top}",
            upperBounds.top < lowerBounds.top
        )
    }

    private companion object {
        val demoPlaylist = listOf(
            PlaylistItem(id = "1", uri = "file:///night.mp3", displayName = "夜曲"),
            PlaylistItem(id = "2", uri = "file:///simple-love.mp3", displayName = "简单爱")
        )

        val demoOnlinePlaylist = listOf(
            PlaylistItem(
                id = "1973665667",
                uri = "https://example.com/night.mp3",
                displayName = "周杰伦 - 夜曲.mp3",
                songId = "1973665667"
            )
        )

        val demoOnlinePlaylistWithCover = listOf(
            PlaylistItem(
                id = "1973665667",
                uri = "https://example.com/night.mp3",
                displayName = "周杰伦 - 夜曲.mp3",
                songId = "1973665667",
                coverUrl = "https://example.com/night.jpg"
            )
        )
    }
}

private fun demoLyricUiState(songId: String = "1973665667"): PlayerLyricUiState.Content {
    return PlayerLyricUiState.Content(
        lyrics = ParsedLyrics(
            songId = songId,
            lines = listOf(
                LyricLine(timestampMs = 1_000L, text = "第一句"),
                LyricLine(timestampMs = 3_000L, text = "第二句")
            ),
            rawText = "[00:01.00]第一句\n[00:03.00]第二句"
        )
    )
}

private fun demoLongLyricUiState(
    songId: String = "1973665667",
    lineCount: Int = 24
): PlayerLyricUiState.Content {
    val lines = (1..lineCount).map { index ->
        LyricLine(
            timestampMs = index * 1_000L,
            text = "第${index}句"
        )
    }
    return PlayerLyricUiState.Content(
        lyrics = ParsedLyrics(
            songId = songId,
            lines = lines,
            rawText = lines.joinToString(separator = "\n") { line ->
                "[00:${line.timestampMs / 1_000L}.${"00"}]${line.text}"
            }
        )
    )
}
