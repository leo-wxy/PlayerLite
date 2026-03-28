package com.wxy.playerlite.feature.webplaylistimport

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
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
                    onOpenLogin = {},
                    onConfirmImport = {}
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
                    onOpenLogin = {},
                    onConfirmImport = {}
                )
            }
        }

        composeRule.onNodeWithTag("web_playlist_import_login_required").assertIsDisplayed()
        composeRule.onNodeWithTag("web_playlist_import_login_button").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun previewStage_shouldShowPlaylistSummary() {
        var importClicks = 0
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
                                coverUrl = "https://example.com/cover.jpg",
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
                    onOpenLogin = {},
                    onConfirmImport = { importClicks += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("web_playlist_import_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("web_playlist_import_preview_title").assertIsDisplayed()
        composeRule.onNodeWithTag("web_playlist_import_preview_cover").assertIsDisplayed()
        composeRule.onNodeWithTag("web_playlist_import_progress").assertIsDisplayed()
        composeRule.onNodeWithTag("web_playlist_import_summary_total").assertIsDisplayed()
        composeRule.onNodeWithTag("web_playlist_import_preview")
            .performScrollToNode(hasTestTag("web_playlist_import_summary_direct"))
        composeRule.onNodeWithTag("web_playlist_import_summary_direct").assertIsDisplayed()
        composeRule.onNodeWithTag("web_playlist_import_preview")
            .performScrollToNode(hasTestTag("web_playlist_import_confirm"))
        composeRule.onNodeWithTag("web_playlist_import_confirm")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()
        composeRule.onNodeWithTag("web_playlist_import_confirm").performClick()
        assertEquals(1, importClicks)
    }

    @Test
    fun previewStage_withoutImportableTracks_shouldDisableConfirmAndShowReason() {
        composeRule.setContent {
            PlayerLiteTheme {
                WebPlaylistImportScreen(
                    state = WebPlaylistImportUiState(
                        stage = WebPlaylistImportStage.Preview(
                            snapshot = ImportedPlaylistSnapshot(
                                source = ImportedPlaylistSource.QQ_MUSIC,
                                playlistId = "4204621746",
                                sourceUrl = "https://y.qq.com/n/ryqq_v2/playlist/4204621746",
                                title = "测试歌单",
                                creatorName = "Codex",
                                description = "desc",
                                coverUrl = null,
                                tracks = listOf(
                                    ImportedPlaylistTrack(
                                        sourceTrackId = "1000",
                                        title = "未匹配歌曲",
                                        artistNames = listOf("未知歌手"),
                                        albumTitle = "测试专辑",
                                        durationMs = 180_000L,
                                        resolution = ImportedTrackResolution.Unmatched
                                    )
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onUrlChanged = {},
                    onSubmit = {},
                    onOpenLogin = {},
                    onConfirmImport = {}
                )
            }
        }

        composeRule.onNodeWithTag("web_playlist_import_preview")
            .performScrollToNode(hasTestTag("web_playlist_import_confirm"))
        composeRule.onNodeWithTag("web_playlist_import_confirm")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("web_playlist_import_preview")
            .performScrollToNode(hasTestTag("web_playlist_import_confirm_disabled_reason"))
        composeRule.onNodeWithTag("web_playlist_import_confirm_disabled_reason").assertIsDisplayed()
    }

    @Test
    fun previewStage_whenMatchingPaused_shouldShowPausedNoticeAndPendingLabel() {
        composeRule.setContent {
            PlayerLiteTheme {
                WebPlaylistImportScreen(
                    state = WebPlaylistImportUiState(
                        stage = WebPlaylistImportStage.Preview(
                            snapshot = ImportedPlaylistSnapshot(
                                source = ImportedPlaylistSource.QQ_MUSIC,
                                playlistId = "4204621746",
                                sourceUrl = "https://y.qq.com/n/ryqq_v2/playlist/4204621746",
                                title = "测试歌单",
                                creatorName = "Codex",
                                description = "desc",
                                coverUrl = null,
                                tracks = listOf(
                                    ImportedPlaylistTrack(
                                        sourceTrackId = "1000",
                                        title = "待匹配歌曲",
                                        artistNames = listOf("未知歌手"),
                                        albumTitle = "测试专辑",
                                        durationMs = 180_000L,
                                        resolution = ImportedTrackResolution.Pending
                                    )
                                ),
                                matchingProgress = ImportedPlaylistMatchingProgress(
                                    completedCount = 0,
                                    totalCount = 1,
                                    isPaused = true,
                                    pauseMessage = "匹配请求过于频繁，已暂停后续匹配"
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onUrlChanged = {},
                    onSubmit = {},
                    onOpenLogin = {},
                    onConfirmImport = {}
                )
            }
        }

        composeRule.onNodeWithTag("web_playlist_import_preview")
            .performScrollToNode(hasTestTag("web_playlist_import_paused_notice"))
        composeRule.onNodeWithTag("web_playlist_import_paused_notice").assertIsDisplayed()
        composeRule.onNodeWithTag("web_playlist_import_preview")
            .performScrollToNode(hasText("待匹配"))
        composeRule.onNodeWithText("待匹配").assertIsDisplayed()
    }
}
