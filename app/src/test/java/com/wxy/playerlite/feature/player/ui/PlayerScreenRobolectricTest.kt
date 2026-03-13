package com.wxy.playerlite.feature.player.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.PlayerSongWikiUiState
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
    fun activePlayback_shouldUseSimplifiedThreeSectionLayoutAndHideLegacyToolPanels() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "夜曲",
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

        composeRule.onNodeWithTag("player_screen_visual_section").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_progress_section").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_controls_section").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_playlist_button").assertIsDisplayed()

        composeRule.onAllNodesWithText("Audio Info").assertCountEquals(0)
        composeRule.onAllNodesWithText("PLAYER LITE").assertCountEquals(0)
        composeRule.onAllNodesWithText("Local Audio Deck").assertCountEquals(0)
        composeRule.onAllNodesWithTag("login_entry_button_root").assertCountEquals(0)

        assertVerticalOrder(
            upperTag = "player_screen_title",
            lowerTag = "player_screen_artist"
        )
        assertVerticalOrder(
            upperTag = "player_screen_artist",
            lowerTag = "player_screen_slider"
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
        composeRule.onNodeWithTag("player_screen_song_wiki_button").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_song_wiki_button").performClick()
        composeRule.onNodeWithTag("player_screen_song_wiki_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("正在加载歌曲百科…").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("播放").assertIsDisplayed()
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
    }
}
