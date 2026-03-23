package com.wxy.playerlite.feature.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackDetailChromeRobolectricTest {
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
                PlaybackDetailChrome(
                    playerState = playerState,
                    bottomPadding = 12.dp,
                    onOpenPlayer = { openPlayerCount += 1 },
                    onTogglePlayback = {},
                    onOpenPlaylist = {
                        openPlaylistCount += 1
                        playerState = playerState.copy(showPlaylistSheet = true)
                    },
                    onDismissPlaylist = {
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

        composeRule.onNodeWithTag("detail_mini_player_playlist_button").performClick()
        composeRule.onNodeWithTag("playlist_sheet_surface").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, openPlayerCount)
            assertEquals(1, openPlaylistCount)
        }
    }
}
