package com.wxy.playerlite.core.playback

import android.content.Context
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackGateway
import com.wxy.playerlite.feature.player.runtime.PlayerRuntime
import com.wxy.playerlite.feature.player.runtime.PlayerRuntimeRegistry
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.playback.orchestrator.MediaControllerPlayerServiceController
import com.wxy.playerlite.playback.orchestrator.PlayerServiceController
import com.wxy.playerlite.playback.orchestrator.detail.OrchestratorDetailPlaybackGateway
import com.wxy.playerlite.playback.orchestrator.detail.PlaybackQueueMetadataRepository

internal object AppPlaybackGraph {
    fun runtime(context: Context): PlayerRuntime {
        return PlayerRuntimeRegistry.get(context.applicationContext)
    }

    fun playerServiceController(
        context: Context,
        onControllerError: (String) -> Unit
    ): PlayerServiceController {
        return MediaControllerPlayerServiceController(
            context = context.applicationContext,
            onControllerError = onControllerError
        )
    }

    fun detailPlaybackGateway(context: Context): DetailPlaybackGateway {
        val appContext = context.applicationContext
        return OrchestratorDetailPlaybackGateway(
            runtime = runtime(appContext),
            playbackScope = PlayerRuntimeRegistry.runtimeScope,
            metadataRepository = object : PlaybackQueueMetadataRepository {
                private val repository = AppContainer.songDetailRepository(appContext)

                override suspend fun fetchSongs(songIds: List<String>): List<MusicInfo> {
                    return repository.fetchSongs(songIds)
                }
            }
        )
    }
}
