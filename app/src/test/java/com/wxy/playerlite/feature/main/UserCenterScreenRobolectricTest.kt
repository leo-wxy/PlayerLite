package com.wxy.playerlite.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.user.model.UserSessionUiState
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserCenterScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loggedInState_shouldShowQuickEntriesAndOwnPlaylistsOnly() {
        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(
                        isLoggedIn = true,
                        title = "Wucy",
                        summary = "在线账户 · Lv.10",
                        avatarUrl = "https://example.com/avatar.jpg"
                    ),
                    contentState = UserCenterUiState(
                        likedPlaylistId = "liked-playlist",
                        playlistsState = UserCenterPlaylistsState.Content(
                            items = listOf(
                                UserCenterCollectionItemUiModel(
                                    id = "playlist-1",
                                    title = "我的歌单",
                                    subtitle = "Wucy",
                                    imageUrl = null
                                )
                            )
                        )
                    ),
                    onRetryPlaylists = {},
                    onContentClick = {},
                    onOpenLikedSongs = {},
                    onOpenRecentSongs = {},
                    onOpenLocalSongs = {},
                    onOpenPlaylistImport = {},
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("user_center_profile_header").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_quick_entries").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_quick_entry_liked").assertHasClickAction()
        composeRule.onNodeWithTag("user_center_quick_entry_recent").assertHasClickAction()
        composeRule.onNodeWithTag("user_center_quick_entry_local").assertHasClickAction()
        composeRule.onNodeWithTag("user_center_quick_entry_import").assertHasClickAction()
        composeRule.onAllNodesWithTag("user_center_tabs").assertCountEquals(0)
        composeRule.onNodeWithTag("user_center_playlists_section_header").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_scroll_content").performScrollToNode(
            matcher = hasTestTag("user_center_content_item_playlist-1")
        )
        composeRule.onNodeWithTag("user_center_content_item_playlist-1").assertHasClickAction()
    }

    @Test
    fun loggedOutState_shouldKeepQuickEntriesVisibleAndShowLoginAction() {
        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(),
                    contentState = UserCenterUiState(),
                    onRetryPlaylists = {},
                    onContentClick = {},
                    onOpenLikedSongs = {},
                    onOpenRecentSongs = {},
                    onOpenLocalSongs = {},
                    onOpenPlaylistImport = {},
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("user_center_quick_entries").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_quick_entry_local").assertHasClickAction()
        composeRule.onNodeWithTag("user_center_quick_entry_import").assertHasClickAction()
        composeRule.onNodeWithTag("user_center_scroll_content").performScrollToNode(
            matcher = hasTestTag("user_center_primary_action")
        )
        composeRule.onNodeWithTag("user_center_primary_action").assertIsDisplayed()
        composeRule.onAllNodesWithTag("user_center_content_item_playlist-1").assertCountEquals(0)
    }

    @Test
    fun quickEntryCallbacks_shouldDispatch() {
        var likedClicks = 0
        var recentClicks = 0
        var localClicks = 0
        var importClicks = 0

        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(isLoggedIn = true),
                    contentState = UserCenterUiState(likedPlaylistId = "liked-id"),
                    onRetryPlaylists = {},
                    onContentClick = {},
                    onOpenLikedSongs = { likedClicks += 1 },
                    onOpenRecentSongs = { recentClicks += 1 },
                    onOpenLocalSongs = { localClicks += 1 },
                    onOpenPlaylistImport = { importClicks += 1 },
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("user_center_quick_entry_liked").performClick()
        composeRule.onNodeWithTag("user_center_quick_entry_recent").performClick()
        composeRule.onNodeWithTag("user_center_quick_entry_local").performClick()
        composeRule.onNodeWithTag("user_center_quick_entry_import").performClick()

        composeRule.runOnIdle {
            assertEquals(1, likedClicks)
            assertEquals(1, recentClicks)
            assertEquals(1, localClicks)
            assertEquals(1, importClicks)
        }
    }

    @Test
    fun errorState_shouldShowRetryEntryForPlaylists() {
        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(
                        isLoggedIn = true,
                        title = "Wucy",
                        summary = "在线账户 · Lv.10"
                    ),
                    contentState = UserCenterUiState(
                        playlistsState = UserCenterPlaylistsState.Error("歌单加载失败")
                    ),
                    onRetryPlaylists = {},
                    onContentClick = {},
                    onOpenLikedSongs = {},
                    onOpenRecentSongs = {},
                    onOpenLocalSongs = {},
                    onOpenPlaylistImport = {},
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("user_center_scroll_content").performScrollToNode(
            matcher = hasTestTag("user_center_playlists_retry")
        )
        composeRule.onNodeWithTag("user_center_playlists_retry").assertIsDisplayed()
    }

    @Test
    fun userCenterScreen_withGlobalMiniPlayerOverlay_shouldKeepMiniPlayerVisible() {
        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    UserCenterScreen(
                        userState = UserSessionUiState(
                            isLoggedIn = true,
                            title = "Wucy",
                            summary = "在线账户 · Lv.10"
                        ),
                        contentState = UserCenterUiState(),
                        onRetryPlaylists = {},
                        onContentClick = {},
                        onOpenLikedSongs = {},
                        onOpenRecentSongs = {},
                        onOpenLocalSongs = {},
                        onOpenPlaylistImport = {},
                        onLoginClick = {},
                        onLogoutClick = {},
                        bottomContentPadding = HomeChromeLayoutSpec.homeOverviewScrollBottomPadding
                    )
                    MainShellMiniPlayerOverlay(
                        playerState = PlayerUiState(
                            hasSelection = true,
                            selectedFileName = "陈奕迅 - 尘大师 Lightly.mp3",
                            statusText = "正在播放"
                        ),
                        onOpenPlayer = {},
                        onTogglePlayback = {},
                        onOpenPlaylist = {},
                        onSkipPrevious = {},
                        onSkipNext = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("user_center_profile_header").assertIsDisplayed()
        composeRule.onNodeWithTag("home_play_entry_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_mini_player_bar").assertIsDisplayed()
    }
}
