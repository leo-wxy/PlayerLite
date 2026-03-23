package com.wxy.playerlite.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainShellMiniPlayerChromeRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playlistButton_shouldOpenLocalPlaylistSheetWithoutOpeningPlayer() {
        var openPlayerCount = 0
        var openPlaylistCount = 0
        var playerState by mutableStateOf(
            PlayerUiState(
                hasSelection = true,
                currentTrackTitle = "夜曲",
                currentTrackArtist = "周杰伦",
                playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                playlistItems = listOf(
                    PlaylistItem(
                        id = "playlist:test:0:track-1",
                        uri = "https://example.com/track-1.mp3",
                        displayName = "夜曲",
                        songId = "track-1",
                        title = "夜曲",
                        artistText = "周杰伦"
                    )
                ),
                activePlaylistIndex = 0,
                canReorderPlaylist = true
            )
        )

        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainShellMiniPlayerChrome(
                        playerState = playerState,
                        onOpenPlayer = { openPlayerCount += 1 },
                        onTogglePlayback = {},
                        onTogglePlaylistSheet = {
                            openPlaylistCount += 1
                            playerState = playerState.copy(showPlaylistSheet = true)
                        },
                        onDismissPlaylistSheet = {
                            playerState = playerState.copy(showPlaylistSheet = false)
                        },
                        onCyclePlaybackMode = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSelectPlaylistItem = {},
                        onClearPlaylist = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onSkipPrevious = {},
                        onSkipNext = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home_mini_player_playlist_button").performClick()
        composeRule.onNodeWithTag("playlist_sheet_surface").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, openPlayerCount)
            assertEquals(1, openPlaylistCount)
        }
    }

    @Test
    fun playlistSheet_shouldCoverBottomBarAreaWhenShown() {
        val playerState = PlayerUiState(
            hasSelection = true,
            currentTrackTitle = "夜曲",
            currentTrackArtist = "周杰伦",
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            playlistItems = listOf(
                PlaylistItem(
                    id = "playlist:test:0:track-1",
                    uri = "https://example.com/track-1.mp3",
                    displayName = "夜曲",
                    songId = "track-1",
                    title = "夜曲",
                    artistText = "周杰伦"
                )
            ),
            activePlaylistIndex = 0,
            canReorderPlaylist = true,
            showPlaylistSheet = true
        )

        composeRule.setContent {
            PlayerLiteTheme {
                MainShellScaffold(
                    selectedTab = MainTab.HOME,
                    onTabSelected = {},
                    playerState = playerState,
                    onOpenPlayer = {},
                    onTogglePlayback = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSelectPlaylistItem = {},
                    onClearPlaylist = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onSkipPrevious = {},
                    onSkipNext = {}
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }

        val sheetBounds = composeRule
            .onNodeWithTag("playlist_sheet_surface")
            .fetchSemanticsNode()
            .boundsInRoot
        val bottomBarBounds = composeRule
            .onNodeWithTag("main_bottom_bar_root")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected playlist sheet to extend over the bottom bar area, sheet=$sheetBounds bar=$bottomBarBounds",
            sheetBounds.bottom > bottomBarBounds.top
        )
    }

    @Test
    fun playlistModeButton_shouldTriggerModeCycleInsideHomePlaylistSheet() {
        var modeToggleCount = 0
        val playerState = PlayerUiState(
            hasSelection = true,
            currentTrackTitle = "夜曲",
            currentTrackArtist = "周杰伦",
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            playlistItems = listOf(
                PlaylistItem(
                    id = "playlist:test:0:track-1",
                    uri = "https://example.com/track-1.mp3",
                    displayName = "夜曲",
                    songId = "track-1",
                    title = "夜曲",
                    artistText = "周杰伦"
                )
            ),
            activePlaylistIndex = 0,
            canReorderPlaylist = true,
            showPlaylistSheet = true,
            playbackMode = PlaybackMode.LIST_LOOP
        )

        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainShellMiniPlayerChrome(
                        playerState = playerState,
                        onOpenPlayer = {},
                        onTogglePlayback = {},
                        onTogglePlaylistSheet = {},
                        onDismissPlaylistSheet = {},
                        onCyclePlaybackMode = { modeToggleCount += 1 },
                        onShowOriginalOrderInShuffleChange = {},
                        onSelectPlaylistItem = {},
                        onClearPlaylist = {},
                        onRemovePlaylistItem = {},
                        onMovePlaylistItem = { _, _ -> },
                        onSkipPrevious = {},
                        onSkipNext = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("playlist_sheet_mode_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(1, modeToggleCount)
        }
    }
}
