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
import com.wxy.playerlite.playback.model.PlayableItem
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.model.PlaybackSessionCommands

@UnstableApi
class PlayerServiceBridge(
    context: Context,
    private val serviceClass: Class<*>,
    private val onControllerError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

    private var released = false
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()
    private var pendingQueueSyncRequest: PendingQueueSyncRequest? = null
    private var pendingPlaybackModeUpdate: PlaybackMode? = null

    fun prewarmConnection() {
        connectIfNeeded()
    }

    fun ensurePlaybackServiceStartedForPlayback() {
        val intent = Intent(appContext, serviceClass)
        runCatching {
            ContextCompat.startForegroundService(appContext, intent)
        }.onFailure {
            onControllerError("Playback service start failed: ${it.message ?: "unknown"}")
        }
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
            ComponentName(appContext, serviceClass)
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
                        if (
                            pendingActions.isNotEmpty() ||
                            pendingQueueSyncRequest != null ||
                            pendingPlaybackModeUpdate != null
                        ) {
                            connectIfNeeded()
                        }
                    }
                }.onFailure {
                    pendingActions.clear()
                    pendingQueueSyncRequest = null
                    pendingPlaybackModeUpdate = null
                    onControllerError("MediaController connect failed: ${it.message ?: "unknown"}")
                }
            },
            mainExecutor
        )
    }

    fun syncQueue(
        queue: List<PlayableItem>,
        activeIndex: Int,
        playWhenReady: Boolean,
        startPositionMs: Long = C.TIME_UNSET
    ): Boolean {
        val request = PendingQueueSyncRequest(
            queue = queue,
            activeIndex = activeIndex,
            playWhenReady = playWhenReady,
            startPositionMs = startPositionMs
        )
        val activeController = controller
        if (activeController == null || !isConnected(activeController)) {
            if (released) {
                return false
            }
            if (activeController != null) {
                runCatching { activeController.release() }
                controller = null
            }
            pendingQueueSyncRequest = request
            connectIfNeeded()
            safeLogD("Controller unavailable; queue sync deferred")
            return true
        }

        return runCatching {
            performQueueSync(activeController, request)
        }.fold(
            onSuccess = { true },
            onFailure = {
                onControllerError("MediaController command failed: ${it.message ?: "unknown"}")
                false
            }
        )
    }

    private fun performQueueSync(
        activeController: MediaController,
        request: PendingQueueSyncRequest
    ) {
        if (request.queue.isEmpty()) {
            activeController.stop()
            activeController.clearMediaItems()
            return
        }

        val mediaItems = request.queue.map { it.toMediaItem() }
        val normalizedIndex = request.activeIndex.coerceIn(0, mediaItems.lastIndex)
        val shouldReplaceQueue = QueueSyncPlanResolver.shouldReplaceQueue(
            currentMediaIds = currentMediaIds(activeController),
            currentIndex = activeController.currentMediaItemIndex,
            currentMediaId = activeController.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() },
            requestedMediaIds = mediaItems.map { it.mediaId },
            requestedIndex = normalizedIndex
        )

        if (shouldReplaceQueue) {
            activeController.setMediaItems(
                mediaItems,
                normalizedIndex,
                request.startPositionMs
            )
            activeController.prepare()
        }

        when {
            request.playWhenReady -> {
                if (shouldReplaceQueue || !activeController.isPlaying) {
                    activeController.play()
                }
            }

            shouldReplaceQueue -> {
                activeController.pause()
            }
        }
    }

    fun playQueue(queue: List<PlayableItem>, activeIndex: Int): Boolean {
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

    fun seekToNextMediaItem(): Boolean {
        return withController { it.seekToNextMediaItem() }
    }

    fun seekToPreviousMediaItem(): Boolean {
        return withController { it.seekToPreviousMediaItem() }
    }

    fun stop(): Boolean {
        return withController { it.stop() }
    }

    fun clearCache(): Boolean {
        return withController { activeController ->
            val future = activeController.sendCustomCommand(
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

    fun setPlaybackSpeed(speed: Float, onResult: ((Boolean) -> Unit)? = null): Boolean {
        val args = Bundle().apply {
            putFloat(PlaybackSessionCommands.EXTRA_PLAYBACK_SPEED, speed)
        }
        return withController { activeController ->
            val future = activeController.sendCustomCommand(
                SessionCommand(PlaybackSessionCommands.ACTION_SET_PLAYBACK_SPEED, Bundle.EMPTY),
                args
            )
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { result ->
                            if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                                onControllerError("Set speed rejected: ${result.resultCode}")
                                onResult?.invoke(false)
                            } else {
                                onResult?.invoke(true)
                            }
                        }
                        .onFailure { error ->
                            onControllerError(
                                "Set speed failed: ${error.message ?: "unknown"}"
                            )
                            onResult?.invoke(false)
                        }
                },
                mainExecutor
            )
        }
    }

    fun setPlaybackMode(playbackMode: PlaybackMode, onResult: ((Boolean) -> Unit)? = null): Boolean {
        val args = Bundle().apply {
            putString(PlaybackSessionCommands.EXTRA_PLAYBACK_MODE, playbackMode.wireValue)
        }
        val activeController = controller
        if (activeController == null || !isConnected(activeController)) {
            if (released) {
                return false
            }
            if (activeController != null) {
                runCatching { activeController.release() }
                controller = null
            }
            pendingPlaybackModeUpdate = playbackMode
            connectIfNeeded()
            onResult?.invoke(true)
            safeLogD("Controller unavailable; playback mode update deferred")
            return true
        }
        return withController { resolvedController ->
            val future = resolvedController.sendCustomCommand(
                SessionCommand(PlaybackSessionCommands.ACTION_SET_PLAYBACK_MODE, Bundle.EMPTY),
                args
            )
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { result ->
                            if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                                onControllerError("Set mode rejected: ${result.resultCode}")
                                onResult?.invoke(false)
                            } else {
                                onResult?.invoke(true)
                            }
                        }
                        .onFailure { error ->
                            onControllerError(
                                "Set mode failed: ${error.message ?: "unknown"}"
                            )
                            onResult?.invoke(false)
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
        val currentMetadataExtras = currentMetadata?.extras
        val rootMetadataExtras = activeController.mediaMetadata.extras
        val currentPlayable = activeController.currentMediaItem?.let(PlayableItemSnapshot::fromMediaItem)
        val seekSupported = PlaybackMetadataExtras.readSeekSupported(currentMetadataExtras)
            ?: PlaybackMetadataExtras.readSeekSupported(sessionExtras)
            ?: PlaybackMetadataExtras.readSeekSupported(rootMetadataExtras)
            ?: false
        val statusText = PlaybackMetadataExtras.readStatusText(sessionExtras)
            ?: currentMetadata?.subtitle?.toString()
            ?: activeController.mediaMetadata.subtitle?.toString()

        return RemotePlaybackSnapshotMapper.map(
            playbackState = activeController.playbackState,
            playWhenReady = activeController.playWhenReady,
            isPlaying = activeController.isPlaying,
            isSeekSupported = seekSupported,
            currentPositionMs = activeController.currentPosition,
            durationMs = activeController.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            playbackParametersSpeed = activeController.playbackParameters.speed,
            currentMetadataExtras = currentMetadataExtras,
            sessionExtras = sessionExtras,
            rootMetadataExtras = rootMetadataExtras,
            currentPlayable = currentPlayable,
            currentMediaId = activeController.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() },
            statusText = statusText
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
        if (!isConnected(activeController)) {
            return
        }
        pendingQueueSyncRequest?.let { request ->
            pendingQueueSyncRequest = null
            runCatching {
                performQueueSync(activeController, request)
            }.onFailure {
                onControllerError("MediaController command failed: ${it.message ?: "unknown"}")
            }
        }
        pendingPlaybackModeUpdate?.let { playbackMode ->
            pendingPlaybackModeUpdate = null
            runCatching {
                setPlaybackMode(playbackMode)
            }.onFailure {
                onControllerError("MediaController command failed: ${it.message ?: "unknown"}")
            }
        }
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

    private fun isConnected(activeController: MediaController): Boolean {
        return runCatching { activeController.isConnected }.getOrDefault(false)
    }

    private fun currentMediaIds(activeController: MediaController): List<String> {
        return List(activeController.mediaItemCount) { index ->
            activeController.getMediaItemAt(index).mediaId
        }
    }

    private companion object {
        private const val TAG = "PlayerServiceBridge"
    }

    private data class PendingQueueSyncRequest(
        val queue: List<PlayableItem>,
        val activeIndex: Int,
        val playWhenReady: Boolean,
        val startPositionMs: Long
    )

    private fun safeLogD(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun safeLogW(message: String) {
        runCatching { Log.w(TAG, message) }
    }
}
