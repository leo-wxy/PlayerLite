package com.wxy.playerlite

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.feature.artist.ArtistDetailActivity
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.ui.PlayerScreen
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CurrentPlayerArtistDetailNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // MainActivity's real startup prewarms the Media3 controller and crashes before UI becomes interactive in JVM tests.
    // This keeps the regression anchored to the current-player host callback itself: artist text click -> startActivity(intent).
    @Test
    fun currentPlayerArtistClick_shouldOpenArtistDetailActivityWithCurrentArtistId() {
        val activity = composeRule.activity
        val currentArtistId = "artist-6452"

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = "布拉格广场",
                    artistText = "周杰伦",
                    status = "正在播放",
                    hasSelection = true,
                    playlistItems = listOf(currentArtistPlayableItem()),
                    activePlaylistIndex = 0,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 273_000L,
                    totalDurationText = "04:33",
                    currentSongId = "210049",
                    currentArtistId = currentArtistId,
                    currentCoverUrl = "https://example.com/brg.jpg",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {},
                    onArtistClick = {
                        activity.startActivity(
                            ArtistDetailActivity.createIntent(
                                context = activity,
                                artistId = currentArtistId
                            )
                        )
                    }
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_artist").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_artist").performClick()

        val startedIntent = shadowOf(activity).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(
            ArtistDetailActivity::class.java.name,
            startedIntent?.component?.className
        )
        assertEquals("artist-6452", ArtistDetailActivity.artistIdFrom(startedIntent!!))
    }

    @Test
    fun currentPlayerArtistClick_shouldFallbackToActivePlaylistArtistIdWhenRuntimeFieldMissing() {
        val activity = composeRule.activity
        val state = firstLaunchPlayerState()
        val resolvedArtistId = resolveCurrentPlayerArtistId(state)
        assertEquals("artist-6452", resolvedArtistId)

        composeRule.setContent {
            PlayerLiteTheme {
                PlayerScreen(
                    fileName = state.currentTrackTitle,
                    artistText = state.currentTrackArtist,
                    status = state.statusText,
                    hasSelection = state.hasSelection,
                    playlistItems = state.playlistItems,
                    activePlaylistIndex = state.activePlaylistIndex,
                    showPlaylistSheet = false,
                    isPreparing = false,
                    playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
                    isSeekSupported = true,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    showOriginalOrderInShuffle = false,
                    canReorderPlaylist = true,
                    seekValueMs = 12_000L,
                    currentDurationText = "00:12",
                    durationMs = 273_000L,
                    totalDurationText = "04:33",
                    currentSongId = "210049",
                    currentArtistId = resolvedArtistId,
                    currentCoverUrl = "https://example.com/brg.jpg",
                    enableEnterMotion = false,
                    onPickAudio = {},
                    onTogglePlaylistSheet = {},
                    onDismissPlaylistSheet = {},
                    onSelectPlaylistItem = {},
                    onRemovePlaylistItem = {},
                    onMovePlaylistItem = { _, _ -> },
                    onPlay = {},
                    onPrevious = {},
                    onNext = {},
                    onPause = {},
                    onResume = {},
                    onCyclePlaybackMode = {},
                    onShowOriginalOrderInShuffleChange = {},
                    onSeekValueChange = {},
                    onSeekFinished = {},
                    onArtistClick = {
                        resolvedArtistId?.let { artistId: String ->
                            activity.startActivity(
                                ArtistDetailActivity.createIntent(
                                    context = activity,
                                    artistId = artistId
                                )
                            )
                        }
                    }
                )
            }
        }

        composeRule.onNodeWithTag("player_screen_artist").assertIsDisplayed()
        composeRule.onNodeWithTag("player_screen_artist").performClick()

        val startedIntent = shadowOf(activity).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(
            ArtistDetailActivity::class.java.name,
            startedIntent?.component?.className
        )
        assertEquals("artist-6452", ArtistDetailActivity.artistIdFrom(startedIntent!!))
    }

    private fun currentArtistPlayableItem(): PlaylistItem {
        return PlaylistItem(
            id = "artist:daily-reco:0:210049",
            displayName = "布拉格广场",
            songId = "210049",
            title = "布拉格广场",
            artistText = "周杰伦",
            primaryArtistId = "artist-6452",
            albumTitle = "范特西",
            coverUrl = "https://example.com/brg.jpg",
            durationMs = 273_000L,
            itemType = PlaylistItemType.ONLINE,
            contextType = "artist",
            contextId = "daily-reco",
            contextTitle = "今日推荐"
        )
    }

    private fun firstLaunchPlayerState(): PlayerUiState {
        return PlayerUiState(
            currentTrackTitle = "布拉格广场",
            currentTrackArtist = "周杰伦",
            currentArtistId = null,
            statusText = "正在播放",
            hasSelection = true,
            playlistItems = listOf(currentArtistPlayableItem()),
            activePlaylistIndex = 0,
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            isSeekSupported = true,
            durationMs = 273_000L,
            seekPositionMs = 12_000L
        )
    }
}
