package com.wxy.playerlite.playback.orchestrator

import androidx.media3.common.C
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.playback.client.RemotePlaybackSnapshot
import com.wxy.playerlite.playback.model.PlayableItem
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.PlaybackOutputInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTransportControllerTest {
    @Test
    fun skipToNextTrack_shouldBackfillRemoteQueueBeforeSeeking() {
        val runtime = FakeTransportRuntime(
            queueItems = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首"),
                onlineItem(index = 1, songId = "track-2", title = "第二首")
            ),
            activeIndex = 0,
            playbackMode = PlaybackMode.LIST_LOOP
        )
        val serviceController = FakeTransportServiceController(currentSnapshot = null)
        val synchronizer = PlaybackServiceSynchronizer(
            runtime = runtime,
            serviceController = serviceController,
            playbackStateMapper = { 0 },
            localShouldContinuePlayback = { true }
        )
        val controller = PlaybackTransportController(
            runtime = runtime,
            serviceController = serviceController,
            playbackSynchronizer = synchronizer
        )

        val accepted = controller.skipToNextTrack()

        assertTrue(accepted)
        assertEquals(
            listOf(
                "ensurePlaybackServiceStartedForPlayback",
                "connectIfNeeded",
                "syncQueue(size=2,active=0,play=true,start=${C.TIME_UNSET})",
                "setPlaybackMode(list_loop)",
                "ensurePlaybackServiceStartedForPlayback",
                "connectIfNeeded",
                "seekToNextMediaItem"
            ),
            serviceController.actions
        )
    }

    @Test
    fun seekTo_whenControllerRejected_shouldReportDisconnectedStatus() {
        val runtime = FakeTransportRuntime(
            queueItems = emptyList(),
            activeIndex = 0,
            playbackMode = PlaybackMode.LIST_LOOP
        )
        val serviceController = FakeTransportServiceController(
            currentSnapshot = null,
            seekToResult = false
        )
        val controller = PlaybackTransportController(
            runtime = runtime,
            serviceController = serviceController,
            playbackSynchronizer = PlaybackServiceSynchronizer(
                runtime = runtime,
                serviceController = serviceController,
                playbackStateMapper = { 0 },
                localShouldContinuePlayback = { false }
            )
        )

        val accepted = controller.seekTo(15_000L)

        assertEquals(false, accepted)
        assertEquals("后台播放进程未连接", runtime.reportedStatusText)
        assertEquals(listOf("connectIfNeeded", "seekTo(15000)"), serviceController.actions)
    }

    @Test
    fun clearCache_shouldPublishAcceptedStatus() {
        val runtime = FakeTransportRuntime(
            queueItems = emptyList(),
            activeIndex = 0,
            playbackMode = PlaybackMode.LIST_LOOP
        )
        val serviceController = FakeTransportServiceController(currentSnapshot = null)
        val controller = PlaybackTransportController(
            runtime = runtime,
            serviceController = serviceController,
            playbackSynchronizer = PlaybackServiceSynchronizer(
                runtime = runtime,
                serviceController = serviceController,
                playbackStateMapper = { 0 },
                localShouldContinuePlayback = { false }
            )
        )

        val accepted = controller.clearCache()

        assertTrue(accepted)
        assertEquals("已请求清理缓存", runtime.reportedStatusText)
        assertEquals(listOf("connectIfNeeded", "clearCache"), serviceController.actions)
    }

    private fun onlineItem(index: Int, songId: String, title: String): PlaylistItem {
        return PlaylistItem(
            id = "playlist:test:$index:$songId",
            displayName = title,
            songId = songId,
            title = title,
            artistText = "测试歌手",
            albumTitle = "测试专辑",
            coverUrl = "https://example.com/$songId.jpg",
            durationMs = 200_000L,
            itemType = PlaylistItemType.ONLINE,
            contextType = "playlist",
            contextId = "playlist-test",
            contextTitle = "测试歌单"
        )
    }
}

private class FakeTransportRuntime(
    private val queueItems: List<PlaylistItem>,
    private val activeIndex: Int,
    private val playbackMode: PlaybackMode
) : PlaybackRuntimePort {
    var reportedStatusText: String? = null

    override fun playbackQueueItems(): List<PlaylistItem> = queueItems

    override fun playbackQueueActiveIndex(): Int = activeIndex

    override fun currentPlaybackMode(): PlaybackMode = playbackMode

    override fun playbackQueueItemsInOriginalOrder(): List<PlaylistItem> = queueItems

    override fun replaceQueueFromDetail(items: List<PlaylistItem>, activeIndex: Int) = Unit

    override fun updatePlaylistItemsMetadata(updatesById: Map<String, PlaylistItem>) = Unit

    override fun updateLocalPlaybackMode(playbackMode: PlaybackMode) = Unit

    override fun selectPlaylistItem(index: Int) = Unit

    override fun removePlaylistItem(index: Int) = Unit

    override fun clearPlaylist() = Unit

    override fun movePlaylistItem(fromIndex: Int, toIndex: Int) = Unit

    override fun updateLocalPlaybackSpeed(playbackSpeed: Float) = Unit

    override fun revertPendingPlaybackSpeed(playbackSpeed: Float) = Unit

    override fun updateLocalAudioEffectPreset(audioEffectPreset: AudioEffectPreset) = Unit

    override fun revertPendingAudioEffectPreset(audioEffectPreset: AudioEffectPreset) = Unit

    override fun updateRemotePlaybackState(
        playbackState: Int,
        positionMs: Long,
        durationMs: Long,
        isSeekSupported: Boolean,
        isPreparing: Boolean,
        playbackSpeed: Float,
        playbackMode: PlaybackMode,
        currentMediaId: String?,
        isProgressAdvancing: Boolean,
        currentPlayable: PlayableItemSnapshot?,
        playbackOutputInfo: PlaybackOutputInfo?,
        audioMeta: AudioMetaDisplay?,
        audioEffectPreset: AudioEffectPreset?
    ) = Unit

    override fun syncActiveItemById(itemId: String?) = Unit

    override fun setStatusText(statusText: String) {
        reportedStatusText = statusText
    }
}

private class FakeTransportServiceController(
    private val currentSnapshot: RemotePlaybackSnapshot?,
    private val playResult: Boolean = true,
    private val pauseResult: Boolean = true,
    private val seekToResult: Boolean = true,
    private val seekToNextResult: Boolean = true,
    private val seekToPreviousResult: Boolean = true,
    private val stopResult: Boolean = true,
    private val clearCacheResult: Boolean = true
) : PlayerServiceController {
    val actions = mutableListOf<String>()
    var lastSyncedQueue: List<PlayableItem>? = null

    override fun prewarmConnection() = Unit

    override fun ensurePlaybackServiceStartedForPlayback() {
        actions += "ensurePlaybackServiceStartedForPlayback"
    }

    override fun connectIfNeeded() {
        actions += "connectIfNeeded"
    }

    override fun syncQueue(
        queue: List<PlayableItem>,
        activeIndex: Int,
        playWhenReady: Boolean,
        startPositionMs: Long
    ): Boolean {
        lastSyncedQueue = queue
        actions += "syncQueue(size=${queue.size},active=$activeIndex,play=$playWhenReady,start=$startPositionMs)"
        return true
    }

    override fun play(): Boolean {
        actions += "play"
        return playResult
    }

    override fun pause(): Boolean {
        actions += "pause"
        return pauseResult
    }

    override fun seekTo(positionMs: Long): Boolean {
        actions += "seekTo($positionMs)"
        return seekToResult
    }

    override fun seekToNextMediaItem(): Boolean {
        actions += "seekToNextMediaItem"
        return seekToNextResult
    }

    override fun seekToPreviousMediaItem(): Boolean {
        actions += "seekToPreviousMediaItem"
        return seekToPreviousResult
    }

    override fun stop(): Boolean {
        actions += "stop"
        return stopResult
    }

    override fun clearCache(): Boolean {
        actions += "clearCache"
        return clearCacheResult
    }

    override fun setPlaybackSpeed(speed: Float, onResult: ((Boolean) -> Unit)?): Boolean = true

    override fun setAudioEffectPreset(
        audioEffectPreset: AudioEffectPreset,
        onResult: ((Boolean) -> Unit)?
    ): Boolean = true

    override fun setPlaybackMode(playbackMode: PlaybackMode, onResult: ((Boolean) -> Unit)?): Boolean {
        actions += "setPlaybackMode(${playbackMode.name.lowercase()})"
        return true
    }

    override fun setDisplayMetadata(title: String?, subtitle: String?): Boolean = true

    override fun currentSnapshot(): RemotePlaybackSnapshot? = currentSnapshot

    override fun release() = Unit
}
