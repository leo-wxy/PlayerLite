package com.wxy.playerlite.playback.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.playback.process.PlayerMediaSessionService
import com.wxy.playerlite.player.PlaybackOutputInfo

data class RemotePlaybackSnapshot(
    val playbackState: Int,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val currentPositionMs: Long,
    val durationMs: Long,
    val statusText: String?,
    val currentMediaId: String?,
    val playbackOutputInfo: PlaybackOutputInfo?
)

@UnstableApi
class PlayerServiceBridge(
    context: Context,
    private val onControllerError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

    private var released = false
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()

    fun ensureServiceStarted() {
        val intent = Intent(appContext, PlayerMediaSessionService::class.java)
        appContext.startService(intent)
    }

    fun connectIfNeeded() {
        if (released || controller != null || controllerFuture != null) {
            return
        }

        val token = SessionToken(
            appContext,
            ComponentName(appContext, PlayerMediaSessionService::class.java)
        )
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                val built = runCatching { future.get() }
                controllerFuture = null
                if (released) {
                    built.getOrNull()?.release()
                    return@addListener
                }
                built.onSuccess {
                    controller = it
                    flushPendingActions(it)
                }.onFailure {
                    pendingActions.clear()
                    onControllerError("MediaController connect failed: ${it.message ?: "unknown"}")
                }
            },
            mainExecutor
        )
    }

    fun syncQueue(
        queue: List<MusicInfo>,
        activeIndex: Int,
        playWhenReady: Boolean
    ): Boolean {
        return withController { controller ->
            if (queue.isEmpty()) {
                controller.stop()
                controller.clearMediaItems()
                return@withController
            }

            val mediaItems = queue.map { it.toMediaItem() }
            val normalizedIndex = activeIndex.coerceIn(0, mediaItems.lastIndex)
            controller.setMediaItems(mediaItems, normalizedIndex, C.TIME_UNSET)
            controller.prepare()
            if (playWhenReady) {
                controller.play()
            } else {
                controller.pause()
            }
        }
    }

    fun playQueue(queue: List<MusicInfo>, activeIndex: Int): Boolean {
        return syncQueue(queue = queue, activeIndex = activeIndex, playWhenReady = true)
    }

    fun play(): Boolean {
        return withController { it.play() }
    }

    fun pause(): Boolean {
        return withController { it.pause() }
    }

    fun seekTo(positionMs: Long): Boolean {
        return withController { it.seekTo(positionMs) }
    }

    fun stop(): Boolean {
        return withController { it.stop() }
    }

    fun currentSnapshot(): RemotePlaybackSnapshot? {
        val activeController = controller ?: return null
        val sessionExtras = activeController.sessionExtras
        val currentMetadata = activeController.currentMediaItem?.mediaMetadata
        val playbackOutputInfo = PlaybackMetadataExtras.readPlaybackOutputInfo(currentMetadata?.extras)
            ?: PlaybackMetadataExtras.readPlaybackOutputInfo(sessionExtras)
            ?: PlaybackMetadataExtras.readPlaybackOutputInfo(activeController.mediaMetadata.extras)
        return RemotePlaybackSnapshot(
            playbackState = activeController.playbackState,
            playWhenReady = activeController.playWhenReady,
            isPlaying = activeController.isPlaying,
            currentPositionMs = activeController.currentPosition,
            durationMs = activeController.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            statusText = PlaybackMetadataExtras.readStatusText(sessionExtras)
                ?: currentMetadata?.subtitle?.toString()
                ?: activeController.mediaMetadata.subtitle?.toString(),
            currentMediaId = activeController.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() },
            playbackOutputInfo = playbackOutputInfo
        )
    }

    fun release() {
        released = true
        controllerFuture?.cancel(false)
        controllerFuture = null

        controller?.release()
        controller = null
    }

    private fun withController(action: (MediaController) -> Unit): Boolean {
        val activeController = controller
        if (activeController == null) {
            if (released) {
                return false
            }
            pendingActions += action
            connectIfNeeded()
            return true
        }

        return runCatching {
            action(activeController)
        }.fold(
            onSuccess = { true },
            onFailure = {
                onControllerError("MediaController command failed: ${it.message ?: "unknown"}")
                false
            }
        )
    }

    private fun flushPendingActions(activeController: MediaController) {
        if (pendingActions.isEmpty()) {
            return
        }
        val actions = pendingActions.toList()
        pendingActions.clear()
        actions.forEach { action ->
            runCatching {
                action(activeController)
            }.onFailure {
                onControllerError("MediaController command failed: ${it.message ?: "unknown"}")
            }
        }
    }
}
