package com.wxy.playerlite.feature.main

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wxy.playerlite.feature.search.SearchRouteTarget
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentSongsScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun screen_shouldRenderAllTabsAndSwitchContent() {
        var selectedTab by mutableStateOf(RecentPlaybackTab.SONGS)

        composeRule.setContent {
            PlayerLiteTheme {
                RecentSongsScreen(
                    state = RecentSongsUiState(
                        isLoggedIn = true,
                        selectedTab = selectedTab,
                        tabStates = mapOf(
                            RecentPlaybackTab.SONGS to RecentPlaybackContentState.SongContent(
                                listOf(
                                    RecentSongItemUiModel(
                                        id = "song-1",
                                        title = "反方向的钟",
                                        artistText = "周杰伦 / 温岚",
                                        imageUrl = null,
                                        albumTitle = "叶惠美",
                                        detailAction = ContentEntryAction.OpenDetail(
                                            SearchRouteTarget.Song("song-1")
                                        )
                                    )
                                )
                            ),
                            RecentPlaybackTab.ALBUMS to RecentPlaybackContentState.GenericContent(
                                listOf(
                                    RecentPlaybackListItemUiModel(
                                        id = "album-1",
                                        title = "叶惠美",
                                        subtitle = "周杰伦",
                                        imageUrl = null,
                                        meta = "10 首",
                                        badge = "专辑"
                                    )
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onLoginClick = {},
                    onRetry = {},
                    onSelectTab = { selectedTab = it },
                    onItemClick = {},
                    onItemInsertNext = {},
                    onItemOpenDetail = {},
                    onItemOpenArtist = {},
                    onItemOpenAlbum = {},
                    onLocalItemClick = {},
                    onLocalItemInsertNext = {},
                    onLocalItemOpenDetail = {},
                    onLocalItemOpenArtist = {},
                    onLocalItemOpenAlbum = {}
                )
            }
        }

        RecentPlaybackTab.entries.forEach { tab ->
            composeRule.onNodeWithTag("recent_playback_tab_${tab.testTag}")
                .assertIsDisplayed()
                .assertHasClickAction()
        }

        composeRule.onNodeWithText("反方向的钟").assertIsDisplayed()
        composeRule.onNodeWithTag("recent_songs_item_more_song-1").assertIsDisplayed()

        composeRule.onNodeWithTag("recent_playback_tab_albums").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("叶惠美").assertIsDisplayed()
        composeRule.onNodeWithText("周杰伦").assertIsDisplayed()
        composeRule.onNodeWithText("专辑 · 10 首").assertIsDisplayed()
        composeRule.onAllNodesWithTag("recent_songs_item_more_song-1").assertCountEquals(0)
    }

    @Test
    fun loggedOut_shouldStillRenderLocalTabContent() {
        composeRule.setContent {
            PlayerLiteTheme {
                RecentSongsScreen(
                    state = RecentSongsUiState(
                        isLoggedIn = false,
                        selectedTab = RecentPlaybackTab.LOCAL,
                        tabStates = mapOf(
                            RecentPlaybackTab.LOCAL to RecentPlaybackContentState.LocalContent(
                                listOf(
                                    RecentLocalPlaybackItemUiModel(
                                        recordKey = "online:song-1",
                                        sourceType = com.wxy.playerlite.core.playlist.PlaylistItemType.ONLINE,
                                        songId = "song-1",
                                        playbackUri = "",
                                        title = "缓存歌曲",
                                        artistText = "缓存歌手",
                                        imageUrl = null
                                    )
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onLoginClick = {},
                    onRetry = {},
                    onSelectTab = {},
                    onItemClick = {},
                    onItemInsertNext = {},
                    onItemOpenDetail = {},
                    onItemOpenArtist = {},
                    onItemOpenAlbum = {},
                    onLocalItemClick = {},
                    onLocalItemInsertNext = {},
                    onLocalItemOpenDetail = {},
                    onLocalItemOpenArtist = {},
                    onLocalItemOpenAlbum = {}
                )
            }
        }

        composeRule.onNodeWithText("缓存歌曲").assertIsDisplayed()
        composeRule.onAllNodesWithTag("recent_songs_login_state").assertCountEquals(0)
    }

    @Test
    fun loggedOut_remoteTab_shouldRenderLoginState() {
        composeRule.setContent {
            PlayerLiteTheme {
                RecentSongsScreen(
                    state = RecentSongsUiState(
                        isLoggedIn = false,
                        selectedTab = RecentPlaybackTab.SONGS
                    ),
                    onBack = {},
                    onLoginClick = {},
                    onRetry = {},
                    onSelectTab = {},
                    onItemClick = {},
                    onItemInsertNext = {},
                    onItemOpenDetail = {},
                    onItemOpenArtist = {},
                    onItemOpenAlbum = {},
                    onLocalItemClick = {},
                    onLocalItemInsertNext = {},
                    onLocalItemOpenDetail = {},
                    onLocalItemOpenArtist = {},
                    onLocalItemOpenAlbum = {}
                )
            }
        }

        composeRule.onNodeWithTag("recent_songs_login_state").assertIsDisplayed()
        composeRule.onNodeWithText("登录后才能查看最近播放").assertIsDisplayed()
    }

    @Test
    fun nonSongTab_shouldRenderReadOnlyRows() {
        composeRule.setContent {
            PlayerLiteTheme {
                RecentSongsScreen(
                    state = RecentSongsUiState(
                        isLoggedIn = true,
                        selectedTab = RecentPlaybackTab.VIDEOS,
                        tabStates = mapOf(
                            RecentPlaybackTab.VIDEOS to RecentPlaybackContentState.GenericContent(
                                listOf(
                                    RecentPlaybackListItemUiModel(
                                        id = "video-1",
                                        title = "晴天 MV",
                                        subtitle = "周杰伦",
                                        imageUrl = null,
                                        meta = "128 万播放",
                                        badge = "视频"
                                    )
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onLoginClick = {},
                    onRetry = {},
                    onSelectTab = {},
                    onItemClick = {},
                    onItemInsertNext = {},
                    onItemOpenDetail = {},
                    onItemOpenArtist = {},
                    onItemOpenAlbum = {},
                    onLocalItemClick = {},
                    onLocalItemInsertNext = {},
                    onLocalItemOpenDetail = {},
                    onLocalItemOpenArtist = {},
                    onLocalItemOpenAlbum = {}
                )
            }
        }

        composeRule.onNodeWithText("晴天 MV").assertIsDisplayed()
        composeRule.onNodeWithText("周杰伦").assertIsDisplayed()
        composeRule.onNodeWithText("视频 · 128 万播放").assertIsDisplayed()
        composeRule.onNodeWithTag("recent_playback_item_videos_video-1").assertIsDisplayed()
        composeRule.onNodeWithTag("recent_playback_more_placeholder_video-1").assertIsDisplayed()
        composeRule.onAllNodesWithText("查看歌曲详情").assertCountEquals(0)
    }

    @Test
    fun songTab_shouldKeepOverflowMenuActions() {
        var clickedId: String? = null
        var detailId: String? = null

        composeRule.setContent {
            PlayerLiteTheme {
                RecentSongsScreen(
                    state = RecentSongsUiState(
                        isLoggedIn = true,
                        selectedTab = RecentPlaybackTab.SONGS,
                        tabStates = mapOf(
                            RecentPlaybackTab.SONGS to RecentPlaybackContentState.SongContent(
                                listOf(
                                    RecentSongItemUiModel(
                                        id = "song-1",
                                        title = "反方向的钟",
                                        artistText = "周杰伦 / 温岚",
                                        imageUrl = null,
                                        albumTitle = "叶惠美",
                                        primaryArtistId = "artist-1",
                                        albumId = "album-1",
                                        detailAction = ContentEntryAction.OpenDetail(
                                            SearchRouteTarget.Song("song-1")
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onLoginClick = {},
                    onRetry = {},
                    onSelectTab = {},
                    onItemClick = { clickedId = it.id },
                    onItemInsertNext = {},
                    onItemOpenDetail = { detailId = it.id },
                    onItemOpenArtist = {},
                    onItemOpenAlbum = {},
                    onLocalItemClick = {},
                    onLocalItemInsertNext = {},
                    onLocalItemOpenDetail = {},
                    onLocalItemOpenArtist = {},
                    onLocalItemOpenAlbum = {}
                )
            }
        }

        composeRule.onNodeWithTag("recent_songs_item_song-1").performClick()
        composeRule.onNodeWithTag("recent_songs_item_more_song-1").performClick()

        composeRule.runOnIdle {
            assertEquals("song-1", clickedId)
            assertEquals("song-1", detailId)
        }
    }

    @Test
    fun localTab_shouldRenderAndKeepOverflowMenuActions() {
        var clickedUri: String? = null
        var detailUri: String? = null
        val playbackUri = ""
        val songId = "song-1"
        val recordKey = "online:$songId"
        val itemId = "recent-cached-${recordKey.hashCode()}"

        composeRule.setContent {
            PlayerLiteTheme {
                RecentSongsScreen(
                    state = RecentSongsUiState(
                        isLoggedIn = true,
                        selectedTab = RecentPlaybackTab.LOCAL,
                        tabStates = mapOf(
                            RecentPlaybackTab.LOCAL to RecentPlaybackContentState.LocalContent(
                                listOf(
                                    RecentLocalPlaybackItemUiModel(
                                        recordKey = recordKey,
                                        sourceType = com.wxy.playerlite.core.playlist.PlaylistItemType.ONLINE,
                                        songId = songId,
                                        playbackUri = playbackUri,
                                        title = "缓存歌曲",
                                        artistText = "缓存歌手",
                                        imageUrl = null,
                                        albumTitle = "缓存专辑",
                                        primaryArtistId = "artist-1",
                                        albumId = "album-1",
                                        durationMs = 215_000L
                                    )
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onLoginClick = {},
                    onRetry = {},
                    onSelectTab = {},
                    onItemClick = {},
                    onItemInsertNext = {},
                    onItemOpenDetail = {},
                    onItemOpenArtist = {},
                    onItemOpenAlbum = {},
                    onLocalItemClick = { clickedUri = it.playbackUri },
                    onLocalItemInsertNext = {},
                    onLocalItemOpenDetail = { detailUri = it.songId ?: it.playbackUri },
                    onLocalItemOpenArtist = {},
                    onLocalItemOpenAlbum = {}
                )
            }
        }

        composeRule.onNodeWithTag("recent_local_item_$itemId").performClick()
        composeRule.onNodeWithTag("recent_local_item_more_$itemId").performClick()

        composeRule.runOnIdle {
            assertEquals(playbackUri, clickedUri)
            assertEquals(songId, detailUri)
        }
    }
}
