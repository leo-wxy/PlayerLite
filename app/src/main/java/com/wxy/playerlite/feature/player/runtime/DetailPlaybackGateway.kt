package com.wxy.playerlite.feature.player.runtime

import android.content.Context
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playback.SongDetailRepository
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
    private val songDetailRepository: SongDetailRepository,
    private val playbackScope: CoroutineScope,
    private val metadataEnricher: PlaybackQueueMetadataEnricher = PlaybackQueueMetadataEnricher(
        repository = songDetailRepository
    )
) : DetailPlaybackGateway {
    private var enrichmentJob: Job? = null

    constructor(appContext: Context) : this(
        runtime = PlayerRuntimeRegistry.get(appContext.applicationContext),
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
        if (queueItems.none { it.toQueuePlayableItem() != null }) {
            runtime.setStatusText("播放失败：当前列表没有可投影的可播放条目")
            return false
        }
        launchMetadataEnrichment(request)
        return true
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
    }
}
