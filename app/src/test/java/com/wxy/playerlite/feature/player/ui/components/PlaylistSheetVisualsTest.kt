package com.wxy.playerlite.feature.player.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.graphics.Color
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

        assertEquals(visualTokens.surfaceRaised, active.containerColor)
        assertEquals(visualTokens.accentStrong, active.titleColor)
        assertTrue(active.raised)
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
