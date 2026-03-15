package com.wxy.playerlite.playback.process

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import android.util.Log
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.NativePlayer
import com.wxy.playerlite.player.PlaybackSpeed
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.player.source.IPlaysource
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PlaybackProcessState(
    val tracks: List<PlaybackTrack> = emptyList(),
    val activeIndex: Int = C.INDEX_UNSET,
    val playWhenReady: Boolean = false,
    val playbackOutputInfo: PlaybackOutputInfo? = null,
    val playbackSpeed: Float = PlaybackSpeed.DEFAULT.value,
    val playbackMode: PlaybackMode = PlaybackMode.LIST_LOOP,
    val playbackState: Int = PLAYBACK_STATE_STOPPED,
    val isSeekSupported: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val audioMeta: AudioMetaDisplay? = null,
    val isPreparing: Boolean = false,
    val statusText: String = "Idle",
    val displayTitleOverride: String? = null,
    val displaySubtitleOverride: String? = null
) {
    val currentTrack: PlaybackTrack?
        get() = tracks.getOrNull(activeIndex)
}

internal class PlaybackProcessRuntime(
    appContext: Context,
    private val serviceScope: CoroutineScope
) {
    private val cacheRootDirPath = File(appContext.cacheDir, "cache_core").absolutePath
    private val onlinePlaybackPlanner = OnlinePlaybackPreparationPlanner(
        resolver = OnlinePlaybackUrlResolver(
            remoteDataSource = NeteaseOnlinePlaybackRemoteDataSource(
                httpClient = JsonHttpClient(baseUrl = API_BASE_URL)
            )
        )
    )
    private val mediaSourceRepository = MediaSourceRepository(appContext)
    private val playbackCoordinator = PlaybackCoordinator(
        player = NativePlayer(),
        scope = serviceScope
    )
    private val trackPreparationCoordinator = TrackPreparationCoordinator(
        sourceRepository = mediaSourceRepository,
        playbackCoordinator = playbackCoordinator,
        onlinePreparationPlanner = onlinePlaybackPlanner
    )
    private val sourceSession = PreparedSourceSession()
    @Volatile
    private var pendingSeekPositionMs: Long? = null
    @Volatile
    private var activePlaybackTrackId: String? = null

    private val _state = MutableStateFlow(PlaybackProcessState())
    val state: StateFlow<PlaybackProcessState> = _state.asStateFlow()

    init {
        ensureCacheCoreReady()

        playbackCoordinator.setPlaybackOutputInfoListener { outputInfo ->
            _state.value = _state.value.copy(playbackOutputInfo = outputInfo)
        }

        playbackCoordinator.setProgressListener { progressMs ->
            val base = _state.value
            val bounded = if (base.durationMs > 0L) {
                progressMs.coerceIn(0L, base.durationMs)
            } else {
                progressMs.coerceAtLeast(0L)
            }
            val pendingSeek = pendingSeekPositionMs
            if (base.isPreparing && pendingSeek != null) {
                if (!shouldAcceptPendingSeekProgress(targetMs = pendingSeek, reportedMs = bounded)) {
                    return@setProgressListener
                }
                pendingSeekPositionMs = null
                _state.value = base.copy(
                    positionMs = bounded,
                    playbackState = if (base.playWhenReady) {
                        PLAYBACK_STATE_PLAYING
                    } else {
                        PLAYBACK_STATE_PAUSED
                    },
                    isPreparing = false,
                    statusText = if (base.playWhenReady) "Playing" else "Paused"
                )
                return@setProgressListener
            }
            if (base.isPreparing) {
                return@setProgressListener
            }
            _state.value = base.copy(positionMs = bounded)
        }

        playbackCoordinator.startPlaybackStateObserver(intervalMs = 200L) { playState ->
            val base = _state.value
            if (base.isPreparing) {
                if (!base.playWhenReady && playState == PLAYBACK_STATE_PAUSED) {
                    pendingSeekPositionMs = null
                    _state.value = base.copy(
                        playbackState = PLAYBACK_STATE_PAUSED,
                        isPreparing = false,
                        statusText = "Paused"
                    )
                }
                return@startPlaybackStateObserver
            }
            _state.value = base.copy(playbackState = playState)
        }
    }

    fun setQueue(mediaItems: List<MediaItem>, startIndex: Int) {
        if (mediaItems.isEmpty()) {
            clearQueue(statusText = "Queue is empty")
            return
        }

        val tracks = mediaItems.mapNotNull { PlaybackTrack.fromMediaItem(it) }
        if (tracks.isEmpty()) {
            clearQueue(statusText = "Invalid queue")
            return
        }

        val previous = _state.value
        val normalizedIndex = when {
            startIndex in tracks.indices -> startIndex
            previous.activeIndex in tracks.indices -> previous.activeIndex
            else -> 0
        }

        applyTrackSelection(
            tracks = tracks,
            nextIndex = normalizedIndex,
            statusText = "Queue updated"
        )
    }

    fun setCurrentMediaItem(mediaItem: MediaItem?) {
        setQueue(
            mediaItems = listOfNotNull(mediaItem),
            startIndex = 0
        )
    }

    fun setActiveIndex(index: Int): Boolean {
        val tracks = _state.value.tracks
        if (index !in tracks.indices) {
            return false
        }
        if (index == _state.value.activeIndex) {
            return false
        }
        applyTrackSelection(
            tracks = tracks,
            nextIndex = index,
            statusText = "Selected: ${tracks[index].displayName}"
        )
        return true
    }

    fun moveToNext(): Boolean {
        val value = _state.value
        val nextIndex = when {
            value.playbackMode == PlaybackMode.LIST_LOOP &&
                value.tracks.size > 1 &&
                value.activeIndex == value.tracks.lastIndex -> 0

            else -> value.activeIndex + 1
        }
        if (nextIndex !in value.tracks.indices) {
            return false
        }
        return setActiveIndex(nextIndex)
    }

    fun moveToPrevious(): Boolean {
        val value = _state.value
        val nextIndex = when {
            value.playbackMode == PlaybackMode.LIST_LOOP &&
                value.tracks.size > 1 &&
                value.activeIndex == 0 -> value.tracks.lastIndex

            else -> value.activeIndex - 1
        }
        if (nextIndex !in value.tracks.indices) {
            return false
        }
        return setActiveIndex(nextIndex)
    }

    fun canMoveToNext(): Boolean {
        val value = _state.value
        return when {
            value.activeIndex !in value.tracks.indices -> false
            value.playbackMode == PlaybackMode.LIST_LOOP -> value.tracks.size > 1
            else -> value.activeIndex < value.tracks.lastIndex
        }
    }

    fun canMoveToPrevious(): Boolean {
        val value = _state.value
        return when {
            value.activeIndex !in value.tracks.indices -> false
            value.playbackMode == PlaybackMode.LIST_LOOP -> value.tracks.size > 1
            else -> value.activeIndex > 0
        }
    }

    fun currentMediaId(): String? {
        return _state.value.currentTrack?.id
    }

    fun setPlayWhenReady(playWhenReady: Boolean) {
        _state.value = _state.value.copy(playWhenReady = playWhenReady)
    }

    fun setPlaybackSpeed(speed: Float): Boolean {
        val normalizedSpeed = PlaybackSpeed.normalizeValue(speed)
        val code = playbackCoordinator.setPlaybackSpeed(normalizedSpeed)
        if (code != 0) {
            _state.value = _state.value.copy(
                statusText = "Set speed failed($code): ${playbackCoordinator.lastError()}"
            )
            return false
        }
        _state.value = _state.value.copy(playbackSpeed = normalizedSpeed)
        return true
    }

    fun setPlaybackMode(playbackMode: PlaybackMode) {
        _state.value = _state.value.copy(playbackMode = playbackMode)
    }

    fun setDisplayMetadata(title: String?, subtitle: String?) {
        _state.value = _state.value.copy(
            displayTitleOverride = title?.takeIf { it.isNotBlank() },
            displaySubtitleOverride = subtitle?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun prepareCurrent() {
        val item = _state.value.currentTrack ?: return
        ensurePrepared(item)
    }

    suspend fun playCurrent() {
        val item = _state.value.currentTrack ?: return
        val requestedTrackId = item.id
        val currentState = _state.value
        if (currentState.playWhenReady &&
            currentState.currentTrack?.id == item.id &&
            currentState.playbackState == PLAYBACK_STATE_PLAYING &&
            activePlaybackTrackId == item.id
        ) {
            safeLogI("playCurrent skip: already active id=${item.id}, state=${currentState.playbackState}")
            return
        }
        safeLogI("playCurrent begin: id=${item.id}, uri=${item.uri}")
        if (!ensurePrepared(item)) {
            safeLogE("playCurrent aborted: ensurePrepared failed for id=${item.id}")
            return
        }

        val source = sourceSession.currentSource() ?: return
        val sourceOpenCode = source.open()
        if (sourceOpenCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
            safeLogE("playCurrent source open failed: id=${item.id}, code=${sourceOpenCode.code}")
            _state.value = _state.value.copy(statusText = "Source open failed(${sourceOpenCode.code})")
            return
        }
        if (source.supportFastSeek() && source.seek(0L, IPlaysource.SEEK_SET) < 0L) {
            safeLogE("playCurrent source rewind failed: id=${item.id}")
            _state.value = _state.value.copy(statusText = "Source rewind failed")
            return
        }
        safeLogI("playCurrent launchPlay: id=${item.id}")

        val speedCode = playbackCoordinator.setPlaybackSpeed(_state.value.playbackSpeed)
        if (speedCode != 0) {
            _state.value = _state.value.copy(
                playWhenReady = false,
                playbackState = PLAYBACK_STATE_STOPPED,
                statusText = "Set speed failed($speedCode): ${playbackCoordinator.lastError()}"
            )
            return
        }

        var completionAction = PlaybackCompletionAction.STOP_WITH_ERROR
        playbackCoordinator.launchPlay(
            source = source,
            onStarted = {
                safeLogI("playCurrent onStarted: id=${item.id}")
                activePlaybackTrackId = requestedTrackId
                pendingSeekPositionMs = null
                _state.value = _state.value.copy(
                    playWhenReady = true,
                    playbackState = PLAYBACK_STATE_PLAYING,
                    positionMs = 0L,
                    statusText = "Playing: ${item.displayName}"
                )
            },
            onCompleted = { playCode ->
                if (shouldIgnorePlaybackCallback(requestedTrackId, _state.value.currentTrack?.id)) {
                    safeLogI("playCurrent ignore stale completion: callback=$requestedTrackId, current=${_state.value.currentTrack?.id}, code=$playCode")
                    return@launchPlay
                }
                safeLogI("playCurrent onCompleted: id=${item.id}, code=$playCode, error=${playbackCoordinator.lastError()}")
                activePlaybackTrackId = null
                pendingSeekPositionMs = null
                completionAction = PlaybackCompletionAction.resolve(
                    playCode = playCode,
                    activeIndex = _state.value.activeIndex,
                    trackCount = _state.value.tracks.size,
                    playbackMode = _state.value.playbackMode
                )
                when (completionAction) {
                    PlaybackCompletionAction.AUTO_NEXT,
                    PlaybackCompletionAction.LOOP_TO_FIRST,
                    PlaybackCompletionAction.REPEAT_CURRENT,
                    PlaybackCompletionAction.STOP_AT_END -> {
                        val durationMs = _state.value.durationMs
                        _state.value = _state.value.copy(
                            playWhenReady = false,
                            playbackState = PLAYBACK_STATE_STOPPED,
                            positionMs = if (durationMs > 0L) durationMs else _state.value.positionMs,
                            statusText = "Playback finished"
                        )
                    }

                    PlaybackCompletionAction.STOP_WITH_ERROR -> {
                        _state.value = _state.value.copy(
                            playWhenReady = false,
                            playbackState = PLAYBACK_STATE_STOPPED,
                            statusText = PlaybackFormatter.formatPlaybackResult(
                                playCode = playCode,
                                lastError = playbackCoordinator.lastError()
                            )
                        )
                    }
                }
            },
            onFinally = {
                if (shouldIgnorePlaybackCallback(requestedTrackId, _state.value.currentTrack?.id)) {
                    safeLogI("playCurrent ignore stale finally: callback=$requestedTrackId, current=${_state.value.currentTrack?.id}")
                    return@launchPlay
                }
                when (completionAction) {
                    PlaybackCompletionAction.AUTO_NEXT -> {
                        val moved = moveToNext()
                        if (!moved) {
                            safeLogI("playCurrent auto-next skipped: already at tail")
                            return@launchPlay
                        }
                    }

                    PlaybackCompletionAction.LOOP_TO_FIRST -> {
                        val moved = setActiveIndex(0)
                        if (!moved && _state.value.activeIndex != 0) {
                            safeLogI("playCurrent loop-to-first skipped: queue unavailable")
                            return@launchPlay
                        }
                    }

                    PlaybackCompletionAction.REPEAT_CURRENT -> Unit
                    PlaybackCompletionAction.STOP_AT_END,
                    PlaybackCompletionAction.STOP_WITH_ERROR -> return@launchPlay
                }
                safeLogI("playCurrent auto-next: nextIndex=${_state.value.activeIndex}, speed=${_state.value.playbackSpeed}")
                serviceScope.launch {
                    _state.value = _state.value.copy(playWhenReady = true)
                    playCurrent()
                }
            }
        )
    }

    fun pause() {
        val code = playbackCoordinator.pause()
        if (code == 0) {
            _state.value = _state.value.copy(
                playWhenReady = false,
                playbackState = PLAYBACK_STATE_PAUSED,
                statusText = "Paused"
            )
        } else {
            _state.value = _state.value.copy(statusText = "Pause failed($code): ${playbackCoordinator.lastError()}")
        }
    }

    fun resume() {
        val speedCode = playbackCoordinator.setPlaybackSpeed(_state.value.playbackSpeed)
        if (speedCode != 0) {
            _state.value = _state.value.copy(
                statusText = "Set speed failed($speedCode): ${playbackCoordinator.lastError()}"
            )
            return
        }
        val code = playbackCoordinator.resume()
        if (code == 0) {
            _state.value = _state.value.copy(
                playWhenReady = true,
                playbackState = PLAYBACK_STATE_PLAYING,
                statusText = "Playing"
            )
        } else {
            _state.value = _state.value.copy(statusText = "Resume failed($code): ${playbackCoordinator.lastError()}")
        }
    }

    fun seekTo(positionMs: Long) {
        if (!_state.value.isSeekSupported) {
            _state.value = _state.value.copy(statusText = "Current source does not support seek")
            return
        }
        val durationMs = _state.value.durationMs
        val bounded = if (durationMs > 0L) positionMs.coerceIn(0L, durationMs) else positionMs.coerceAtLeast(0L)
        val code = playbackCoordinator.seek(bounded)
        if (code == 0) {
            val current = _state.value
            val shouldBuffer = current.playWhenReady || current.playbackState == PLAYBACK_STATE_PLAYING
            pendingSeekPositionMs = if (shouldBuffer) bounded else null
            _state.value = current.copy(
                positionMs = bounded,
                isPreparing = shouldBuffer,
                playbackState = if (shouldBuffer) {
                    PLAYBACK_STATE_STOPPED
                } else {
                    current.playbackState
                },
                statusText = if (shouldBuffer) "Buffering" else current.statusText
            )
        } else {
            _state.value = _state.value.copy(statusText = "Seek failed($code): ${playbackCoordinator.lastError()}")
        }
    }

    fun stop() {
        activePlaybackTrackId = null
        pendingSeekPositionMs = null
        sourceSession.stopCurrent()
        playbackCoordinator.stopPlayback()
        _state.value = _state.value.copy(
            playWhenReady = false,
            playbackState = PLAYBACK_STATE_STOPPED,
            positionMs = 0L,
            statusText = "Stopped"
        )
    }

    fun clearCurrentMediaItem() {
        clearQueue(statusText = "Idle")
    }

    fun clearCache(): Boolean {
        stop()
        sourceSession.release()

        val clearResult = CacheCore.clearAll()
        val success = clearResult.isSuccess
        _state.value = _state.value.copy(
            playbackOutputInfo = null,
            isSeekSupported = false,
            durationMs = 0L,
            positionMs = 0L,
            audioMeta = null,
            statusText = if (success) {
                "缓存已清理"
            } else {
                "清理缓存失败: ${clearResult.exceptionOrNull()?.message ?: "unknown"}"
            }
        )
        return success
    }

    fun release() {
        sourceSession.release()
        playbackCoordinator.close()
        CacheCore.shutdown()
    }

    private suspend fun ensurePrepared(item: PlaybackTrack): Boolean {
        if (sourceSession.isPreparedFor(item.id)) {
            return true
        }

        _state.value = _state.value.copy(isPreparing = true, statusText = "Preparing")
        return try {
            when (val preparation = trackPreparationCoordinator.prepare(item)) {
                is PreparationResult.Invalid -> {
                    pendingSeekPositionMs = null
                    _state.value = _state.value.copy(
                        playWhenReady = false,
                        isPreparing = false,
                        playbackState = PLAYBACK_STATE_STOPPED,
                        isSeekSupported = false,
                        audioMeta = null,
                        statusText = preparation.message
                    )
                    false
                }

                is PreparationResult.Ready -> {
                    sourceSession.markPrepared(item.id, preparation.source)
                    val duration = preparation.mediaMeta.durationMs.coerceAtLeast(0L)
                    _state.value = _state.value.copy(
                        isPreparing = false,
                        isSeekSupported = preparation.isSeekSupported,
                        durationMs = duration,
                        audioMeta = preparation.mediaMeta,
                        statusText = if (!preparation.isSeekSupported) {
                            "Prepared (seek unavailable)"
                        } else if (duration > 0L) {
                            "Prepared"
                        } else {
                            "Prepared (duration unavailable)"
                        }
                    )
                    true
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            pendingSeekPositionMs = null
            _state.value = _state.value.copy(
                playWhenReady = false,
                isPreparing = false,
                playbackState = PLAYBACK_STATE_STOPPED,
                isSeekSupported = false,
                audioMeta = null,
                statusText = "Failed to prepare media"
            )
            false
        }
    }

    private fun clearQueue(statusText: String) {
        val currentSpeed = _state.value.playbackSpeed
        stop()
        sourceSession.release()
        _state.value = PlaybackProcessState(
            playbackSpeed = currentSpeed,
            statusText = statusText
        )
    }

    private fun applyTrackSelection(
        tracks: List<PlaybackTrack>,
        nextIndex: Int,
        statusText: String
    ) {
        val previous = _state.value
        val previousTrackId = previous.currentTrack?.id
        val nextTrackId = tracks.getOrNull(nextIndex)?.id
        val changedTrack = previousTrackId != nextTrackId

        if (changedTrack) {
            activePlaybackTrackId = null
            pendingSeekPositionMs = null
            playbackCoordinator.stopPlayback()
            sourceSession.release()
        }

        _state.value = previous.copy(
            tracks = tracks,
            activeIndex = nextIndex,
            playWhenReady = if (changedTrack) false else previous.playWhenReady,
            playbackOutputInfo = if (changedTrack) null else previous.playbackOutputInfo,
            isSeekSupported = if (changedTrack) false else previous.isSeekSupported,
            positionMs = if (changedTrack) 0L else previous.positionMs,
            durationMs = if (changedTrack) 0L else previous.durationMs,
            audioMeta = if (changedTrack) null else previous.audioMeta,
            isPreparing = false,
            statusText = statusText,
            displayTitleOverride = if (changedTrack) null else previous.displayTitleOverride,
            displaySubtitleOverride = if (changedTrack) null else previous.displaySubtitleOverride
        )
    }

    private fun ensureCacheCoreReady() {
        if (CacheCore.isInitialized()) {
            return
        }
        val initResult = CacheCore.init(CacheCoreConfig(cacheRootDirPath = cacheRootDirPath))
        if (initResult.isFailure) {
            _state.value = _state.value.copy(
                statusText = "缓存初始化失败: ${initResult.exceptionOrNull()?.message ?: "unknown"}"
            )
        }
    }

    private companion object {
        private const val TAG = "PlaybackProcessRuntime"
        private const val API_BASE_URL = "http://139.9.223.233:3000"
    }

    private fun safeLogI(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun safeLogE(message: String) {
        runCatching { Log.e(TAG, message) }
    }

    private fun shouldAcceptPendingSeekProgress(
        targetMs: Long,
        reportedMs: Long
    ): Boolean {
        val backwardToleranceMs = 5_000L
        val forwardToleranceMs = 10_000L
        return when {
            reportedMs < targetMs -> targetMs - reportedMs <= backwardToleranceMs
            else -> abs(reportedMs - targetMs) <= forwardToleranceMs
        }
    }
}
