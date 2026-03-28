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
    fun detailMiniPlayerBar_shouldUseFullHeightArtworkAlignedToLeftEdge() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.fillMaxSize().testTag("detail_mini_player_root")) {
                    DetailMiniPlayerHost(bottomPadding = 0.dp) { hostModifier ->
                        DetailMiniPlayerBar(
                            playerState = PlayerUiState(
                                hasSelection = true,
                                currentTrackTitle = "尘大师 Lightly",
                                currentTrackArtist = "陈奕迅",
                                currentCoverUrl = "https://example.com/lightly.jpg",
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

        val barBounds = composeRule
            .onNodeWithTag("detail_mini_player_bar", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val artworkBounds = composeRule
            .onNodeWithTag("detail_mini_player_artwork", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val songAreaBounds = composeRule
            .onNodeWithTag("detail_mini_player_song_area", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val playlistBounds = composeRule
            .onNodeWithTag("detail_mini_player_playlist_button")
            .fetchSemanticsNode()
            .boundsInRoot

        val artworkWidthDp = with(composeRule.density) { artworkBounds.width.toDp() }
        val artworkHeightDp = with(composeRule.density) { artworkBounds.height.toDp() }
        val artworkLeftInsetDp = with(composeRule.density) { (artworkBounds.left - barBounds.left).toDp() }
        val songAreaLeftInsetDp = with(composeRule.density) { (songAreaBounds.left - barBounds.left).toDp() }
        val playlistRightInsetDp = with(composeRule.density) { (barBounds.right - playlistBounds.right).toDp() }
        val artworkHeightDeltaPx = kotlin.math.abs(artworkBounds.height - barBounds.height)
        val artworkHeightTolerancePx = with(composeRule.density) { 2.dp.toPx() }

        assertTrue(
            "Expected detail minibar artwork to keep a 7dp inset inside the bar instead of filling the whole height, but was $artworkWidthDp x $artworkHeightDp",
            artworkWidthDp in 42.dp..48.dp && artworkHeightDp in 42.dp..48.dp
        )
        assertTrue(
            "Expected detail minibar artwork to keep a compact 7dp left inset, but inset was $artworkLeftInsetDp",
            artworkLeftInsetDp in 6.dp..8.dp
        )
        assertTrue(
            "Expected detail song text block to start after the inset artwork block, but inset was $songAreaLeftInsetDp",
            songAreaLeftInsetDp in 68.dp..72.dp
        )
        assertTrue(
            "Expected detail playlist button to keep compact right breathing room, but inset was $playlistRightInsetDp",
            playlistRightInsetDp in 10.dp..14.dp
        )
        assertTrue(
            "Expected detail artwork to leave visibly larger top and bottom breathing room inside the minibar, delta=$artworkHeightDeltaPx",
            artworkHeightDeltaPx in with(composeRule.density) { 12.dp.toPx() }..with(composeRule.density) { 16.dp.toPx() }
        )
        assertTrue(
            "Expected detail song text block to stay to the right of artwork, artwork=$artworkBounds song=$songAreaBounds",
            songAreaBounds.left > artworkBounds.right
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
