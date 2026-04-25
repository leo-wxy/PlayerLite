package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayerRuntimeProgressRegressionTest {
    @Test
    fun updateRemotePlaybackState_shouldIgnoreMinorBackwardProgressDriftForSamePlayingTrack() {
        var elapsedRealtimeMs = 10_000L
        val runtime = PlayerRuntime(
            appContext = RuntimeEnvironment.getApplication(),
            elapsedRealtimeProvider = { elapsedRealtimeMs }
        )
        val queueItem = onlineItem(
            contextId = "playlist-1",
            index = 0,
            songId = "track-1",
            title = "第一首"
        )
        runtime.applyExternalQueueSelection(
            items = listOf(queueItem),
            activeIndex = 0
        )

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            positionMs = 4_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = queueItem.id,
            isProgressAdvancing = true,
            currentPlayable = queueItem.toPlayableSnapshot(),
            playbackOutputInfo = null,
            audioMeta = null
        )

        elapsedRealtimeMs += 350L
        runtime.tickRemotePlaybackPosition()
        assertEquals(4_350L, runtime.uiStateFlow.value.displayedSeekMs)

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            positionMs = 4_150L,
            durationMs = 200_000L,
            isSeekSupported = true,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = queueItem.id,
            isProgressAdvancing = true,
            currentPlayable = queueItem.toPlayableSnapshot(),
            playbackOutputInfo = null,
            audioMeta = null
        )

        assertEquals(4_350L, runtime.uiStateFlow.value.displayedSeekMs)
    }

    @Test
    fun updateRemotePlaybackState_shouldAcceptLargeBackwardProgressJumpForSamePlayingTrack() {
        var elapsedRealtimeMs = 20_000L
        val runtime = PlayerRuntime(
            appContext = RuntimeEnvironment.getApplication(),
            elapsedRealtimeProvider = { elapsedRealtimeMs }
        )
        val queueItem = onlineItem(
            contextId = "playlist-2",
            index = 0,
            songId = "track-2",
            title = "第二首"
        )
        runtime.applyExternalQueueSelection(
            items = listOf(queueItem),
            activeIndex = 0
        )

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            positionMs = 8_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = queueItem.id,
            isProgressAdvancing = true,
            currentPlayable = queueItem.toPlayableSnapshot(),
            playbackOutputInfo = null,
            audioMeta = null
        )

        elapsedRealtimeMs += 500L
        runtime.tickRemotePlaybackPosition()
        assertEquals(8_500L, runtime.uiStateFlow.value.displayedSeekMs)

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            positionMs = 6_800L,
            durationMs = 200_000L,
            isSeekSupported = true,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = queueItem.id,
            isProgressAdvancing = true,
            currentPlayable = queueItem.toPlayableSnapshot(),
            playbackOutputInfo = null,
            audioMeta = null
        )

        assertEquals(6_800L, runtime.uiStateFlow.value.displayedSeekMs)
    }

    @Test
    fun updateRemotePlaybackState_shouldKeepProjectedPositionWhenSameTrackReentersPreparingAtZero() {
        val runtime = PlayerRuntime(
            appContext = RuntimeEnvironment.getApplication(),
            elapsedRealtimeProvider = { 30_000L }
        )
        val queueItem = onlineItem(
            contextId = "playlist-3",
            index = 0,
            songId = "track-3",
            title = "第三首"
        )
        runtime.applyExternalQueueSelection(
            items = listOf(queueItem),
            activeIndex = 0
        )

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            positionMs = 12_345L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = queueItem.id,
            isProgressAdvancing = true,
            currentPlayable = queueItem.toPlayableSnapshot(),
            playbackOutputInfo = null,
            audioMeta = null
        )

        runtime.updateRemotePlaybackState(
            playbackState = com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED,
            positionMs = 0L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = true,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = queueItem.id,
            isProgressAdvancing = false,
            currentPlayable = queueItem.toPlayableSnapshot(),
            playbackOutputInfo = null,
            audioMeta = null
        )

        assertEquals(12_345L, runtime.uiStateFlow.value.displayedSeekMs)
        assertEquals("第三首", runtime.uiStateFlow.value.currentTrackTitle)
    }

    @Test
    fun updateRemotePlaybackState_whenPreparingAfterSeek_shouldFreezeAtSeekTarget() {
        var elapsedRealtimeMs = 40_000L
        val runtime = PlayerRuntime(
            appContext = RuntimeEnvironment.getApplication(),
            elapsedRealtimeProvider = { elapsedRealtimeMs }
        )
        val queueItem = onlineItem(
            contextId = "playlist-4",
            index = 0,
            songId = "track-4",
            title = "第四首"
        )
        runtime.applyExternalQueueSelection(
            items = listOf(queueItem),
            activeIndex = 0
        )

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            positionMs = 40_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = false,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = queueItem.id,
            isProgressAdvancing = true,
            currentPlayable = queueItem.toPlayableSnapshot(),
            playbackOutputInfo = null,
            audioMeta = null
        )

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            positionMs = 90_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = true,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = queueItem.id,
            isProgressAdvancing = true,
            currentPlayable = queueItem.toPlayableSnapshot(),
            playbackOutputInfo = null,
            audioMeta = null
        )

        elapsedRealtimeMs += 1_000L
        runtime.tickRemotePlaybackPosition()
        assertEquals(90_000L, runtime.uiStateFlow.value.displayedSeekMs)

        runtime.updateRemotePlaybackState(
            playbackState = AUDIO_TRACK_PLAYSTATE_PLAYING,
            positionMs = 85_000L,
            durationMs = 200_000L,
            isSeekSupported = true,
            isPreparing = true,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            currentMediaId = queueItem.id,
            isProgressAdvancing = true,
            currentPlayable = queueItem.toPlayableSnapshot(),
            playbackOutputInfo = null,
            audioMeta = null
        )

        assertEquals(90_000L, runtime.uiStateFlow.value.displayedSeekMs)
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

    private fun PlaylistItem.toPlayableSnapshot(): PlayableItemSnapshot {
        return PlayableItemSnapshot(
            id = id,
            songId = songId,
            title = effectiveTitle,
            artistText = artistText,
            albumTitle = albumTitle,
            coverUrl = coverUrl,
            durationMs = durationMs,
            playbackUri = "https://example.com/${songId.orEmpty()}.mp3"
        )
    }
}
