package com.wxy.playerlite.feature.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.wxy.playerlite.feature.user.model.UserSessionUiState
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserCenterScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loggedInState_shouldShowTabsAndSelectedContent() {
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
                        selectedTab = UserCenterTab.ARTISTS,
                        artistsState = UserCenterTabContentState.Content(
                            items = listOf(
                                UserCenterCollectionItemUiModel(
                                    id = "artist-1",
                                    title = "yama",
                                    subtitle = "真昼",
                                    imageUrl = null,
                                    meta = "68 张专辑"
                                )
                            )
                        )
                    ),
                    onTabSelected = {},
                    onRetryCurrentTab = {},
                    onContentClick = {},
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("user_center_profile_header").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_scroll_content").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("user_center_content_panel_header").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_content_panel_body").assertIsDisplayed()
        composeRule.onAllNodesWithTag("user_center_info_card").assertCountEquals(0)
        composeRule.onAllNodesWithTag("user_center_content_intro").assertCountEquals(0)
        composeRule.onNodeWithTag("user_center_tabs").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_tab_artists").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_content_item_artist-1").assertHasClickAction()
        composeRule.onNodeWithTag("user_center_scroll_content").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("user_center_secondary_action").assertIsDisplayed()
    }

    @Test
    fun loggedOutState_shouldShowPrimaryActionWithoutTabs() {
        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(),
                    contentState = UserCenterUiState(),
                    onTabSelected = {},
                    onRetryCurrentTab = {},
                    onContentClick = {},
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("user_center_profile_header").assertIsDisplayed()
        composeRule.onAllNodesWithTag("user_center_tabs").assertCountEquals(0)
        composeRule.onNodeWithTag("user_center_scroll_content").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("user_center_primary_action").assertIsDisplayed()
    }

    @Test
    fun localSongsEntry_shouldBeVisibleAndDispatchCallback() {
        var openLocalSongsCount = 0

        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(
                        isLoggedIn = true,
                        title = "Wucy",
                        summary = "在线账户 · Lv.10"
                    ),
                    contentState = UserCenterUiState(),
                    onTabSelected = {},
                    onRetryCurrentTab = {},
                    onContentClick = {},
                    onOpenLocalSongs = { openLocalSongsCount += 1 },
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("user_center_local_songs_entry").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("user_center_local_songs_entry").performClick()
        composeRule.runOnIdle {
            assertEquals(1, openLocalSongsCount)
        }
    }

    @Test
    fun errorState_shouldShowRetryEntryForCurrentTab() {
        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(
                        isLoggedIn = true,
                        title = "Wucy",
                        summary = "在线账户 · Lv.10"
                    ),
                    contentState = UserCenterUiState(
                        selectedTab = UserCenterTab.COLUMNS,
                        columnsState = UserCenterTabContentState.Error("专栏加载失败")
                    ),
                    onTabSelected = {},
                    onRetryCurrentTab = {},
                    onContentClick = {},
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("user_center_scroll_content").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("user_center_tab_columns").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_content_retry").assertIsDisplayed()
    }

    @Test
    fun playlistTab_withManyItems_shouldNotComposeFarItemsBeforeScroll() {
        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(
                        isLoggedIn = true,
                        title = "Wucy",
                        summary = "在线账户 · Lv.10"
                    ),
                    contentState = UserCenterUiState(
                        selectedTab = UserCenterTab.PLAYLISTS,
                        playlistsState = UserCenterTabContentState.Content(
                            items = List(1000) { index ->
                                UserCenterCollectionItemUiModel(
                                    id = "playlist-$index",
                                    title = "歌单 $index",
                                    subtitle = "Wucy",
                                    imageUrl = null
                                )
                            }
                        )
                    ),
                    onTabSelected = {},
                    onRetryCurrentTab = {},
                    onContentClick = {},
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onAllNodesWithTag("user_center_content_item_playlist-999").assertCountEquals(0)
    }

    @Test
    fun loggedInState_shouldKeepTabsPinnedWhileScrollingContent() {
        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(
                        isLoggedIn = true,
                        title = "Wucy",
                        summary = "在线账户 · Lv.10"
                    ),
                    contentState = UserCenterUiState(
                        selectedTab = UserCenterTab.PLAYLISTS,
                        playlistsState = UserCenterTabContentState.Content(
                            items = List(40) { index ->
                                UserCenterCollectionItemUiModel(
                                    id = "playlist-$index",
                                    title = "歌单 $index",
                                    subtitle = "Wucy",
                                    imageUrl = null
                                )
                            }
                        )
                    ),
                    onTabSelected = {},
                    onRetryCurrentTab = {},
                    onContentClick = {},
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("user_center_scroll_content").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("user_center_sticky_tabs_header").assertIsDisplayed()
        repeat(3) {
            composeRule.onNodeWithTag("user_center_scroll_content").performTouchInput { swipeUp() }
        }
        composeRule.onNodeWithTag("user_center_sticky_tabs_header").assertIsDisplayed()
        composeRule.onNodeWithTag("user_center_tab_playlists").assertIsDisplayed()
    }

    @Test
    fun loggedInState_shouldSeparateProfileCardFromContentPanel() {
        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(
                        isLoggedIn = true,
                        title = "Wucy",
                        summary = "在线账户 · Lv.10"
                    ),
                    contentState = UserCenterUiState(
                        selectedTab = UserCenterTab.PLAYLISTS,
                        playlistsState = UserCenterTabContentState.Content(
                            items = List(4) { index ->
                                UserCenterCollectionItemUiModel(
                                    id = "playlist-$index",
                                    title = "歌单 $index",
                                    subtitle = "Wucy",
                                    imageUrl = null
                                )
                            }
                        )
                    ),
                    onTabSelected = {},
                    onRetryCurrentTab = {},
                    onContentClick = {},
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        val headerBounds = composeRule.onNodeWithTag("user_center_profile_header").fetchSemanticsNode().boundsInRoot
        val stickyHeaderBounds = composeRule.onNodeWithTag("user_center_sticky_tabs_header").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Expected profile card and content panel to stay visually separated, but gap was ${stickyHeaderBounds.top - headerBounds.bottom}",
            stickyHeaderBounds.top > headerBounds.bottom
        )
    }

    @Test
    fun loggedInState_shouldKeepHeaderAndStickyPanelNearEdgesWithSmallInsets() {
        composeRule.setContent {
            PlayerLiteTheme {
                UserCenterScreen(
                    userState = UserSessionUiState(
                        isLoggedIn = true,
                        title = "Wucy",
                        summary = "在线账户 · Lv.10"
                    ),
                    contentState = UserCenterUiState(
                        selectedTab = UserCenterTab.PLAYLISTS,
                        playlistsState = UserCenterTabContentState.Content(
                            items = List(8) { index ->
                                UserCenterCollectionItemUiModel(
                                    id = "playlist-$index",
                                    title = "歌单 $index",
                                    subtitle = "Wucy",
                                    imageUrl = null
                                )
                            }
                        )
                    ),
                    onTabSelected = {},
                    onRetryCurrentTab = {},
                    onContentClick = {},
                    onLoginClick = {},
                    onLogoutClick = {}
                )
            }
        }

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val headerBounds = composeRule.onNodeWithTag("user_center_profile_header").fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithTag("user_center_scroll_content").performTouchInput { swipeUp() }
        val stickyHeaderBounds = composeRule.onNodeWithTag("user_center_sticky_tabs_header").fetchSemanticsNode().boundsInRoot

        val rootWidth = rootBounds.right - rootBounds.left
        val headerLeftInset = headerBounds.left - rootBounds.left
        val headerRightInset = rootBounds.right - headerBounds.right
        val stickyLeftInset = stickyHeaderBounds.left - rootBounds.left
        val stickyRightInset = rootBounds.right - stickyHeaderBounds.right

        assertTrue(
            "Expected profile header to keep a small left inset, but was $headerLeftInset",
            headerLeftInset > 0f && headerLeftInset < rootWidth * 0.08f
        )
        assertTrue(
            "Expected profile header to keep a small right inset, but was $headerRightInset",
            headerRightInset > 0f && headerRightInset < rootWidth * 0.08f
        )
        assertTrue(
            "Expected sticky tabs header to keep a small left inset, but was $stickyLeftInset",
            stickyLeftInset > 0f && stickyLeftInset < rootWidth * 0.08f
        )
        assertTrue(
            "Expected sticky tabs header to keep a small right inset, but was $stickyRightInset",
            stickyRightInset > 0f && stickyRightInset < rootWidth * 0.08f
        )
    }
}
