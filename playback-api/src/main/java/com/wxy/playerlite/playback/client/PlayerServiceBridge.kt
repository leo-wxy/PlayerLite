package com.wxy.playerlite.playback.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.wxy.playerlite.playback.contract.PlaybackServiceContract
import com.wxy.playerlite.playback.model.PlayableItem
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.model.PlaybackPrewarmPreferences
import com.wxy.playerlite.playback.model.PlaybackSessionCommands
import com.wxy.playerlite.player.AudioEffectPreset

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
    private var pendingQueueSyncRequest: PendingQueueSyncRequest? = null
    private var pendingPlaybackModeUpdate: PlaybackMode? = null
    private var pendingDisplayMetadataUpdate: PendingDisplayMetadataUpdate? = null
    private var lastSnapshotCacheDebugSignature: String? = null
    private var snapshotListener: ((RemotePlaybackSnapshot?) -> Unit)? = null
    private val sessionListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            dispatchSnapshot(controller)
        }

        override fun onDisconnected(controller: MediaController) {
            if (this@PlayerServiceBridge.controller == controller) {
                this@PlayerServiceBridge.controller = null
            }
            releaseController(controller)
            snapshotListener?.invoke(null)
        }
    }
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            dispatchSnapshot(player as? MediaController ?: return)
        }
    }

    fun prewarmConnection() {
        connectIfNeeded()
    }

    fun ensurePlaybackServiceStartedForPlayback() {
        val componentName = resolvePlaybackServiceComponent() ?: run {
            onControllerError("Playback service component resolve failed")
            return
        }
        val intent = Intent(PlaybackServiceContract.ACTION_PLAYBACK_MEDIA_SESSION_SERVICE)
            .setComponent(componentName)
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
            releaseController(existing)
            controller = null
            safeLogW("MediaController exists but disconnected; rebuilding controller")
        }
        if (controllerFuture != null) {
            return
        }

        val componentName = resolvePlaybackServiceComponent() ?: run {
            onControllerError("Playback service component resolve failed")
            return
        }
        val token = SessionToken(appContext, componentName)
        val future = MediaController.Builder(appContext, token)
            .setListener(sessionListener)
            .buildAsync()
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
                        it.addListener(playerListener)
                        flushPendingActions(it)
                        dispatchSnapshot(it)
                    } else {
                        runCatching { it.release() }
                        controller = null
                        safeLogW("Controller future completed but controller not connected; reconnecting")
                        if (
                            pendingActions.isNotEmpty() ||
                            pendingQueueSyncRequest != null ||
                            pendingPlaybackModeUpdate != null ||
                            pendingDisplayMetadataUpdate != null
                        ) {
                            connectIfNeeded()
                        }
                    }
                }.onFailure {
                    pendingActions.clear()
                    pendingQueueSyncRequest = null
                    pendingPlaybackModeUpdate = null
                    pendingDisplayMetadataUpdate = null
                    onControllerError("MediaController connect failed: ${it.message ?: "unknown"}")
                }
            },
            mainExecutor
        )
    }

    private fun resolvePlaybackServiceComponent(): ComponentName? {
        val intent = Intent(PlaybackServiceContract.ACTION_PLAYBACK_MEDIA_SESSION_SERVICE)
            .setPackage(appContext.packageName)
        val services = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.queryIntentServices(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.queryIntentServices(intent, 0)
            }
        }.getOrElse { error ->
            safeLogW(
                "Playback service query failed: ${error.message ?: error::class.java.simpleName}"
            )
            return null
        }
        val serviceInfo = services.firstOrNull()?.serviceInfo ?: return null
        return ComponentName(serviceInfo.packageName, serviceInfo.name)
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
                releaseController(activeController)
                controller = null
            }
            // A newer queue sync supersedes stale transport commands that were queued
            // against an older controller or queue generation.
            pendingActions.clear()
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
            currentPositionMs = activeController.currentPosition,
            requestedMediaIds = mediaItems.map { it.mediaId },
            requestedIndex = normalizedIndex,
            requestedStartPositionMs = request.startPositionMs
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

            shouldReplaceQueue || activeController.isPlaying || activeController.playWhenReady -> {
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

    fun setPlaybackCacheLimitBytes(
        maxBytes: Long,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        val args = Bundle().apply {
            putLong(PlaybackSessionCommands.EXTRA_PLAYBACK_CACHE_LIMIT_BYTES, maxBytes)
        }
        return withController { activeController ->
            val future = activeController.sendCustomCommand(
                SessionCommand(
                    PlaybackSessionCommands.ACTION_SET_PLAYBACK_CACHE_LIMIT,
                    Bundle.EMPTY
                ),
                args
            )
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { result ->
                            if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                                onControllerError("Set cache limit rejected: ${result.resultCode}")
                                onResult?.invoke(false)
                            } else {
                                onResult?.invoke(true)
                            }
                        }
                        .onFailure { error ->
                            onControllerError(
                                "Set cache limit failed: ${error.message ?: "unknown"}"
                            )
                            onResult?.invoke(false)
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
                releaseController(activeController)
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

    fun setAudioEffectPreset(
        audioEffectPreset: AudioEffectPreset,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        val args = Bundle().apply {
            putString(
                PlaybackSessionCommands.EXTRA_AUDIO_EFFECT_PRESET,
                audioEffectPreset.wireValue
            )
        }
        return withController { activeController ->
            val future = activeController.sendCustomCommand(
                SessionCommand(
                    PlaybackSessionCommands.ACTION_SET_AUDIO_EFFECT_PRESET,
                    Bundle.EMPTY
                ),
                args
            )
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { result ->
                            if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                                onControllerError("Set audio effect rejected: ${result.resultCode}")
                                onResult?.invoke(false)
                            } else {
                                onResult?.invoke(true)
                            }
                        }
                        .onFailure { error ->
                            onControllerError(
                                "Set audio effect failed: ${error.message ?: "unknown"}"
                            )
                            onResult?.invoke(false)
                        }
                },
                mainExecutor
            )
        }
    }

    fun setPreferredAudioQuality(
        audioQuality: PlaybackAudioQuality,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        val args = Bundle().apply {
            putString(
                PlaybackSessionCommands.EXTRA_PREFERRED_AUDIO_QUALITY,
                audioQuality.wireValue
            )
        }
        return withController { activeController ->
            val future = activeController.sendCustomCommand(
                SessionCommand(
                    PlaybackSessionCommands.ACTION_SET_PREFERRED_AUDIO_QUALITY,
                    Bundle.EMPTY
                ),
                args
            )
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { result ->
                            if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                                onControllerError("Set preferred audio quality rejected: ${result.resultCode}")
                                onResult?.invoke(false)
                            } else {
                                onResult?.invoke(true)
                            }
                        }
                        .onFailure { error ->
                            onControllerError(
                                "Set preferred audio quality failed: ${error.message ?: "unknown"}"
                            )
                            onResult?.invoke(false)
                        }
                },
                mainExecutor
            )
        }
    }

    fun setActiveAudioSourceConfigJson(
        configJson: String?,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        val args = Bundle().apply {
            putString(PlaybackSessionCommands.EXTRA_ACTIVE_AUDIO_SOURCE_CONFIG_JSON, configJson)
        }
        return withController { activeController ->
            val future = activeController.sendCustomCommand(
                SessionCommand(
                    PlaybackSessionCommands.ACTION_SET_ACTIVE_AUDIO_SOURCE_CONFIG,
                    Bundle.EMPTY
                ),
                args
            )
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { result ->
                            if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                                onControllerError("Set audio source rejected: ${result.resultCode}")
                                onResult?.invoke(false)
                            } else {
                                onResult?.invoke(true)
                            }
                        }
                        .onFailure { error ->
                            onControllerError(
                                "Set audio source failed: ${error.message ?: "unknown"}"
                            )
                            onResult?.invoke(false)
                        }
                },
                mainExecutor
            )
        }
    }

    fun setWeakNetworkAutoRetryEnabled(
        enabled: Boolean,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        val args = Bundle().apply {
            putBoolean(PlaybackSessionCommands.EXTRA_WEAK_NETWORK_AUTO_RETRY_ENABLED, enabled)
        }
        return withController { activeController ->
            val future = activeController.sendCustomCommand(
                SessionCommand(
                    PlaybackSessionCommands.ACTION_SET_WEAK_NETWORK_AUTO_RETRY,
                    Bundle.EMPTY
                ),
                args
            )
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { result ->
                            if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                                onControllerError("Set weak network retry rejected: ${result.resultCode}")
                                onResult?.invoke(false)
                            } else {
                                onResult?.invoke(true)
                            }
                        }
                        .onFailure { error ->
                            onControllerError(
                                "Set weak network retry failed: ${error.message ?: "unknown"}"
                            )
                            onResult?.invoke(false)
                        }
                },
                mainExecutor
            )
        }
    }

    fun setCachePolicyPreferences(
        showCacheFailureNotifications: Boolean,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        val args = Bundle().apply {
            putBoolean(
                PlaybackSessionCommands.EXTRA_SHOW_CACHE_FAILURE_NOTIFICATIONS,
                showCacheFailureNotifications
            )
        }
        return withController { activeController ->
            val future = activeController.sendCustomCommand(
                SessionCommand(PlaybackSessionCommands.ACTION_SET_CACHE_POLICY, Bundle.EMPTY),
                args
            )
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { result ->
                            if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                                onControllerError("Set cache policy rejected: ${result.resultCode}")
                                onResult?.invoke(false)
                            } else {
                                onResult?.invoke(true)
                            }
                        }
                        .onFailure { error ->
                            onControllerError(
                                "Set cache policy failed: ${error.message ?: "unknown"}"
                            )
                            onResult?.invoke(false)
                        }
                },
                mainExecutor
            )
        }
    }

    fun setPlaybackPrewarmPreferences(
        preferences: PlaybackPrewarmPreferences,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        val sanitized = preferences.sanitized()
        val args = Bundle().apply {
            putBoolean(
                PlaybackSessionCommands.EXTRA_PLAYBACK_PREWARM_ENABLED,
                sanitized.enabled
            )
            putLong(
                PlaybackSessionCommands.EXTRA_PLAYBACK_PREWARM_BUDGET_DURATION_MS,
                sanitized.budgetDurationMs
            )
            putLong(
                PlaybackSessionCommands.EXTRA_PLAYBACK_PREWARM_BUDGET_BYTES,
                sanitized.budgetBytes
            )
            putLong(
                PlaybackSessionCommands.EXTRA_PLAYBACK_PREWARM_READY_THRESHOLD_DURATION_MS,
                sanitized.readyThresholdDurationMs
            )
            putLong(
                PlaybackSessionCommands.EXTRA_PLAYBACK_PREWARM_READY_THRESHOLD_BYTES,
                sanitized.readyThresholdBytes
            )
        }
        return withController { activeController ->
            val future = activeController.sendCustomCommand(
                SessionCommand(PlaybackSessionCommands.ACTION_SET_PLAYBACK_PREWARM, Bundle.EMPTY),
                args
            )
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { result ->
                            if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                                onControllerError("Set playback prewarm rejected: ${result.resultCode}")
                                onResult?.invoke(false)
                            } else {
                                onResult?.invoke(true)
                            }
                        }
                        .onFailure { error ->
                            onControllerError(
                                "Set playback prewarm failed: ${error.message ?: "unknown"}"
                            )
                            onResult?.invoke(false)
                        }
                },
                mainExecutor
            )
        }
    }

    fun setDisplayMetadata(title: String?, subtitle: String?): Boolean {
        val args = Bundle().apply {
            putString(PlaybackSessionCommands.EXTRA_DISPLAY_TITLE, title)
            putString(PlaybackSessionCommands.EXTRA_DISPLAY_SUBTITLE, subtitle)
        }
        val activeController = controller
        if (activeController == null || !isConnected(activeController)) {
            if (released) {
                return false
            }
            if (activeController != null) {
                releaseController(activeController)
                controller = null
            }
            pendingDisplayMetadataUpdate = PendingDisplayMetadataUpdate(
                title = title,
                subtitle = subtitle
            )
            connectIfNeeded()
            safeLogD("Controller unavailable; display metadata update deferred")
            return true
        }
        return withController { resolvedController ->
            val future = resolvedController.sendCustomCommand(
                SessionCommand(PlaybackSessionCommands.ACTION_SET_DISPLAY_METADATA, Bundle.EMPTY),
                args
            )
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { result ->
                            if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                                onControllerError("Set display metadata rejected: ${result.resultCode}")
                            }
                        }
                        .onFailure { error ->
                            onControllerError(
                                "Set display metadata failed: ${error.message ?: "unknown"}"
                            )
                        }
                },
                mainExecutor
            )
        }
    }

    fun currentSnapshot(): RemotePlaybackSnapshot? {
        val activeController = controller ?: return null
        return currentSnapshot(activeController)
    }

    private fun currentSnapshot(activeController: MediaController): RemotePlaybackSnapshot? {
        if (!isConnected(activeController)) {
            return null
        }
        val sessionExtras = activeController.sessionExtras
        val currentMetadata = activeController.currentMediaItem?.mediaMetadata
        val currentMetadataExtras = currentMetadata?.extras
        val rootMetadataExtras = activeController.mediaMetadata.extras
        val currentPlayable = activeController.currentMediaItem?.let(PlayableItemSnapshot::fromMediaItem)
        val currentMetadataCacheProgress = PlaybackMetadataExtras.readCacheProgress(currentMetadataExtras)
        val sessionCacheProgress = PlaybackMetadataExtras.readCacheProgress(sessionExtras)
        val rootMetadataCacheProgress = PlaybackMetadataExtras.readCacheProgress(rootMetadataExtras)
        val preferredCacheProgress = RemotePlaybackSnapshotMapper.readPreferredCacheProgress(
            currentMetadataExtras = currentMetadataExtras,
            sessionExtras = sessionExtras,
            rootMetadataExtras = rootMetadataExtras
        )
        val selectedCacheProgressSource = when {
            preferredCacheProgress == sessionCacheProgress && sessionCacheProgress != null -> "sessionExtras"
            preferredCacheProgress == currentMetadataCacheProgress && currentMetadataCacheProgress != null -> "currentMetadata"
            preferredCacheProgress == rootMetadataCacheProgress && rootMetadataCacheProgress != null -> "rootMetadata"
            else -> "none"
        }
        emitSnapshotCacheDebugLog(
            buildString {
                append("currentSnapshot cacheProgress: mediaId=")
                append(activeController.currentMediaItem?.mediaId ?: "<none>")
                append(", source=")
                append(selectedCacheProgressSource)
                append(", current=")
                append(describeCacheProgress(currentMetadataCacheProgress))
                append(", session=")
                append(describeCacheProgress(sessionCacheProgress))
                append(", root=")
                append(describeCacheProgress(rootMetadataCacheProgress))
            }
        )
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
            bufferedPositionMs = activeController.bufferedPosition.takeIf { it != C.TIME_UNSET } ?: 0L,
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

        controller?.let(::releaseController)
        controller = null
        snapshotListener = null
    }

    fun setSnapshotListener(listener: ((RemotePlaybackSnapshot?) -> Unit)?) {
        snapshotListener = listener
        val activeController = controller ?: return
        if (listener != null) {
            listener(currentSnapshot(activeController))
        }
    }

    private fun withController(action: (MediaController) -> Unit): Boolean {
        val activeController = controller
        if (activeController == null || !isConnected(activeController)) {
            if (released) {
                return false
            }
            if (activeController != null) {
                releaseController(activeController)
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
        pendingDisplayMetadataUpdate?.let { update ->
            pendingDisplayMetadataUpdate = null
            runCatching {
                setDisplayMetadata(update.title, update.subtitle)
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

    private fun dispatchSnapshot(activeController: MediaController) {
        snapshotListener?.invoke(currentSnapshot(activeController))
    }

    private fun releaseController(activeController: MediaController) {
        runCatching { activeController.removeListener(playerListener) }
        runCatching { activeController.release() }
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

    private data class PendingDisplayMetadataUpdate(
        val title: String?,
        val subtitle: String?
    )

    private fun safeLogD(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun safeLogW(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private fun emitSnapshotCacheDebugLog(message: String) {
        if (lastSnapshotCacheDebugSignature == message) {
            return
        }
        lastSnapshotCacheDebugSignature = message
        safeLogD(message)
    }

    private fun describeCacheProgress(
        cacheProgress: com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot?
    ): String {
        if (cacheProgress == null) {
            return "<null>"
        }
        return "cached=${cacheProgress.cachedBytes},total=${cacheProgress.totalBytes ?: -1L},ratio=${cacheProgress.normalizedDisplayRatio},full=${cacheProgress.isFullyCached},estimated=${cacheProgress.isEstimated}"
    }
}
