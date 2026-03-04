package com.wxy.playerlite.playback.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.playback.model.PlaybackSessionCommands
import com.wxy.playerlite.playback.process.PlayerMediaSessionService
import com.wxy.playerlite.player.PlaybackOutputInfo

data class RemotePlaybackSnapshot(
    val playbackState: Int,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val isSeekSupported: Boolean,
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
        if (released) {
            return
        }
        val existing = controller
        if (existing != null) {
            if (isConnected(existing)) {
                return
            }
            runCatching { existing.release() }
            controller = null
            safeLogW("MediaController exists but disconnected; rebuilding controller")
        }
        if (controllerFuture != null) {
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
                    if (isConnected(it)) {
                        controller = it
                        flushPendingActions(it)
                    } else {
                        runCatching { it.release() }
                        controller = null
                        safeLogW("Controller future completed but controller not connected; reconnecting")
                        if (pendingActions.isNotEmpty()) {
                            connectIfNeeded()
                        }
                    }
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

    fun clearCache(): Boolean {
        return withController { controller ->
            val future = controller.sendCustomCommand(
                SessionCommand(PlaybackSessionCommands.ACTION_CLEAR_CACHE, Bundle.EMPTY),
                Bundle.EMPTY
            )
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { result ->
                            if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                                onControllerError("Clear cache rejected: ${result.resultCode}")
                            }
                        }
                        .onFailure { error ->
                            onControllerError(
                                "Clear cache failed: ${error.message ?: "unknown"}"
                            )
                        }
                },
                mainExecutor
            )
        }
    }

    fun currentSnapshot(): RemotePlaybackSnapshot? {
        val activeController = controller ?: return null
        val sessionExtras = activeController.sessionExtras
        val currentMetadata = activeController.currentMediaItem?.mediaMetadata
        val playbackOutputInfo = PlaybackMetadataExtras.readPlaybackOutputInfo(currentMetadata?.extras)
            ?: PlaybackMetadataExtras.readPlaybackOutputInfo(sessionExtras)
            ?: PlaybackMetadataExtras.readPlaybackOutputInfo(activeController.mediaMetadata.extras)
        val seekSupported = PlaybackMetadataExtras.readSeekSupported(currentMetadata?.extras)
            ?: PlaybackMetadataExtras.readSeekSupported(sessionExtras)
            ?: PlaybackMetadataExtras.readSeekSupported(activeController.mediaMetadata.extras)
            ?: false
        return RemotePlaybackSnapshot(
            playbackState = activeController.playbackState,
            playWhenReady = activeController.playWhenReady,
            isPlaying = activeController.isPlaying,
            isSeekSupported = seekSupported,
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
        if (activeController == null || !isConnected(activeController)) {
            if (released) {
                return false
            }
            if (activeController != null) {
                runCatching { activeController.release() }
                controller = null
            }
            pendingActions += action
            connectIfNeeded()
            safeLogD("Controller unavailable; action queued. pending=${pendingActions.size}")
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
        if (!isConnected(activeController)) {
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

    private fun isConnected(activeController: MediaController): Boolean {
        return runCatching { activeController.isConnected }.getOrDefault(false)
    }

    private companion object {
        private const val TAG = "PlayerServiceBridge"
    }

    private fun safeLogD(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun safeLogW(message: String) {
        runCatching { Log.w(TAG, message) }
    }
}
