package com.wxy.playerlite.feature.player.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.designsystem.theme.PlayerLiteThemeContract
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistSheetVisualsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resolvePlaylistSheetItemVisuals_shouldHighlightActiveItemAndKeepInactiveRowsLightweight() {
        val colorScheme = PlayerLiteThemeContract.colorScheme(darkTheme = false)
        val visualTokens = PlayerLiteThemeContract.visualTokens(
            darkTheme = false,
            colorScheme = colorScheme
        )

        val active = resolvePlaylistSheetItemVisuals(
            isActive = true,
            isDragging = false,
            canReorder = true,
            visualTokens = visualTokens
        )
        val inactive = resolvePlaylistSheetItemVisuals(
            isActive = false,
            isDragging = false,
            canReorder = true,
            visualTokens = visualTokens
        )

        assertEquals(visualTokens.accentStrong.copy(alpha = 0.045f), active.containerColor)
        assertEquals(visualTokens.accentStrong, active.titleColor)
        assertTrue(!active.raised)
        assertEquals(Color.Transparent, inactive.containerColor)
        assertEquals(PlayerLiteThemeContract.DefaultBrandPalettes.light.onSurface, inactive.titleColor)
        assertEquals(visualTokens.textMuted, inactive.subtitleColor)
        assertEquals(null, inactive.border)
    }

    @Test
    fun resolvePlaylistSheetItemSubtitle_shouldPreferArtistOverInteractionHintText() {
        val item = PlaylistItem(
            id = "track-1",
            uri = "https://example.com/track-1.mp3",
            displayName = "悬日",
            title = "悬日",
            artistText = "田馥甄",
            albumTitle = "无人知晓"
        )

        assertEquals("田馥甄", resolvePlaylistSheetItemSubtitle(item))
    }

    @Test
    fun resolvePlaylistSheetLayoutSpec_shouldUseHalfWidthSidePanelInLandscape() {
        val spec = resolvePlaylistSheetLayoutSpec(
            viewportWidthDp = 960f,
            viewportHeightDp = 540f
        )

        assertTrue(spec.isLandscape)
        assertEquals(0.5f, spec.widthFraction)
        assertEquals(360f, spec.minWidthDp)
        assertEquals(560f, spec.maxWidthDp)
        assertEquals(0.84f, spec.heightFraction)
        assertTrue(spec.dockToEnd)
    }

    @Test
    fun resolvePlaylistSheetLayoutSpec_shouldKeepFullWidthBottomSheetInPortrait() {
        val spec = resolvePlaylistSheetLayoutSpec(
            viewportWidthDp = 360f,
            viewportHeightDp = 760f
        )

        assertTrue(!spec.isLandscape)
        assertEquals(1f, spec.widthFraction)
        assertEquals(null, spec.minWidthDp)
        assertEquals(null, spec.maxWidthDp)
        assertEquals(0.74f, spec.heightFraction)
        assertTrue(!spec.dockToEnd)
    }

    @Test
    fun playlistBottomSheet_shouldScrollActiveItemIntoViewportWhenOpened() {
        val items = buildPlaylistItems(prefix = "opened")

        composeRule.setContent {
            PlayerLiteTheme {
                PlaylistBottomSheet(
                    visible = true,
                    items = items,
                    activeIndex = 30,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorder = true,
                    onDismiss = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSelect = {},
                    onRemove = {},
                    onMove = { _, _ -> }
                )
            }
        }

        composeRule.waitForIdle()
        waitUntilFirstVisibleIndex(expected = 30)

        composeRule
            .onNodeWithTag("playlist_sheet_artwork_opened-30", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun playlistBottomSheet_rows_shouldUseBalancedVerticalDensity() {
        val items = buildPlaylistItems(prefix = "density")

        composeRule.setContent {
            PlayerLiteTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 760.dp)) {
                    PlaylistBottomSheet(
                        visible = true,
                        items = items,
                        activeIndex = 0,
                        playbackMode = PlaybackMode.LIST_LOOP,
                        showOriginalOrderInShuffle = false,
                        canReorder = true,
                        onDismiss = {},
                        onShowOriginalOrderInShuffleChange = {},
                        onSelect = {},
                        onRemove = {},
                        onMove = { _, _ -> }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val activeRowBounds = composeRule
            .onNodeWithTag("playlist_sheet_item_row_density-0", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val nextRowBounds = composeRule
            .onNodeWithTag("playlist_sheet_item_row_density-1", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val activeHeight = with(composeRule.density) { activeRowBounds.height.toDp() }
        val rowGap = with(composeRule.density) { (nextRowBounds.top - activeRowBounds.bottom).toDp() }

        assertTrue(
            "Expected active queue row to keep enough breathing room, but height was $activeHeight",
            activeHeight >= 72.dp
        )
        assertTrue(
            "Expected active queue row to avoid oversized card spacing, but height was $activeHeight",
            activeHeight <= 88.dp
        )
        assertTrue(
            "Expected queue row gap to avoid cramped rows, but gap was $rowGap",
            rowGap >= 8.dp
        )
        assertTrue(
            "Expected queue row gap to avoid oversized card spacing, but gap was $rowGap",
            rowGap <= 20.dp
        )
    }

    @Test
    fun playlistBottomSheet_shouldFollowActiveItemIdentityChangeWhileVisible() {
        var items by mutableStateOf(buildPlaylistItems(prefix = "before"))

        composeRule.setContent {
            PlayerLiteTheme {
                PlaylistBottomSheet(
                    visible = true,
                    items = items,
                    activeIndex = 30,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorder = true,
                    onDismiss = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSelect = {},
                    onRemove = {},
                    onMove = { _, _ -> }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag("playlist_sheet_artwork_before-30", useUnmergedTree = true)
            .assertIsDisplayed()

        scrollAwayFromActiveItem()

        composeRule.runOnIdle {
            items = buildPlaylistItems(prefix = "after")
        }
        composeRule.waitForIdle()
        waitUntilFirstVisibleIndex(expected = 30)

        composeRule
            .onNodeWithTag("playlist_sheet_artwork_after-30", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun playlistBottomSheet_shouldNotStealScrollOnPlainRecompose() {
        val items = buildPlaylistItems(prefix = "stable")
        var recomposeTick by mutableIntStateOf(0)

        composeRule.setContent {
            PlayerLiteTheme {
                recomposeTick
                PlaylistBottomSheet(
                    visible = true,
                    items = items,
                    activeIndex = 30,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorder = true,
                    onDismiss = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSelect = {},
                    onRemove = {},
                    onMove = { _, _ -> }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag("playlist_sheet_artwork_stable-30", useUnmergedTree = true)
            .assertIsDisplayed()

        scrollAwayFromActiveItem()

        composeRule.runOnIdle {
            recomposeTick += 1
        }
        composeRule.waitForIdle()

        waitUntilFirstVisibleIndex(expected = 0)
    }

    @Test
    fun playlistBottomSheet_shouldShowAndCyclePlaybackModeFromHeader() {
        var playbackMode by mutableStateOf(PlaybackMode.LIST_LOOP)
        var cycleCount = 0

        composeRule.setContent {
            PlayerLiteTheme {
                PlaylistBottomSheet(
                    visible = true,
                    items = buildPlaylistItems(prefix = "mode"),
                    activeIndex = 0,
                    playbackMode = playbackMode,
                    showOriginalOrderInShuffle = false,
                    canReorder = true,
                    onDismiss = {},
                    onCyclePlaybackMode = {
                        cycleCount += 1
                        playbackMode = when (playbackMode) {
                            PlaybackMode.LIST_LOOP -> PlaybackMode.SINGLE_LOOP
                            PlaybackMode.SINGLE_LOOP -> PlaybackMode.SHUFFLE
                            PlaybackMode.SHUFFLE -> PlaybackMode.LIST_LOOP
                        }
                    },
                    onShowOriginalOrderInShuffleChange = {},
                    onSelect = {},
                    onRemove = {},
                    onMove = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("列表循环").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_sheet_mode_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("单曲循环").assertIsDisplayed()
        composeRule.onNodeWithTag("playlist_sheet_mode_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("随机播放").assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(2, cycleCount)
        }
    }

    @Test
    fun playlistBottomSheet_shouldOpenQueueAwareMoreMenuAndDispatchActions() {
        var detailId: String? = null
        var artistId: String? = null
        var albumId: String? = null
        var removedIndex = -1

        composeRule.setContent {
            PlayerLiteTheme {
                PlaylistBottomSheet(
                    visible = true,
                    items = listOf(
                        PlaylistItem(
                            id = "queue-1",
                            uri = "https://example.com/queue-1.mp3",
                            displayName = "夜曲",
                            songId = "song-1",
                            title = "夜曲",
                            artistText = "周杰伦",
                            primaryArtistId = "artist-1",
                            albumId = "album-1",
                            albumTitle = "十一月的萧邦"
                        )
                    ),
                    activeIndex = 0,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorder = true,
                    onDismiss = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSelect = {},
                    onClearAll = {},
                    onRemove = { removedIndex = it },
                    onMove = { _, _ -> },
                    onOpenSongDetail = { detailId = it.id },
                    onOpenArtist = { artistId = it },
                    onOpenAlbum = { albumId = it },
                    expandedMenuItemIdOverride = "queue-1"
                )
            }
        }

        composeRule.onNodeWithTag("playlist_sheet_more_queue-1").assertIsDisplayed()
        composeRule.onAllNodesWithText("下一首播放").assertCountEquals(0)
        composeRule.onAllNodesWithTag("playlist_sheet_action_detail_queue-1").assertCountEquals(1)
        composeRule.onNodeWithTag("playlist_sheet_action_detail_queue-1")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onAllNodesWithTag("playlist_sheet_action_artist_queue-1").assertCountEquals(1)
        composeRule.onNodeWithTag("playlist_sheet_action_artist_queue-1")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onAllNodesWithTag("playlist_sheet_action_album_queue-1").assertCountEquals(1)
        composeRule.onNodeWithTag("playlist_sheet_action_album_queue-1")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onAllNodesWithTag("playlist_sheet_action_remove_queue-1").assertCountEquals(1)
        composeRule.onNodeWithTag("playlist_sheet_action_remove_queue-1")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals("queue-1", detailId)
            assertEquals("artist-1", artistId)
            assertEquals("album-1", albumId)
            assertEquals(0, removedIndex)
        }
    }

    @Test
    fun playlistBottomSheet_missingArtistOrAlbumId_shouldHideUnsupportedQueueActions() {
        composeRule.setContent {
            PlayerLiteTheme {
                PlaylistBottomSheet(
                    visible = true,
                    items = listOf(
                        PlaylistItem(
                            id = "queue-2",
                            uri = "https://example.com/queue-2.mp3",
                            displayName = "稻香",
                            songId = "song-2",
                            title = "稻香"
                        )
                    ),
                    activeIndex = 0,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorder = true,
                    onDismiss = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSelect = {},
                    onClearAll = {},
                    onRemove = {},
                    onMove = { _, _ -> },
                    expandedMenuItemIdOverride = "queue-2"
                )
            }
        }

        composeRule.onNodeWithTag("playlist_sheet_more_queue-2").assertIsDisplayed()
        composeRule.onAllNodesWithTag("playlist_sheet_action_detail_queue-2").assertCountEquals(1)
        composeRule.onAllNodesWithTag("playlist_sheet_action_remove_queue-2").assertCountEquals(1)
        composeRule.onAllNodesWithTag("playlist_sheet_action_artist_queue-2").assertCountEquals(0)
        composeRule.onAllNodesWithTag("playlist_sheet_action_album_queue-2").assertCountEquals(0)
    }

    private fun scrollAwayFromActiveItem() {
        composeRule.onNodeWithTag("playlist_sheet_list").performScrollToIndex(0)
        waitUntilFirstVisibleIndex(expected = 0)
    }

    private fun waitUntilFirstVisibleIndex(expected: Int) {
        val matcher = SemanticsMatcher.expectValue(PlaylistSheetFirstVisibleIndexKey, expected)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithTag("playlist_sheet_list")
                .fetchSemanticsNodes()
                .singleOrNull()
                ?.config
                ?.getOrNull(PlaylistSheetFirstVisibleIndexKey) == expected
        }
        composeRule.onNodeWithTag("playlist_sheet_list").assert(matcher)
    }

    private fun buildPlaylistItems(prefix: String): List<PlaylistItem> {
        return List(40) { index ->
            PlaylistItem(
                id = "$prefix-$index",
                uri = "file:///$prefix-$index.mp3",
                displayName = "歌曲 $index"
            )
        }
    }
}
