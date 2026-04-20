package com.wxy.playerlite.playback.orchestrator

import androidx.media3.common.C
import androidx.media3.common.Player
import com.wxy.playerlite.playback.client.RemotePlaybackSnapshot
import com.wxy.playerlite.playback.model.MusicInfo

class PlaybackServiceSynchronizer(
    private val runtime: PlaybackRuntimePort,
    private val serviceController: PlayerServiceController,
    private val authHeadersProvider: () -> Map<String, String> = { emptyMap() },
    private val playbackStateMapper: (RemotePlaybackSnapshot) -> Int,
    private val localShouldContinuePlayback: () -> Boolean
) {
    fun syncRemotePlaybackState(): Boolean {
        val snapshot = serviceController.currentSnapshot() ?: return false
        runtime.updateRemotePlaybackState(
            playbackState = playbackStateMapper(snapshot),
            positionMs = snapshot.currentPositionMs,
            durationMs = snapshot.durationMs,
            isSeekSupported = snapshot.isSeekSupported,
            isPreparing = snapshot.playbackState == Player.STATE_BUFFERING,
            playbackSpeed = snapshot.playbackSpeed,
            playbackMode = snapshot.playbackMode,
            currentMediaId = snapshot.currentMediaId,
            isProgressAdvancing = snapshot.isPlaying,
            currentPlayable = snapshot.currentPlayable,
            playbackOutputInfo = snapshot.playbackOutputInfo,
            audioMeta = snapshot.audioMeta,
            audioEffectPreset = snapshot.audioEffectPreset,
            preferredAudioQuality = snapshot.preferredAudioQuality,
            appliedAudioQuality = snapshot.appliedAudioQuality
        )
        runtime.syncActiveItemById(snapshot.currentMediaId)
        syncCurrentPlayableSourceContext(snapshot)
        snapshot.statusText
            ?.takeIf { it.isNotBlank() }
            ?.let(runtime::setStatusText)
        return true
    }

    private fun syncCurrentPlayableSourceContext(snapshot: RemotePlaybackSnapshot) {
        val currentPlayable = snapshot.currentPlayable ?: return
        val sourceContext = currentPlayable.sourceContext ?: return
        val matchedItem = runtime.playbackQueueItems().firstOrNull { item ->
            item.id == snapshot.currentMediaId || item.id == currentPlayable.id
        } ?: return
        if (matchedItem.sourceContext == sourceContext) {
            return
        }
        runtime.updatePlaylistItemsMetadata(
            mapOf(
                matchedItem.id to matchedItem.copy(sourceContext = sourceContext)
            )
        )
    }

    fun ensureRemoteQueueReadyForSkip(): Boolean {
        val snapshot = serviceController.currentSnapshot()
        if (!snapshot?.currentMediaId.isNullOrBlank()) {
            return true
        }
        if (runtime.playbackQueueItems().isEmpty()) {
            return false
        }
        val shouldContinue = shouldContinuePlayback(snapshot)
        return syncQueueToPlaybackProcess(
            playWhenReady = shouldContinue,
            requirePlaybackServiceStart = true
        )
    }

    fun shouldContinuePlayback(snapshot: RemotePlaybackSnapshot? = serviceController.currentSnapshot()): Boolean {
        if (snapshot != null) {
            return snapshot.playWhenReady || snapshot.isPlaying || snapshot.playbackState == Player.STATE_BUFFERING
        }
        return localShouldContinuePlayback()
    }

    fun syncQueueToPlaybackProcess(
        playWhenReady: Boolean,
        startPositionMs: Long = C.TIME_UNSET,
        requirePlaybackServiceStart: Boolean = playWhenReady
    ): Boolean {
        val queueItems = runtime.playbackQueueItems()
        if (queueItems.isEmpty()) {
            runtime.setStatusText("Pick audio first")
            return false
        }

        val activeIndex = runtime.playbackQueueActiveIndex().takeIf { it in queueItems.indices } ?: 0
        val activeItemId = queueItems.getOrNull(activeIndex)?.id
        val authHeaders = authHeadersProvider()
        val queueEntries = queueItems.mapNotNull { item ->
            val playable = item.toQueuePlayableItem()?.let { candidate ->
                if (candidate is MusicInfo) {
                    candidate.copy(requestHeaders = authHeaders)
                } else {
                    candidate
                }
            }
            playable?.let { item.id to it }
        }
        if (queueEntries.isEmpty()) {
            runtime.setStatusText("播放失败：当前列表没有可投影的可播放条目")
            return false
        }
        val queue = queueEntries.map { it.second }
        val normalizedActiveIndex = queueEntries.indexOfFirst { it.first == activeItemId }
            .takeIf { it >= 0 }
            ?: 0

        if (requirePlaybackServiceStart) {
            serviceController.ensurePlaybackServiceStartedForPlayback()
        }
        serviceController.connectIfNeeded()
        val synced = serviceController.syncQueue(
            queue = queue,
            activeIndex = normalizedActiveIndex,
            playWhenReady = playWhenReady,
            startPositionMs = startPositionMs
        )

        if (!synced) {
            runtime.setStatusText("播放失败：后台播放进程未连接")
        } else {
            serviceController.setPlaybackMode(runtime.currentPlaybackMode())
            runtime.setStatusText(
                if (playWhenReady) {
                    "已同步队列并开始后台播放"
                } else {
                    "已同步播放队列"
                }
            )
        }
        return synced
    }
}
