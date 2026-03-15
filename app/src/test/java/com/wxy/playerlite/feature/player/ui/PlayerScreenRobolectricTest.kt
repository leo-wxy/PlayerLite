package com.wxy.playerlite.feature.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.wxy.playerlite.feature.player.model.PlayerLyricUiState
import com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState
import com.wxy.playerlite.feature.player.model.PlayerTopTab
import com.wxy.playerlite.feature.player.model.demoSongWikiSummary
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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
        composeRule.onNodeWithTag("player_screen_top_tabs").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_top_tab_song").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_top_tab_lyrics").assertIsDisplayed()
        composeRule.onAllNodesWithTag("player_screen_top_tab_indicator_song", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithTag("player_screen_top_tab_indicator_lyrics", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag("player_screen_top_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_top_share_button").assertIsDisplayed()
        composeRule.onNodeWithText("歌曲").assertIsDisplayed()
        composeRule.onNodeWithText("歌词").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_song_page").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_visual_section").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_cover_card").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_bottom_section").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_info_section").assertIsDisplayed()
        composeRule.onNodeWithText("歌词待补充").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_favorite_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_song_wiki_tool_button").assertIsDisplayed()
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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
        var showSongWikiSheet by mutableStateOf(false)
        var songWikiUiState by mutableStateOf<PlayerSongWikiUiState>(PlayerSongWikiUiState.Placeholder)
        var playbackMode by mutableStateOf(PlaybackMode.LIST_LOOP)
        var playbackState by mutableStateOf(AUDIO_TRACK_PLAYSTATE_PLAYING)

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "周杰伦 - 夜曲.mp3",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = showPlaylistSheet,
                    showSongWikiSheet = showSongWikiSheet,
                    songWikiUiState = songWikiUiState,
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
                    onShowSongWiki = {
                        showSongWikiSheet = true
                        songWikiUiState = PlayerSongWikiUiState.Loading
                    },
                    onDismissSongWiki = {
                        showSongWikiSheet = false
                    },
                    onRetrySongWiki = {
                        songWikiUiState = PlayerSongWikiUiState.Loading
                    },
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
                    onSeekFinished = {}
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_toggle_button").performClick()
        composeRule.onNodeWithTag("player_screen_playback_mode_button", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("player_screen_playlist_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_song_wiki_tool_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_song_wiki_tool_button").performClick()
        composeRule.onNodeWithTag("player_screen_song_wiki_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("正在加载歌曲百科…").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("播放").assertIsDisplayed()
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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
        composeRule.onNode(hasText("歌词") and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(PlayerTopTab.LYRICS, selectedTopTab)
        }
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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
                        showSongWikiSheet = false,
                        songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                        onShowSongWiki = {},
                        onDismissSongWiki = {},
                        onRetrySongWiki = {},
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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
                        showSongWikiSheet = false,
                        songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                        onShowSongWiki = {},
                        onDismissSongWiki = {},
                        onRetrySongWiki = {},
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
                        showSongWikiSheet = false,
                        songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                        onShowSongWiki = {},
                        onDismissSongWiki = {},
                        onRetrySongWiki = {},
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
                        showSongWikiSheet = false,
                        songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                        onShowSongWiki = {},
                        onDismissSongWiki = {},
                        onRetrySongWiki = {},
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
                        showSongWikiSheet = false,
                        songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                        onShowSongWiki = {},
                        onDismissSongWiki = {},
                        onRetrySongWiki = {},
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
                        showSongWikiSheet = false,
                        songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                        onShowSongWiki = {},
                        onDismissSongWiki = {},
                        onRetrySongWiki = {},
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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

        composeRule.onNodeWithText("播放列表").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭播放列表").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("播放列表").assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(1, dismissClicks)
        }
    }

    @Test
    fun localPlayback_shouldNotShowSongWikiButton() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲.mp3",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoPlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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

        composeRule.onAllNodesWithTag("player_screen_song_wiki_button").assertCountEquals(0)
    }

    @Test
    fun songWikiSheet_shouldRenderStructuredSummaryAndRetryState() {
        var retryClicks = 0
        var songWikiUiState by mutableStateOf<PlayerSongWikiUiState>(
            PlayerSongWikiUiState.Content(
                summary = demoSongWikiSummary()
            )
        )

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "周杰伦 - 夜曲.mp3",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    showSongWikiSheet = true,
                    songWikiUiState = songWikiUiState,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {
                        retryClicks += 1
                        songWikiUiState = PlayerSongWikiUiState.Loading
                    },
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

        composeRule.onNodeWithText("音乐百科").assertIsDisplayed()
        composeRule.onNodeWithText("曲风").assertIsDisplayed()
        composeRule.onNodeWithText("流行-华语流行").assertIsDisplayed()
        composeRule.onNodeWithText("推荐标签").assertIsDisplayed()
        composeRule.onNodeWithText("治愈 · 悲伤").assertIsDisplayed()
        composeRule.onNodeWithText("参与共建").assertIsDisplayed()

        composeRule.runOnIdle {
            songWikiUiState = PlayerSongWikiUiState.Error(
                message = "歌曲百科加载失败"
            )
        }

        composeRule.onNodeWithText("歌曲百科加载失败").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_song_wiki_retry").performClick()
        composeRule.onNodeWithText("正在加载歌曲百科…").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue("Expected retry action to be reachable", retryClicks >= 1)
        }
    }

    @Test
    fun songWikiSheet_shouldDismissWhenCloseButtonClicked() {
        var showSongWikiSheet by mutableStateOf(true)
        var dismissClicks = 0

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "周杰伦 - 夜曲.mp3",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = demoOnlinePlaylist,
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    showSongWikiSheet = showSongWikiSheet,
                    songWikiUiState = PlayerSongWikiUiState.Content(
                        summary = demoSongWikiSummary()
                    ),
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {
                        dismissClicks += 1
                        showSongWikiSheet = false
                    },
                    onRetrySongWiki = {},
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

        composeRule.onNodeWithTag("player_screen_song_wiki_sheet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭歌曲百科").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("player_screen_song_wiki_sheet").assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(1, dismissClicks)
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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
                    showSongWikiSheet = false,
                    songWikiUiState = PlayerSongWikiUiState.Placeholder,
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
                    onShowSongWiki = {},
                    onDismissSongWiki = {},
                    onRetrySongWiki = {},
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

private fun demoLongLyricUiState(songId: String = "1973665667"): PlayerLyricUiState.Content {
    val lines = (1..24).map { index ->
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
