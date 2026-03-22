package com.wxy.playerlite.feature.artist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.material3.MaterialTheme
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class ArtistDetailScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentState_shouldShowHeroStickyTabsAndHotSongsActions() {
        composeRule.setContent {
            PlayerLiteTheme {
                ArtistDetailScreen(
                    state = contentState(
                        hotSongs = listOf(
                            hotSong(trackId = "210049", title = "布拉格广场")
                        )
                    ),
                    heroAccentColor = MaterialTheme.colorScheme.primary,
                    topBarContentColor = MaterialTheme.colorScheme.surface,
                    onBack = {},
                    onRetry = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("detail_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_detail_hero_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_detail_cover").assertIsDisplayed()
        scrollOuterToTag("artist_sticky_tabs_header")
        composeRule.onNodeWithTag("artist_sticky_tabs_header").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_tab_hot_songs").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_tab_albums").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_tab_encyclopedia").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_hero_play_hot_button").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_hero_follow_button").assertIsDisplayed()
        scrollOuterToTag("artist_tab_pager")
        composeRule.onNodeWithTag("artist_hot_song_210049").assertIsDisplayed()
    }

    @Test
    fun swipingToAlbums_afterScrollingHotSongs_shouldShowAlbumsPanel() {
        composeRule.setContent {
            PlayerLiteTheme {
                ArtistDetailScreen(
                    state = contentState(
                        hotSongs = List(12) { index ->
                            hotSong(
                                trackId = "track-$index",
                                title = "热门歌曲 $index"
                            )
                        },
                        albums = List(12) { index ->
                            album(
                                albumId = "album-$index",
                                title = "专辑 $index"
                            )
                        }
                    ),
                    heroAccentColor = MaterialTheme.colorScheme.primary,
                    topBarContentColor = MaterialTheme.colorScheme.surface,
                    onBack = {},
                    onRetry = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        scrollOuterToTag("artist_tab_pager")
        scrollHotSongsToTag("artist_hot_song_track-4")
        composeRule.onNodeWithTag("artist_hot_song_track-4").assertIsDisplayed()

        composeRule.onNodeWithTag("artist_tab_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("artist_album_album-0").assertIsDisplayed()
    }

    @Test
    fun selectingTabs_beforeStickyHeaderPins_shouldNotForceWholePageToTop() {
        composeRule.setContent {
            PlayerLiteTheme {
                ArtistDetailScreen(
                    state = contentState(
                        hotSongs = List(12) { index ->
                            hotSong(
                                trackId = "track-$index",
                                title = "热门歌曲 $index"
                            )
                        },
                        albums = List(12) { index ->
                            album(
                                albumId = "album-$index",
                                title = "专辑 $index"
                            )
                        }
                    ),
                    heroAccentColor = MaterialTheme.colorScheme.primary,
                    topBarContentColor = MaterialTheme.colorScheme.surface,
                    onBack = {},
                    onRetry = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("artist_tab_albums"))
        val heroBoundsBeforeClick = composeRule
            .onNodeWithTag("artist_detail_hero_panel")
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.onNodeWithTag("artist_tab_albums")
            .performClick()
        composeRule.waitForIdle()

        val heroBoundsAfterClick = composeRule
            .onNodeWithTag("artist_detail_hero_panel")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected selecting albums tab before sticky header pins to keep outer page offset stable, before=$heroBoundsBeforeClick after=$heroBoundsAfterClick",
            abs(heroBoundsBeforeClick.top - heroBoundsAfterClick.top) < 1f
        )
        composeRule.onNodeWithTag("artist_album_album-0").assertIsDisplayed()
    }

    @Test
    fun scrollingLongHotSongs_shouldKeepStickyTabsHeaderVisible() {
        composeRule.setContent {
            PlayerLiteTheme {
                ArtistDetailScreen(
                    state = contentState(
                        hotSongs = List(30) { index ->
                            hotSong(
                                trackId = "track-$index",
                                title = "热门歌曲 $index"
                            )
                        }
                    ),
                    heroAccentColor = MaterialTheme.colorScheme.primary,
                    topBarContentColor = MaterialTheme.colorScheme.surface,
                    onBack = {},
                    onRetry = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        scrollOuterToTag("artist_sticky_tabs_header")
        composeRule.onNodeWithTag("artist_sticky_tabs_header").assertIsDisplayed()
        repeat(4) {
            composeRule.onNodeWithTag("artist_hot_songs_list").performTouchInput { swipeUp() }
        }
        composeRule.onNodeWithTag("artist_sticky_tabs_header").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_tab_hot_songs").assertIsDisplayed()
    }

    @Test
    fun collapsedTopBar_afterStickyHeaderPins_shouldExposeArtistNameSemantics() {
        composeRule.setContent {
            PlayerLiteTheme {
                ArtistDetailScreen(
                    state = contentState(
                        hotSongs = List(30) { index ->
                            hotSong(
                                trackId = "track-$index",
                                title = "热门歌曲 $index"
                            )
                        }
                    ),
                    heroAccentColor = MaterialTheme.colorScheme.primary,
                    topBarContentColor = MaterialTheme.colorScheme.surface,
                    onBack = {},
                    onRetry = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        scrollOuterToTag("artist_tab_pager")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("artist_collapsing_top_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_collapsing_top_bar_title")
            .assertIsDisplayed()
            .assertTextEquals("周杰伦")
    }

    @Test
    fun stickyTabsHeader_afterPinning_shouldSitDirectlyBelowCollapsingTopBar() {
        composeRule.setContent {
            PlayerLiteTheme {
                ArtistDetailScreen(
                    state = contentState(
                        hotSongs = List(30) { index ->
                            hotSong(
                                trackId = "track-$index",
                                title = "热门歌曲 $index"
                            )
                        }
                    ),
                    heroAccentColor = MaterialTheme.colorScheme.primary,
                    topBarContentColor = MaterialTheme.colorScheme.surface,
                    onBack = {},
                    onRetry = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        scrollOuterToTag("artist_tab_pager")
        composeRule.waitForIdle()

        val topBarBounds = composeRule
            .onNodeWithTag("artist_collapsing_top_bar")
            .fetchSemanticsNode()
            .boundsInRoot
        val stickyHeaderBounds = composeRule
            .onNodeWithTag("artist_sticky_tabs_header")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected sticky tabs header to sit directly below collapsing top bar, topBar=$topBarBounds sticky=$stickyHeaderBounds",
            abs(topBarBounds.bottom - stickyHeaderBounds.top) < 1f
        )
    }

    @Test
    fun selectingEncyclopediaTab_shouldShowFullDescriptionPanel() {
        val longDescription = "周杰伦简介第一段。周杰伦简介第二段。周杰伦简介第三段。"

        composeRule.setContent {
            PlayerLiteTheme {
                ArtistDetailScreen(
                    state = contentState(
                        briefDesc = longDescription,
                        hotSongsState = ArtistHotSongsUiState.Empty
                    ),
                    heroAccentColor = MaterialTheme.colorScheme.primary,
                    topBarContentColor = MaterialTheme.colorScheme.surface,
                    onBack = {},
                    onRetry = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        scrollOuterToTag("artist_sticky_tabs_header")
        composeRule.onNodeWithTag("artist_tab_encyclopedia")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("artist_encyclopedia_list").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_encyclopedia_panel").assertIsDisplayed()
        composeRule.onNodeWithText(longDescription).assertIsDisplayed()
    }

    @Test
    fun clickingCover_shouldShowPreviewDialog() {
        composeRule.setContent {
            PlayerLiteTheme {
                ArtistDetailScreen(
                    state = contentState(
                        avatarUrl = "http://example.com/avatar.jpg",
                        hotSongsState = ArtistHotSongsUiState.Empty
                    ),
                    heroAccentColor = MaterialTheme.colorScheme.primary,
                    topBarContentColor = MaterialTheme.colorScheme.surface,
                    onBack = {},
                    onRetry = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        scrollOuterToTag("artist_detail_cover")
        composeRule.onNodeWithTag("artist_detail_cover", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("关闭").assertIsDisplayed()
    }

    @Test
    fun hotSongsError_shouldKeepHeroAndShowSectionRetry() {
        composeRule.setContent {
            PlayerLiteTheme {
                ArtistDetailScreen(
                    state = contentState(
                        hotSongsState = ArtistHotSongsUiState.Error("热门歌曲加载失败")
                    ),
                    heroAccentColor = MaterialTheme.colorScheme.primary,
                    topBarContentColor = MaterialTheme.colorScheme.surface,
                    onBack = {},
                    onRetry = {},
                    onPlayAll = {},
                    onTrackClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("artist_detail_hero_panel").assertIsDisplayed()
        scrollOuterToTag("artist_tab_pager")
        composeRule.onNodeWithTag("artist_hot_songs_error").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
    }

    @Test
    fun playAllAndTrackClick_shouldInvokeCallbacks() {
        var playAllClicks = 0
        var clickedTrackIndex = -1

        composeRule.setContent {
            PlayerLiteTheme {
                ArtistDetailScreen(
                    state = contentState(
                        hotSongs = listOf(
                            hotSong(trackId = "210049", title = "布拉格广场")
                        )
                    ),
                    heroAccentColor = MaterialTheme.colorScheme.primary,
                    topBarContentColor = MaterialTheme.colorScheme.surface,
                    onBack = {},
                    onRetry = {},
                    onPlayAll = { playAllClicks++ },
                    onTrackClick = { clickedTrackIndex = it }
                )
            }
        }

        scrollOuterToTag("artist_hero_play_hot_button")
        composeRule.onNodeWithTag("artist_hero_play_hot_button", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        scrollOuterToTag("artist_tab_pager")
        composeRule.onNodeWithTag("artist_hot_song_210049").performClick()

        assertEquals(1, playAllClicks)
        assertEquals(0, clickedTrackIndex)
    }

    private fun contentState(
        briefDesc: String = "简介",
        avatarUrl: String? = null,
        coverUrl: String? = null,
        encyclopediaState: ArtistEncyclopediaUiState = ArtistEncyclopediaUiState.Empty,
        hotSongsState: ArtistHotSongsUiState? = null,
        hotSongs: List<ArtistHotSongRow>? = null,
        albumsState: ArtistAlbumsUiState? = null,
        albums: List<ArtistAlbumRow>? = null
    ): ArtistDetailUiState {
        val resolvedHotSongsState = hotSongsState ?: ArtistHotSongsUiState.Content(
            hotSongs ?: listOf(hotSong())
        )
        val resolvedAlbumsState = albumsState ?: albums?.let { items ->
            ArtistAlbumsUiState.Content(
                items = items,
                hasMore = false
            )
        } ?: ArtistAlbumsUiState.Loading
        return ArtistDetailUiState(
            headerState = ArtistDetailHeaderUiState.Content(
                ArtistDetailContent(
                    artistId = "6452",
                    name = "周杰伦",
                    aliases = listOf("Jay Chou"),
                    identities = listOf("作曲"),
                    avatarUrl = avatarUrl,
                    coverUrl = coverUrl,
                    briefDesc = briefDesc,
                    musicCount = 568,
                    albumCount = 44
                )
            ),
            encyclopediaState = encyclopediaState,
            hotSongsState = resolvedHotSongsState,
            albumsState = resolvedAlbumsState
        )
    }

    private fun hotSong(
        trackId: String = "210049",
        title: String = "布拉格广场"
    ): ArtistHotSongRow {
        return ArtistHotSongRow(
            trackId = trackId,
            title = title,
            artistText = "蔡依林 / 周杰伦",
            albumTitle = "看我72变",
            coverUrl = null,
            durationMs = 294600L
        )
    }

    private fun album(
        albumId: String = "album-0",
        title: String = "最伟大的作品"
    ): ArtistAlbumRow {
        return ArtistAlbumRow(
            albumId = albumId,
            title = title,
            artistText = "周杰伦",
            coverUrl = null,
            trackCount = 10,
            type = "录音室专辑",
            publishTimeText = "2022-07-15"
        )
    }

    private fun scrollOuterToTag(tag: String) {
        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag(tag))
    }

    private fun scrollHotSongsToTag(tag: String) {
        composeRule.onNodeWithTag("artist_hot_songs_list")
            .performScrollToNode(hasTestTag(tag))
    }

}
