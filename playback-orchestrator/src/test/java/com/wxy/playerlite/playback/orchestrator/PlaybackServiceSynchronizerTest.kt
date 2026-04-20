package com.wxy.playerlite.playback.orchestrator

import androidx.media3.common.C
import androidx.media3.common.Player
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.playback.client.RemotePlaybackSnapshot
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.playback.model.PlayableItem
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.model.PlaybackSourceContext
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.PlaybackOutputInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServiceSynchronizerTest {
    @Test
    fun syncQueueToPlaybackProcess_shouldProjectQueueAndApplyAuthHeaders() {
        val runtime = FakePlaybackRuntime(
            queueItems = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首"),
                onlineItem(index = 1, songId = "track-2", title = "第二首")
            ),
            activeIndex = 1,
            playbackMode = PlaybackMode.SINGLE_LOOP
        )
        val serviceController = FakePlayerServiceController(currentSnapshot = null)
        val synchronizer = PlaybackServiceSynchronizer(
            runtime = runtime,
            serviceController = serviceController,
            authHeadersProvider = { mapOf("Authorization" to "Bearer token") },
            playbackStateMapper = { 0 },
            localShouldContinuePlayback = { false }
        )

        val synced = synchronizer.syncQueueToPlaybackProcess(playWhenReady = true)

        assertTrue(synced)
        assertEquals(
            listOf(
                "ensurePlaybackServiceStartedForPlayback",
                "connectIfNeeded",
                "syncQueue(size=2,active=1,play=true,start=${C.TIME_UNSET})",
                "setPlaybackMode(single_loop)"
            ),
            serviceController.actions
        )
        val first = serviceController.lastSyncedQueue?.firstOrNull()
        require(first is MusicInfo)
        assertEquals(mapOf("Authorization" to "Bearer token"), first.requestHeaders)
        assertEquals("已同步队列并开始后台播放", runtime.reportedStatusText)
    }

    @Test
    fun ensureRemoteQueueReadyForSkip_shouldBackfillRemoteQueueWhenRemoteMediaMissing() {
        val runtime = FakePlaybackRuntime(
            queueItems = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首"),
                onlineItem(index = 1, songId = "track-2", title = "第二首")
            ),
            activeIndex = 0,
            playbackMode = PlaybackMode.LIST_LOOP
        )
        val serviceController = FakePlayerServiceController(currentSnapshot = null)
        val synchronizer = PlaybackServiceSynchronizer(
            runtime = runtime,
            serviceController = serviceController,
            playbackStateMapper = { 0 },
            localShouldContinuePlayback = { true }
        )

        val synced = synchronizer.ensureRemoteQueueReadyForSkip()

        assertTrue(synced)
        assertEquals(
            listOf(
                "ensurePlaybackServiceStartedForPlayback",
                "connectIfNeeded",
                "syncQueue(size=2,active=0,play=true,start=${C.TIME_UNSET})",
                "setPlaybackMode(list_loop)"
            ),
            serviceController.actions
        )
    }

    @Test
    fun ensureRemoteQueueReadyForSkip_shouldNoopWhenRemoteMediaAlreadyExists() {
        val runtime = FakePlaybackRuntime(
            queueItems = listOf(onlineItem(index = 0, songId = "track-1", title = "第一首")),
            activeIndex = 0,
            playbackMode = PlaybackMode.LIST_LOOP
        )
        val serviceController = FakePlayerServiceController(
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
        val synchronizer = PlaybackServiceSynchronizer(
            runtime = runtime,
            serviceController = serviceController,
            playbackStateMapper = { 0 },
            localShouldContinuePlayback = { false }
        )

        val synced = synchronizer.ensureRemoteQueueReadyForSkip()

        assertTrue(synced)
        assertTrue(serviceController.actions.isEmpty())
    }

    @Test
    fun syncRemotePlaybackState_shouldApplySnapshotToRuntime() {
        val runtime = FakePlaybackRuntime(
            queueItems = emptyList(),
            activeIndex = 0,
            playbackMode = PlaybackMode.LIST_LOOP
        )
        val snapshot = RemotePlaybackSnapshot(
            playbackState = 3,
            playWhenReady = true,
            isPlaying = true,
            isSeekSupported = true,
            currentPositionMs = 12_000L,
            durationMs = 200_000L,
            playbackSpeed = 1.25f,
            playbackMode = PlaybackMode.SINGLE_LOOP,
            audioEffectPreset = AudioEffectPreset.WARM,
            preferredAudioQuality = PlaybackAudioQuality.HIRES,
            appliedAudioQuality = PlaybackAudioQuality.LOSSLESS,
            statusText = "Playing",
            currentPlayable = PlayableItemSnapshot(
                id = "playlist:test:0:track-1",
                songId = "track-1",
                title = "第一首",
                artistText = "测试歌手",
                albumTitle = "测试专辑",
                coverUrl = "https://example.com/track-1.jpg",
                durationMs = 200_000L,
                playbackUri = "https://example.com/track-1.mp3"
            ),
            currentMediaId = "playlist:test:0:track-1",
            playbackOutputInfo = null,
            audioMeta = null
        )
        val serviceController = FakePlayerServiceController(currentSnapshot = snapshot)
        val synchronizer = PlaybackServiceSynchronizer(
            runtime = runtime,
            serviceController = serviceController,
            playbackStateMapper = { 7 },
            localShouldContinuePlayback = { false }
        )

        val applied = synchronizer.syncRemotePlaybackState()

        assertTrue(applied)
        val remoteUpdate = runtime.lastRemoteUpdate
        requireNotNull(remoteUpdate)
        assertEquals(7, remoteUpdate.playbackState)
        assertEquals(12_000L, remoteUpdate.positionMs)
        assertEquals(200_000L, remoteUpdate.durationMs)
        assertEquals(PlaybackMode.SINGLE_LOOP, remoteUpdate.playbackMode)
        assertEquals(AudioEffectPreset.WARM, remoteUpdate.audioEffectPreset)
        assertEquals(PlaybackAudioQuality.HIRES, remoteUpdate.preferredAudioQuality)
        assertEquals(PlaybackAudioQuality.LOSSLESS, remoteUpdate.appliedAudioQuality)
        assertEquals("playlist:test:0:track-1", runtime.lastSyncedActiveItemId)
        assertEquals("Playing", runtime.reportedStatusText)
    }

    @Test
    fun syncRemotePlaybackState_whenSnapshotIsRetrying_shouldProjectPreparingStateAndRetryStatus() {
        val runtime = FakePlaybackRuntime(
            queueItems = emptyList(),
            activeIndex = 0,
            playbackMode = PlaybackMode.LIST_LOOP
        )
        val snapshot = RemotePlaybackSnapshot(
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = true,
            isPlaying = false,
            isSeekSupported = true,
            currentPositionMs = 45_000L,
            durationMs = 200_000L,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            statusText = "重试中（1/2）",
            currentPlayable = PlayableItemSnapshot(
                id = "playlist:test:0:track-1",
                songId = "track-1",
                title = "第一首",
                artistText = "测试歌手",
                albumTitle = "测试专辑",
                coverUrl = "https://example.com/track-1.jpg",
                durationMs = 200_000L,
                playbackUri = "https://example.com/track-1.mp3"
            ),
            currentMediaId = "playlist:test:0:track-1",
            playbackOutputInfo = null,
            audioMeta = null
        )
        val serviceController = FakePlayerServiceController(currentSnapshot = snapshot)
        val synchronizer = PlaybackServiceSynchronizer(
            runtime = runtime,
            serviceController = serviceController,
            playbackStateMapper = { 9 },
            localShouldContinuePlayback = { false }
        )

        val applied = synchronizer.syncRemotePlaybackState()

        assertTrue(applied)
        val remoteUpdate = requireNotNull(runtime.lastRemoteUpdate)
        assertEquals(9, remoteUpdate.playbackState)
        assertEquals(45_000L, remoteUpdate.positionMs)
        assertTrue(remoteUpdate.isPreparing)
        assertFalse(remoteUpdate.isProgressAdvancing)
        assertEquals("重试中（1/2）", runtime.reportedStatusText)
    }

    @Test
    fun syncRemotePlaybackState_whenCurrentPlayableCarriesSourceContext_shouldUpdateMatchingQueueItemOnly() {
        val runtime = FakePlaybackRuntime(
            queueItems = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首"),
                onlineItem(index = 1, songId = "track-2", title = "第二首")
            ),
            activeIndex = 0,
            playbackMode = PlaybackMode.LIST_LOOP
        )
        val snapshot = RemotePlaybackSnapshot(
            playbackState = Player.STATE_READY,
            playWhenReady = true,
            isPlaying = true,
            isSeekSupported = true,
            currentPositionMs = 12_000L,
            durationMs = 200_000L,
            playbackSpeed = 1.0f,
            playbackMode = PlaybackMode.LIST_LOOP,
            statusText = "Playing",
            currentPlayable = PlayableItemSnapshot(
                id = "playlist:test:0:track-1",
                songId = "track-1",
                title = "第一首",
                artistText = "测试歌手",
                albumTitle = "测试专辑",
                coverUrl = "https://example.com/track-1.jpg",
                durationMs = 200_000L,
                playbackUri = "https://example.com/track-1.mp3",
                sourceContext = PlaybackSourceContext(
                    sourceConfigJson = """
                    {"type":"netease-compatible","baseUrl":"https://mirror.example.com/api"}
                    """.trimIndent()
                )
            ),
            currentMediaId = "playlist:test:0:track-1",
            playbackOutputInfo = null,
            audioMeta = null
        )
        val serviceController = FakePlayerServiceController(currentSnapshot = snapshot)
        val synchronizer = PlaybackServiceSynchronizer(
            runtime = runtime,
            serviceController = serviceController,
            playbackStateMapper = { 0 },
            localShouldContinuePlayback = { false }
        )

        val applied = synchronizer.syncRemotePlaybackState()

        assertTrue(applied)
        assertEquals(listOf("playlist:test:0:track-1"), runtime.metadataUpdates.keys.toList())
        assertEquals(
            """
            {"type":"netease-compatible","baseUrl":"https://mirror.example.com/api"}
            """.trimIndent(),
            runtime.metadataUpdates
                .getValue("playlist:test:0:track-1")
                .sourceContext
                ?.sourceConfigJson
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

private class FakePlaybackRuntime(
    private val queueItems: List<PlaylistItem>,
    private val activeIndex: Int,
    private val playbackMode: PlaybackMode
) : PlaybackRuntimePort {
    var reportedStatusText: String? = null
    var lastSyncedActiveItemId: String? = null
    var lastRemoteUpdate: RemoteUpdate? = null
    var lastDetailQueueReplacement: Pair<List<PlaylistItem>, Int>? = null
    var metadataUpdates: Map<String, PlaylistItem> = emptyMap()

    override fun playbackQueueItems(): List<PlaylistItem> = queueItems

    override fun playbackQueueActiveIndex(): Int = activeIndex

    override fun currentPlaybackMode(): PlaybackMode = playbackMode

    override fun playbackQueueItemsInOriginalOrder(): List<PlaylistItem> = queueItems

    override fun replaceQueueFromDetail(items: List<PlaylistItem>, activeIndex: Int) {
        lastDetailQueueReplacement = items to activeIndex
    }

    override fun updatePlaylistItemsMetadata(updatesById: Map<String, PlaylistItem>) {
        metadataUpdates = updatesById
    }

    override fun updateLocalPlaybackMode(playbackMode: PlaybackMode) = Unit

    override fun selectPlaylistItem(index: Int) = Unit

    override fun removePlaylistItem(index: Int) = Unit

    override fun clearPlaylist() = Unit

    override fun movePlaylistItem(fromIndex: Int, toIndex: Int) = Unit

    override fun updateLocalPlaybackSpeed(playbackSpeed: Float) = Unit

    override fun revertPendingPlaybackSpeed(playbackSpeed: Float) = Unit

    override fun updateLocalAudioEffectPreset(audioEffectPreset: AudioEffectPreset) = Unit

    override fun revertPendingAudioEffectPreset(audioEffectPreset: AudioEffectPreset) = Unit

    override fun updateLocalPreferredAudioQuality(audioQuality: PlaybackAudioQuality) = Unit

    override fun revertPendingPreferredAudioQuality(audioQuality: PlaybackAudioQuality) = Unit

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
        audioEffectPreset: AudioEffectPreset?,
        preferredAudioQuality: PlaybackAudioQuality?,
        appliedAudioQuality: PlaybackAudioQuality?
    ) {
        lastRemoteUpdate = RemoteUpdate(
            playbackState = playbackState,
            positionMs = positionMs,
            durationMs = durationMs,
            isSeekSupported = isSeekSupported,
            isPreparing = isPreparing,
            playbackSpeed = playbackSpeed,
            playbackMode = playbackMode,
            currentMediaId = currentMediaId,
            isProgressAdvancing = isProgressAdvancing,
            currentPlayable = currentPlayable,
            playbackOutputInfo = playbackOutputInfo,
            audioMeta = audioMeta,
            audioEffectPreset = audioEffectPreset,
            preferredAudioQuality = preferredAudioQuality,
            appliedAudioQuality = appliedAudioQuality
        )
    }

    override fun syncActiveItemById(itemId: String?) {
        lastSyncedActiveItemId = itemId
    }

    override fun setStatusText(statusText: String) {
        this.reportedStatusText = statusText
    }
}

private class FakePlayerServiceController(
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

    override fun stop(): Boolean = true

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

private data class RemoteUpdate(
    val playbackState: Int,
    val positionMs: Long,
    val durationMs: Long,
    val isSeekSupported: Boolean,
    val isPreparing: Boolean,
    val playbackSpeed: Float,
    val playbackMode: PlaybackMode,
    val currentMediaId: String?,
    val isProgressAdvancing: Boolean,
    val currentPlayable: PlayableItemSnapshot?,
    val playbackOutputInfo: PlaybackOutputInfo?,
    val audioMeta: AudioMetaDisplay?,
    val audioEffectPreset: AudioEffectPreset?,
    val preferredAudioQuality: PlaybackAudioQuality?,
    val appliedAudioQuality: PlaybackAudioQuality?
)
