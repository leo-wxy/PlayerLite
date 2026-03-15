package com.wxy.playerlite.feature.player.runtime

import android.content.Context
import android.util.Log
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playback.SongDetailRepository
import com.wxy.playerlite.playback.client.PlayerServiceBridge
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.playback.process.PlayerMediaSessionService
import com.wxy.playerlite.user.UserRepository
import com.wxy.playerlite.user.model.toAuthHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class DetailPlaybackRequest(
    val items: List<PlaylistItem>,
    val activeIndex: Int
)

internal interface DetailPlaybackGateway : AutoCloseable {
    fun play(request: DetailPlaybackRequest): Boolean

    override fun close() = Unit
}

internal class RuntimeDetailPlaybackGateway(
    private val runtime: PlayerRuntime,
    private val userRepository: UserRepository,
    private val serviceBridge: PlayerServiceBridge,
    private val songDetailRepository: SongDetailRepository,
    private val playbackScope: CoroutineScope,
    private val metadataEnricher: PlaybackQueueMetadataEnricher = PlaybackQueueMetadataEnricher(
        repository = songDetailRepository
    )
) : DetailPlaybackGateway {
    private var enrichmentJob: Job? = null

    constructor(appContext: Context) : this(
        runtime = PlayerRuntimeRegistry.get(appContext.applicationContext),
        userRepository = AppContainer.userRepository(appContext.applicationContext),
        serviceBridge = PlayerServiceBridge(
            appContext.applicationContext,
            PlayerMediaSessionService::class.java
        ) { errorMessage ->
            PlayerRuntimeRegistry.get(appContext.applicationContext).setStatusText(errorMessage)
            Log.w(TAG, errorMessage)
        },
        songDetailRepository = AppContainer.songDetailRepository(appContext.applicationContext),
        playbackScope = PlayerRuntimeRegistry.runtimeScope
    )

    override fun play(request: DetailPlaybackRequest): Boolean {
        enrichmentJob?.cancel()
        runtime.applyExternalQueueSelection(
            items = request.items,
            activeIndex = request.activeIndex
        )
        val queueItems = runtime.playbackQueueItems()
        if (queueItems.isEmpty()) {
            runtime.setStatusText("播放失败：当前列表没有可投影的可播放条目")
            return false
        }
        val activeIndex = runtime.playbackQueueActiveIndex().takeIf { it in queueItems.indices } ?: 0
        val activeItemId = queueItems.getOrNull(activeIndex)?.id
        val authHeaders = userRepository.currentSession()?.toAuthHeaders().orEmpty()
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
        val normalizedActiveIndex = queueEntries.indexOfFirst { it.first == activeItemId }
            .takeIf { it >= 0 }
            ?: 0
        serviceBridge.ensurePlaybackServiceStartedForPlayback()
        serviceBridge.connectIfNeeded()
        val synced = serviceBridge.syncQueue(
            queue = queueEntries.map { it.second },
            activeIndex = normalizedActiveIndex,
            playWhenReady = true
        )
        if (!synced) {
            runtime.setStatusText("播放失败：后台播放进程未连接")
        } else {
            serviceBridge.setPlaybackMode(runtime.uiStateFlow.value.playbackMode)
        }
        launchMetadataEnrichment(request)
        return synced
    }

    private fun launchMetadataEnrichment(request: DetailPlaybackRequest) {
        val expectedQueueIds = request.items.map { it.id }
        enrichmentJob = playbackScope.launch {
            metadataEnricher.enrich(
                items = request.items,
                activeIndex = request.activeIndex
            ) { updates ->
                val currentQueueIds = runtime.playbackQueueItemsInOriginalOrder().map { it.id }
                if (currentQueueIds != expectedQueueIds) {
                    return@enrich
                }
                runtime.updatePlaylistItemsMetadata(updates)
            }
        }
    }

    override fun close() {
        enrichmentJob?.cancel()
        serviceBridge.release()
    }

    private companion object {
        const val TAG = "RuntimeDetailPlayback"
    }
}
