package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.playback.model.PlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayerRuntimeInteractionTest {
    @Test
    fun updateLocalPlaybackMode_shouldPublishNewModeImmediately() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateLocalPlaybackMode(PlaybackMode.SINGLE_LOOP)

        assertEquals(PlaybackMode.SINGLE_LOOP, runtime.uiStateFlow.value.playbackMode)
    }

    @Test
    fun finishSeekDrag_shouldClampDraggedPositionIntoKnownDuration() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            positionMs = 12_000L,
            durationMs = 120_000L,
            isSeekSupported = true,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = null,
            playbackOutputInfo = null,
            audioMeta = null
        )

        runtime.onSeekValueChange(180_000L)
        assertTrue(runtime.uiStateFlow.value.isSeekDragging)
        assertEquals(180_000L, runtime.uiStateFlow.value.seekDragPositionMs)

        runtime.finishSeekDrag()

        val state = runtime.uiStateFlow.value
        assertFalse(state.isSeekDragging)
        assertEquals(120_000L, state.seekPositionMs)
        assertEquals(120_000L, state.seekDragPositionMs)
        assertEquals(120_000L, state.displayedSeekMs)
    }

    @Test
    fun onSeekValueChange_shouldIgnoreUnsupportedSeekSources() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            positionMs = 8_000L,
            durationMs = 0L,
            isSeekSupported = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = "track-1",
            isProgressAdvancing = false,
            currentPlayable = null,
            playbackOutputInfo = null,
            audioMeta = null
        )

        runtime.onSeekValueChange(30_000L)

        val state = runtime.uiStateFlow.value
        assertFalse(state.isSeekDragging)
        assertEquals(8_000L, state.seekDragPositionMs)
        assertEquals(8_000L, state.seekPositionMs)
    }

    @Test
    fun applyExternalQueueSelection_shouldOnlySwitchIndexWhenQueueMatches() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())
        val sameQueue = listOf(
            onlineItem(contextId = "artist-6452", index = 0, songId = "210049", title = "布拉格广场"),
            onlineItem(contextId = "artist-6452", index = 1, songId = "185809", title = "夜曲")
        )

        val first = runtime.applyExternalQueueSelection(
            items = sameQueue,
            activeIndex = 0
        )
        val second = runtime.applyExternalQueueSelection(
            items = sameQueue,
            activeIndex = 1
        )

        assertTrue(first.replacedQueue)
        assertFalse(second.replacedQueue)
        assertEquals(listOf("布拉格广场", "夜曲"), runtime.uiStateFlow.value.playlistItems.map { it.displayName })
        assertEquals(1, runtime.uiStateFlow.value.activePlaylistIndex)
    }

    @Test
    fun removePlaylistItem_afterExternalQueueSelection_shouldKeepRemainingOrderAndAdvanceActiveItem() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())
        val queue = listOf(
            onlineItem(contextId = "playlist-1", index = 0, songId = "track-1", title = "第一首"),
            onlineItem(contextId = "playlist-1", index = 1, songId = "track-2", title = "第二首"),
            onlineItem(contextId = "playlist-1", index = 2, songId = "track-3", title = "第三首")
        )

        runtime.applyExternalQueueSelection(
            items = queue,
            activeIndex = 1
        )
        runtime.removePlaylistItem(1)

        val state = runtime.uiStateFlow.value
        assertEquals(listOf("第一首", "第三首"), state.playlistItems.map { it.displayName })
        assertEquals(1, state.activePlaylistIndex)
        assertEquals("第三首", state.currentTrackTitle)
    }

    @Test
    fun updatePlaylistItemsMetadata_shouldRefreshActiveArtworkWithoutChangingQueueOrder() {
        val runtime = PlayerRuntime(appContext = RuntimeEnvironment.getApplication())
        val queue = listOf(
            onlineItem(contextId = "album-1", index = 0, songId = "track-1", title = "第一首")
                .copy(coverUrl = null, primaryArtistId = null),
            onlineItem(contextId = "album-1", index = 1, songId = "track-2", title = "第二首")
                .copy(coverUrl = null, primaryArtistId = null)
        )

        runtime.applyExternalQueueSelection(
            items = queue,
            activeIndex = 1
        )
        runtime.updatePlaylistItemsMetadata(
            mapOf(
                "artist:album-1:1:track-2" to queue[1].copy(
                    title = "第二首完整版",
                    coverUrl = "https://example.com/track-2.jpg",
                    primaryArtistId = "artist-6452"
                )
            )
        )

        val state = runtime.uiStateFlow.value
        assertEquals(
            listOf("artist:album-1:0:track-1", "artist:album-1:1:track-2"),
            state.playlistItems.map { it.id }
        )
        assertEquals(1, state.activePlaylistIndex)
        assertEquals("第二首完整版", state.currentTrackTitle)
        assertEquals("https://example.com/track-2.jpg", state.currentCoverUrl)
        assertEquals("artist-6452", state.currentArtistId)
    }

    private fun onlineItem(
        contextId: String,
        index: Int,
        songId: String,
        title: String
    ): PlaylistItem {
        return PlaylistItem(
            id = "artist:$contextId:$index:$songId",
            displayName = title,
            songId = songId,
            title = title,
            artistText = "周杰伦",
            primaryArtistId = "artist-6452",
            albumTitle = "热门歌曲",
            coverUrl = "http://example.com/$songId.jpg",
            durationMs = 200_000L,
            itemType = PlaylistItemType.ONLINE,
            contextType = "artist",
            contextId = contextId,
            contextTitle = "周杰伦"
        )
    }
}
