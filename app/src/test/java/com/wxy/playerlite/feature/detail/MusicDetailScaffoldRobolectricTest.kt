package com.wxy.playerlite.feature.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.feature.player.LyricLine
import com.wxy.playerlite.feature.player.ParsedLyrics
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.PlayerLyricUiState
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MusicDetailScaffoldRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backButton_shouldRemainFixedAfterListScroll() {
        composeRule.setContent {
            PlayerLiteTheme {
                MusicDetailScaffold(
                    heroTestTag = "detail_test_hero",
                    onBack = {},
                    heroContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .testTag("detail_test_hero_content")
                        )
                    }
                ) {
                    items(count = 40) { index ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .testTag("detail_body_item_$index")
                        )
                    }
                }
            }
        }

        val initialBounds = composeRule
            .onNodeWithTag("detail_back_button")
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("detail_body_item_39"))

        composeRule.onNodeWithTag("detail_back_button").assertIsDisplayed()

        val scrolledBounds = composeRule
            .onNodeWithTag("detail_back_button")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected detail back button top to stay fixed, but moved from ${initialBounds.top} to ${scrolledBounds.top}",
            abs(initialBounds.top - scrolledBounds.top) < 1f
        )
        assertTrue(
            "Expected detail back button left to stay fixed, but moved from ${initialBounds.left} to ${scrolledBounds.left}",
            abs(initialBounds.left - scrolledBounds.left) < 1f
        )
    }

    @Test
    fun bottomOverlayPadding_shouldNotShrinkDetailListViewport() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.fillMaxSize().testTag("detail_scaffold_root")) {
                    MusicDetailScaffold(
                        heroTestTag = "detail_test_hero",
                        onBack = {},
                        bottomOverlayPadding = 96.dp,
                        heroContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .testTag("detail_test_hero_content")
                            )
                        }
                    ) {
                        items(count = 4) { index ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .testTag("detail_body_item_$index")
                            )
                        }
                    }
                }
            }
        }

        val rootBounds = composeRule
            .onNodeWithTag("detail_scaffold_root")
            .fetchSemanticsNode()
            .boundsInRoot
        val listBounds = composeRule
            .onNodeWithTag("detail_scaffold_list")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected detail list viewport to stay full-height when minibar overlays content, root=$rootBounds list=$listBounds",
            abs(rootBounds.bottom - listBounds.bottom) < 1f
        )
    }

    @Test
    fun detailMiniPlayerBar_shouldHonorConfiguredBottomPadding() {
        val expectedBottomPadding = 18.dp

        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.fillMaxSize().testTag("detail_mini_player_root")) {
                    DetailMiniPlayerHost(bottomPadding = expectedBottomPadding) { hostModifier ->
                        DetailMiniPlayerBar(
                            playerState = PlayerUiState(
                                hasSelection = true,
                                currentTrackTitle = "先知",
                                currentTrackArtist = "田馥甄",
                                playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING
                            ),
                            onOpenPlayer = {},
                            onTogglePlayback = {},
                            onOpenPlaylist = {},
                            onSkipPrevious = {},
                            onSkipNext = {},
                            modifier = hostModifier
                        )
                    }
                }
            }
        }

        val rootBounds = composeRule
            .onNodeWithTag("detail_mini_player_root")
            .fetchSemanticsNode()
            .boundsInRoot
        val cardBounds = composeRule
            .onNodeWithTag("detail_mini_player_card")
            .fetchSemanticsNode()
            .boundsInRoot

        val bottomInsetDp = with(composeRule.density) {
            (rootBounds.bottom - cardBounds.bottom).toDp()
        }

        assertTrue(
            "Expected detail mini player bottom inset to match configured padding, expected=$expectedBottomPadding actual=$bottomInsetDp",
            abs((bottomInsetDp - expectedBottomPadding).value) < 1f
        )
        assertTrue(
            "Expected detail mini player card to stay narrower than the root width, root=$rootBounds card=$cardBounds",
            cardBounds.width < rootBounds.width
        )
    }

    @Test
    fun detailMiniPlayerBar_clickCard_shouldDispatchOpenPlayer() {
        var openPlayerCount = 0

        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    DetailMiniPlayerHost(bottomPadding = 0.dp) { hostModifier ->
                        DetailMiniPlayerBar(
                            playerState = PlayerUiState(
                                hasSelection = true,
                                currentTrackTitle = "夜曲",
                                currentTrackArtist = "周杰伦",
                                playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING
                            ),
                            onOpenPlayer = { openPlayerCount += 1 },
                            onTogglePlayback = {},
                            onOpenPlaylist = {},
                            onSkipPrevious = {},
                            onSkipNext = {},
                            modifier = hostModifier
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("detail_mini_player_card").performClick()
        composeRule.runOnIdle {
            assertEquals(1, openPlayerCount)
        }
    }

    @Test
    fun detailMiniPlayerBar_localButtons_shouldDispatchIndependentCallbacks() {
        var openPlayerCount = 0
        var togglePlaybackCount = 0
        var openPlaylistCount = 0

        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    DetailMiniPlayerHost(bottomPadding = 0.dp) { hostModifier ->
                        DetailMiniPlayerBar(
                            playerState = PlayerUiState(
                                hasSelection = true,
                                currentTrackTitle = "夜曲",
                                currentTrackArtist = "周杰伦",
                                playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING
                            ),
                            onOpenPlayer = { openPlayerCount += 1 },
                            onTogglePlayback = { togglePlaybackCount += 1 },
                            onOpenPlaylist = { openPlaylistCount += 1 },
                            onSkipPrevious = {},
                            onSkipNext = {},
                            modifier = hostModifier
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("detail_mini_player_play_pause_button").performClick()
        composeRule.onNodeWithTag("detail_mini_player_playlist_button").performClick()

        composeRule.runOnIdle {
            assertEquals(0, openPlayerCount)
            assertEquals(1, togglePlaybackCount)
            assertEquals(1, openPlaylistCount)
        }
    }

    @Test
    fun detailMiniPlayerBar_shouldPreferCurrentLyricInSingleLine() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    DetailMiniPlayerHost(bottomPadding = 0.dp) { hostModifier ->
                        DetailMiniPlayerBar(
                            playerState = PlayerUiState(
                                hasSelection = true,
                                currentTrackTitle = "夜曲",
                                currentTrackArtist = "周杰伦",
                                lyricUiState = PlayerLyricUiState.Content(
                                    lyrics = ParsedLyrics(
                                        songId = "track-1",
                                        lines = listOf(
                                            LyricLine(timestampMs = 1_000L, text = "第一句"),
                                            LyricLine(timestampMs = 3_000L, text = "第二句")
                                        ),
                                        rawText = "[00:01.00]第一句\n[00:03.00]第二句"
                                    )
                                ),
                                seekPositionMs = 3_500L,
                                playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING
                            ),
                            onOpenPlayer = {},
                            onTogglePlayback = {},
                            onOpenPlaylist = {},
                            onSkipPrevious = {},
                            onSkipNext = {},
                            modifier = hostModifier
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("detail_mini_player_title", useUnmergedTree = true)
            .assertTextEquals("第二句")
    }
}
