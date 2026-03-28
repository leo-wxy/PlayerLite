package com.wxy.playerlite.feature.player.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedMiniPlayerBarRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sharedMiniPlayerBar_bodyClickMode_shouldScopeTagsAndCallbacks() {
        var openPlayerCount = 0
        var togglePlaybackCount = 0
        var openPlaylistCount = 0

        composeRule.setContent {
            PlayerLiteTheme {
                SharedMiniPlayerBar(
                    state = SharedMiniPlayerBarState(
                        contentLine = "阿司匹林",
                        progress = 0.42f,
                        isPlaying = true,
                        artworkUrl = "https://example.com/cover.jpg"
                    ),
                    testTags = SharedMiniPlayerBarTestTags(
                        cardTag = "shared_body_card",
                        prefix = "shared_body"
                    ),
                    openPlayerClickTarget = SharedMiniPlayerOpenPlayerClickTarget.Body,
                    onOpenPlayer = { openPlayerCount += 1 },
                    onTogglePlayback = { togglePlaybackCount += 1 },
                    onOpenPlaylist = { openPlaylistCount += 1 },
                    onSkipPrevious = {},
                    onSkipNext = {}
                )
            }
        }

        composeRule.onNodeWithTag("shared_body_card").assertIsDisplayed()
        composeRule.onNodeWithTag("shared_body_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("shared_body_body").assertHasClickAction().performClick()
        composeRule.onNodeWithTag("shared_body_play_pause_button").performClick()
        composeRule.onNodeWithTag("shared_body_playlist_button").performClick()

        composeRule.runOnIdle {
            assertEquals(1, openPlayerCount)
            assertEquals(1, togglePlaybackCount)
            assertEquals(1, openPlaylistCount)
        }
    }

    @Test
    fun sharedMiniPlayerBar_cardClickMode_shouldOpenPlayerFromCardRoot() {
        var openPlayerCount = 0

        composeRule.setContent {
            PlayerLiteTheme {
                SharedMiniPlayerBar(
                    state = SharedMiniPlayerBarState(
                        contentLine = "轨道 9",
                        progress = 0.18f,
                        isPlaying = false,
                        artworkUrl = null
                    ),
                    testTags = SharedMiniPlayerBarTestTags(
                        cardTag = "shared_card_card",
                        prefix = "shared_card"
                    ),
                    openPlayerClickTarget = SharedMiniPlayerOpenPlayerClickTarget.Card,
                    onOpenPlayer = { openPlayerCount += 1 },
                    onTogglePlayback = {},
                    onOpenPlaylist = {},
                    onSkipPrevious = {},
                    onSkipNext = {}
                )
            }
        }

        composeRule.onNodeWithTag("shared_card_card").performClick()

        composeRule.runOnIdle {
            assertEquals(1, openPlayerCount)
        }
    }
}
