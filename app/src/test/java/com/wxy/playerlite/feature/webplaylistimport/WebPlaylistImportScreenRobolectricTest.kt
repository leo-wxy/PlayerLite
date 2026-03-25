package com.wxy.playerlite.feature.webplaylistimport

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebPlaylistImportScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inputStage_shouldShowUrlFieldAndSubmitAction() {
        composeRule.setContent {
            PlayerLiteTheme {
                WebPlaylistImportScreen(
                    state = WebPlaylistImportUiState(),
                    onBack = {},
                    onUrlChanged = {},
                    onSubmit = {},
                    onOpenLogin = {}
                )
            }
        }

        composeRule.onNodeWithTag("web_playlist_import_url_field").assertIsDisplayed()
        composeRule.onNodeWithTag("web_playlist_import_submit").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun loginRequiredStage_shouldShowLoginAction() {
        composeRule.setContent {
            PlayerLiteTheme {
                WebPlaylistImportScreen(
                    state = WebPlaylistImportUiState(
                        inputUrl = "https://music.163.com/#/playlist?id=17729789137",
                        stage = WebPlaylistImportStage.LoginRequired
                    ),
                    onBack = {},
                    onUrlChanged = {},
                    onSubmit = {},
                    onOpenLogin = {}
                )
            }
        }

        composeRule.onNodeWithTag("web_playlist_import_login_required").assertIsDisplayed()
        composeRule.onNodeWithTag("web_playlist_import_login_button").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun previewStage_shouldShowPlaylistSummary() {
        composeRule.setContent {
            PlayerLiteTheme {
                WebPlaylistImportScreen(
                    state = WebPlaylistImportUiState(
                        stage = WebPlaylistImportStage.Preview(
                            snapshot = ImportedPlaylistSnapshot(
                                source = ImportedPlaylistSource.NETEASE,
                                playlistId = "17729789137",
                                sourceUrl = "https://music.163.com/#/playlist?id=17729789137",
                                title = "深夜 R&B",
                                creatorName = "Buradarrr",
                                description = "夜间歌单",
                                coverUrl = null,
                                tracks = listOf(
                                    ImportedPlaylistTrack(
                                        sourceTrackId = "1000",
                                        title = "Song 0",
                                        artistNames = listOf("Artist 0"),
                                        albumTitle = "Album 0",
                                        durationMs = 180_000L,
                                        resolution = ImportedTrackResolution.Direct(
                                            song = ResolvedImportedSong(
                                                songId = "1000",
                                                title = "Song 0",
                                                artistText = "Artist 0",
                                                albumTitle = "Album 0",
                                                durationMs = 180_000L
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onUrlChanged = {},
                    onSubmit = {},
                    onOpenLogin = {}
                )
            }
        }

        composeRule.onNodeWithTag("web_playlist_import_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("web_playlist_import_preview_title").assertIsDisplayed()
        composeRule.onNodeWithTag("web_playlist_import_summary_total").assertIsDisplayed()
        composeRule.onNodeWithTag("web_playlist_import_summary_direct").assertIsDisplayed()
    }
}
