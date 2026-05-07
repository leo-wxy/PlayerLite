package com.wxy.playerlite.playback.orchestrator

import androidx.media3.common.C
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.playback.client.RemotePlaybackSnapshot
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot
import com.wxy.playerlite.playback.model.PlayableItem
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.model.PlaybackPrewarmPreferences
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.PlaybackOutputInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueControllerTest {
    @Test
    fun updatePlaybackMode_shouldApplyLocalModeAndResyncQueueFromCurrentPosition() {
        val runtime = FakeQueueRuntime(
            queueItems = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首"),
                onlineItem(index = 1, songId = "track-2", title = "第二首")
            ),
            activeIndex = 1,
            playbackMode = PlaybackMode.LIST_LOOP
        )
        val serviceController = FakeQueueServiceController(currentSnapshot = null)
        val controller = PlaybackQueueController(
            runtime = runtime,
            serviceController = serviceController,
            playbackSynchronizer = PlaybackServiceSynchronizer(
                runtime = runtime,
                serviceController = serviceController,
                playbackStateMapper = { 0 },
                localShouldContinuePlayback = { true }
            )
        )

        val synced = controller.updatePlaybackMode(
            playbackMode = PlaybackMode.SINGLE_LOOP,
            startPositionMs = 48_000L
        )

        assertTrue(synced)
        assertEquals(PlaybackMode.SINGLE_LOOP, runtime.localPlaybackMode)
        assertEquals(
            listOf(
                "ensurePlaybackServiceStartedForPlayback",
                "connectIfNeeded",
                "syncQueue(size=2,active=1,play=true,start=48000)",
                "setPlaybackMode(single_loop)"
            ),
            serviceController.actions
        )
    }

    @Test
    fun removePlaylistItem_shouldStopPlaybackWhenQueueBecomesEmpty() {
        val runtime = FakeQueueRuntime(
            queueItems = listOf(onlineItem(index = 0, songId = "track-1", title = "第一首")),
            activeIndex = 0,
            playbackMode = PlaybackMode.LIST_LOOP
        )
        val serviceController = FakeQueueServiceController(currentSnapshot = null)
        val controller = PlaybackQueueController(
            runtime = runtime,
            serviceController = serviceController,
            playbackSynchronizer = PlaybackServiceSynchronizer(
                runtime = runtime,
                serviceController = serviceController,
                playbackStateMapper = { 0 },
                localShouldContinuePlayback = { false }
            )
        )

        val handled = controller.removePlaylistItem(
            index = 0,
            removedActiveWhilePlayingOrPreparing = true
        )

        assertTrue(handled)
        assertEquals(emptyList<PlaylistItem>(), runtime.queueItems)
        assertEquals(listOf("stop"), serviceController.actions)
    }

    @Test
    fun movePlaylistItem_shouldResyncQueueWithCurrentPlaybackIntent() {
        val runtime = FakeQueueRuntime(
            queueItems = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首"),
                onlineItem(index = 1, songId = "track-2", title = "第二首")
            ),
            activeIndex = 0,
            playbackMode = PlaybackMode.LIST_LOOP
        )
        val serviceController = FakeQueueServiceController(
            currentSnapshot = RemotePlaybackSnapshot(
                playbackState = 3,
                playWhenReady = true,
                isPlaying = true,
                isSeekSupported = true,
                currentPositionMs = 0L,
                durationMs = 200_000L,
                playbackSpeed = 1.0f,
                playbackMode = PlaybackMode.LIST_LOOP,
                statusText = null,
                currentPlayable = null,
                currentMediaId = "playlist:test:0:track-1",
                playbackOutputInfo = null,
                audioMeta = null
            )
        )
        val controller = PlaybackQueueController(
            runtime = runtime,
            serviceController = serviceController,
            playbackSynchronizer = PlaybackServiceSynchronizer(
                runtime = runtime,
                serviceController = serviceController,
                playbackStateMapper = { 0 },
                localShouldContinuePlayback = { false }
            )
        )

        val synced = controller.movePlaylistItem(fromIndex = 0, toIndex = 1)

        assertTrue(synced)
        assertEquals(listOf("第二首", "第一首"), runtime.queueItems.map { it.displayName })
        assertEquals(
            listOf(
                "ensurePlaybackServiceStartedForPlayback",
                "connectIfNeeded",
                "syncQueue(size=2,active=1,play=true,start=${C.TIME_UNSET})",
                "setPlaybackMode(list_loop)"
            ),
            serviceController.actions
        )
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

private class FakeQueueRuntime(
    queueItems: List<PlaylistItem>,
    activeIndex: Int,
    playbackMode: PlaybackMode
) : PlaybackRuntimePort {
    var queueItems: List<PlaylistItem> = queueItems
    var activeIndex: Int = activeIndex
    var localPlaybackMode: PlaybackMode = playbackMode

    override fun playbackQueueItems(): List<PlaylistItem> = queueItems

    override fun playbackQueueActiveIndex(): Int = activeIndex

    override fun currentPlaybackMode(): PlaybackMode = localPlaybackMode

    override fun playbackQueueItemsInOriginalOrder(): List<PlaylistItem> = queueItems

    override fun replaceQueueFromDetail(items: List<PlaylistItem>, activeIndex: Int) {
        queueItems = items
        this.activeIndex = activeIndex
    }

    override fun updatePlaylistItemsMetadata(updatesById: Map<String, PlaylistItem>) = Unit

    override fun updateLocalPlaybackMode(playbackMode: PlaybackMode) {
        localPlaybackMode = playbackMode
    }

    override fun selectPlaylistItem(index: Int) {
        activeIndex = index
    }

    override fun removePlaylistItem(index: Int) {
        if (index !in queueItems.indices) {
            return
        }
        queueItems = queueItems.toMutableList().also { it.removeAt(index) }
        if (queueItems.isEmpty()) {
            activeIndex = 0
        } else if (activeIndex >= queueItems.size) {
            activeIndex = queueItems.lastIndex
        } else if (index < activeIndex) {
            activeIndex -= 1
        }
    }

    override fun clearPlaylist() {
        queueItems = emptyList()
        activeIndex = 0
    }

    override fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in queueItems.indices || toIndex !in queueItems.indices) {
            return
        }
        val moved = queueItems.toMutableList()
        val item = moved.removeAt(fromIndex)
        moved.add(toIndex, item)
        queueItems = moved
        if (activeIndex == fromIndex) {
            activeIndex = toIndex
        } else if (fromIndex < activeIndex && toIndex >= activeIndex) {
            activeIndex -= 1
        } else if (fromIndex > activeIndex && toIndex <= activeIndex) {
            activeIndex += 1
        }
    }

    override fun updateLocalPlaybackSpeed(playbackSpeed: Float) = Unit

    override fun revertPendingPlaybackSpeed(playbackSpeed: Float) = Unit

    override fun updateLocalAudioEffectPreset(audioEffectPreset: AudioEffectPreset) = Unit

    override fun revertPendingAudioEffectPreset(audioEffectPreset: AudioEffectPreset) = Unit

    override fun updateLocalPreferredAudioQuality(audioQuality: PlaybackAudioQuality) = Unit

    override fun revertPendingPreferredAudioQuality(audioQuality: PlaybackAudioQuality) = Unit

    override fun updateRemotePlaybackState(
        playbackState: Int,
        positionMs: Long,
        bufferedPositionMs: Long,
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
        audioEffectPreset: AudioEffectPreset?,
        preferredAudioQuality: PlaybackAudioQuality?,
        appliedAudioQuality: PlaybackAudioQuality?,
        cacheProgress: PlaybackCacheProgressSnapshot?
    ) = Unit

    override fun syncActiveItemById(itemId: String?) = Unit

    override fun setStatusText(statusText: String) = Unit
}

private class FakeQueueServiceController(
    private val currentSnapshot: RemotePlaybackSnapshot?
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

    override fun play(): Boolean = true

    override fun pause(): Boolean = true

    override fun seekTo(positionMs: Long): Boolean = true

    override fun seekToNextMediaItem(): Boolean = true

    override fun seekToPreviousMediaItem(): Boolean = true

    override fun stop(): Boolean {
        actions += "stop"
        return true
    }

    override fun clearCache(): Boolean = true

    override fun setPlaybackCacheLimitBytes(maxBytes: Long, onResult: ((Boolean) -> Unit)?): Boolean = true

    override fun setPlaybackSpeed(speed: Float, onResult: ((Boolean) -> Unit)?): Boolean = true

    override fun setAudioEffectPreset(
        audioEffectPreset: AudioEffectPreset,
        onResult: ((Boolean) -> Unit)?
    ): Boolean = true

    override fun setPreferredAudioQuality(
        audioQuality: PlaybackAudioQuality,
        onResult: ((Boolean) -> Unit)?
    ): Boolean = true

    override fun setWeakNetworkAutoRetryEnabled(
        enabled: Boolean,
        onResult: ((Boolean) -> Unit)?
    ): Boolean = true

    override fun setCachePolicyPreferences(
        showCacheFailureNotifications: Boolean,
        onResult: ((Boolean) -> Unit)?
    ): Boolean = true

    override fun setPlaybackPrewarmPreferences(
        preferences: PlaybackPrewarmPreferences,
        onResult: ((Boolean) -> Unit)?
    ): Boolean = true

    override fun setActiveAudioSourceConfigJson(
        configJson: String?,
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
