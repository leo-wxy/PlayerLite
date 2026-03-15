package com.wxy.playerlite.feature.local

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalSongsScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cachedSongsState_shouldRenderScanActionAndPlaybackCallbacks() {
        var scanCount = 0
        var playAllCount = 0
        var playTrackIndex = -1

        composeRule.setContent {
            PlayerLiteTheme {
                LocalSongsScreen(
                    state = LocalSongsUiState(
                        songs = listOf(
                            LocalSongEntry(
                                id = "local-1",
                                contentUri = "content://media/external/audio/media/1",
                                title = "晴天",
                                artist = "周杰伦",
                                album = "叶惠美",
                                durationMs = 269000L
                            )
                        ),
                        hasCachedSongs = true
                    ),
                    onBack = {},
                    onRequestPermission = {},
                    onScan = { scanCount += 1 },
                    onPlayAll = { playAllCount += 1 },
                    onSongClick = { playTrackIndex = it }
                )
            }
        }

        composeRule.onNodeWithTag("local_songs_scan_action").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("local_songs_play_all").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("local_songs_item_local-1").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("晴天").assertIsDisplayed()

        composeRule.onNodeWithTag("local_songs_scan_action").performClick()
        composeRule.onNodeWithTag("local_songs_play_all").performClick()
        composeRule.onNodeWithTag("local_songs_item_local-1").performClick()

        composeRule.runOnIdle {
            assertEquals(1, scanCount)
            assertEquals(1, playAllCount)
            assertEquals(0, playTrackIndex)
        }
    }
}
