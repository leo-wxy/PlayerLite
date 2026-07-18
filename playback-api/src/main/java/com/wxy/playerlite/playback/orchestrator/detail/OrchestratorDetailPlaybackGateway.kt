package com.wxy.playerlite.playback.orchestrator.detail

import com.wxy.playerlite.feature.player.runtime.DetailPlaybackGateway
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackRequest
import com.wxy.playerlite.playback.orchestrator.PlaybackRuntimePort
import com.wxy.playerlite.playback.orchestrator.toQueuePlayableItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OrchestratorDetailPlaybackGateway(
    private val runtime: PlaybackRuntimePort,
    private val playbackScope: CoroutineScope,
    metadataRepository: PlaybackQueueMetadataRepository,
    private val metadataEnricher: PlaybackQueueMetadataEnricher = PlaybackQueueMetadataEnricher(
        repository = metadataRepository
    )
) : DetailPlaybackGateway {
    private var enrichmentJob: Job? = null

    override fun play(request: DetailPlaybackRequest): Boolean {
        enrichmentJob?.cancel()
        runtime.replaceQueueFromDetail(
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
