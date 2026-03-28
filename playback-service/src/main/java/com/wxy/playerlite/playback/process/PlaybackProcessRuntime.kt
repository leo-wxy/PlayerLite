package com.wxy.playerlite.playback.process

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import android.util.Log
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.INativePlayer
import com.wxy.playerlite.player.NativePlayer
import com.wxy.playerlite.player.PlaybackSpeed
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.player.source.IPlaysource
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

internal data class PlaybackProcessState(
    val tracks: List<PlaybackTrack> = emptyList(),
    val activeIndex: Int = C.INDEX_UNSET,
    val playWhenReady: Boolean = false,
    val playbackOutputInfo: PlaybackOutputInfo? = null,
    val playbackSpeed: Float = PlaybackSpeed.DEFAULT.value,
    val audioEffectPreset: AudioEffectPreset = AudioEffectPreset.DEFAULT,
    val preferredAudioQuality: PlaybackAudioQuality = PlaybackAudioQuality.EXHIGH,
    val activeAudioSourceConfigJson: String? = null,
    val playbackCacheLimitBytes: Long = 500L * 1024L * 1024L,
    val appliedAudioQuality: PlaybackAudioQuality? = null,
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
    private val serviceScope: CoroutineScope,
    nativePlayerFactory: () -> INativePlayer = { NativePlayer() },
    trackPreparer: TrackPreparer? = null,
    sourceAdapterFactory: SourceAdapterFactory? = null
) {
    private val cacheRootDirPath = File(appContext.cacheDir, "cache_core").absolutePath
    private val audioEffectPreferences: SharedPreferences? = appContext.getSharedPreferences(
        AUDIO_EFFECT_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val sourceAdapterFactory: SourceAdapterFactory = sourceAdapterFactory
        ?: DefaultSourceAdapterFactory(API_BASE_URL)
    @Volatile
    private var activeAudioSourceRuntime: ActivePlaybackSourceRuntime = readPersistedActiveAudioSourceRuntime()
    @Volatile
    private var playbackCacheLimitBytes: Long = readPersistedPlaybackCacheLimitBytes()
    private val onlinePlaybackPlanner = OnlinePlaybackPreparationPlanner(
        sourceAdapterProvider = ::currentSourceAdapter
    )
    private val mediaSourceRepository = MediaSourceRepository(appContext)
    private val playbackCoordinator = PlaybackCoordinator(
        player = nativePlayerFactory(),
        scope = serviceScope
    )
    private val trackPreparationCoordinator: TrackPreparer = trackPreparer ?: TrackPreparationCoordinator(
        sourceRepository = mediaSourceRepository,
        playbackCoordinator = playbackCoordinator,
        onlinePreparationPlanner = onlinePlaybackPlanner
    )
    private val sourceSession = PreparedSourceSession()
    @Volatile
    private var pendingSeekPositionMs: Long? = null
    @Volatile
    private var activePlaybackTrackId: String? = null
    @Volatile
    private var pendingAudioQualitySwitchTarget: PlaybackAudioQuality? = null

    private val _state = MutableStateFlow(
        PlaybackProcessState(
            audioEffectPreset = readPersistedAudioEffectPreset(),
            preferredAudioQuality = readPersistedPreferredAudioQuality(),
            activeAudioSourceConfigJson = activeAudioSourceRuntime.configJson,
            playbackCacheLimitBytes = playbackCacheLimitBytes
        )
    )
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
                val audioQualityStatus = consumePendingAudioQualitySwitchStatus(
                    appliedAudioQuality = base.appliedAudioQuality
                )
                _state.value = base.copy(
                    positionMs = bounded,
                    playbackState = if (base.playWhenReady) {
                        PLAYBACK_STATE_PLAYING
                    } else {
                        PLAYBACK_STATE_PAUSED
                    },
                    isPreparing = false,
                    statusText = audioQualityStatus ?: if (base.playWhenReady) "Playing" else "Paused"
                )
                return@setProgressListener
            }
            if (base.isPreparing) {
                return@setProgressListener
            }
            if (
                base.playWhenReady &&
                base.playbackState == PLAYBACK_STATE_PLAYING &&
                pendingSeek == null &&
                bounded < base.positionMs &&
                base.positionMs - bounded > STALE_PROGRESS_REGRESSION_TOLERANCE_MS
            ) {
                return@setProgressListener
            }
            _state.value = base.copy(positionMs = bounded)
        }

        playbackCoordinator.startPlaybackStateObserver(intervalMs = 200L) { playState ->
            val base = _state.value
            if (base.isPreparing) {
                if (!base.playWhenReady && playState == PLAYBACK_STATE_PAUSED) {
                    pendingSeekPositionMs = null
                    val audioQualityStatus = consumePendingAudioQualitySwitchStatus(
                        appliedAudioQuality = base.appliedAudioQuality
                    )
                    _state.value = base.copy(
                        playbackState = PLAYBACK_STATE_PAUSED,
                        isPreparing = false,
                        statusText = audioQualityStatus ?: "Paused"
                    )
                }
                return@startPlaybackStateObserver
            }
            if (base.playWhenReady && playState == PLAYBACK_STATE_PAUSED) {
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
            else -> value.tracks.size > 1
        }
    }

    fun canMoveToPrevious(): Boolean {
        val value = _state.value
        return when {
            value.activeIndex !in value.tracks.indices -> false
            else -> value.tracks.size > 1
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

    fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Boolean {
        val code = playbackCoordinator.setAudioEffectPreset(audioEffectPreset)
        if (code != 0) {
            _state.value = _state.value.copy(
                statusText = "Set audio effect failed($code): ${playbackCoordinator.lastError()}"
            )
            return false
        }
        _state.value = _state.value.copy(audioEffectPreset = audioEffectPreset)
        persistAudioEffectPreset(audioEffectPreset)
        return true
    }

    fun setPreferredAudioQuality(audioQuality: PlaybackAudioQuality): Boolean {
        val sanitizedAudioQuality = sanitizePreferredAudioQuality(audioQuality)
        val currentState = _state.value
        val currentTrack = currentState.currentTrack
        val nextAppliedQuality = if (!currentTrack?.songId.isNullOrBlank()) {
            currentState.appliedAudioQuality
        } else {
            null
        }
        _state.value = currentState.copy(
            preferredAudioQuality = sanitizedAudioQuality,
            appliedAudioQuality = nextAppliedQuality
        )
        persistPreferredAudioQuality(sanitizedAudioQuality)
        if (
            currentState.preferredAudioQuality != sanitizedAudioQuality &&
            currentTrack != null &&
            !currentTrack.songId.isNullOrBlank() &&
            (
                currentState.playWhenReady ||
                    currentState.playbackState == PLAYBACK_STATE_PLAYING ||
                    currentState.playbackState == PLAYBACK_STATE_PAUSED
                )
        ) {
            reprepareCurrentTrackForAudioQualityChange(
                previousState = currentState,
                currentTrack = currentTrack,
                requestedAudioQuality = sanitizedAudioQuality
            )
        }
        return true
    }

    fun setPlaybackCacheLimitBytes(maxBytes: Long): Boolean {
        val sanitizedLimitBytes = maxBytes.coerceAtLeast(MIN_PLAYBACK_CACHE_LIMIT_BYTES)
        playbackCacheLimitBytes = sanitizedLimitBytes
        persistPlaybackCacheLimitBytes(sanitizedLimitBytes)
        val reconfigured = ensureCacheCoreReady(forceReinitialize = true)
        _state.value = _state.value.copy(
            playbackCacheLimitBytes = sanitizedLimitBytes,
            statusText = if (reconfigured) {
                "歌曲缓存上限已更新"
            } else {
                "歌曲缓存上限已保存，播放进程重连后生效"
            }
        )
        return reconfigured
    }

    fun setActiveAudioSourceConfigJson(configJson: String?): Boolean {
        val nextRuntime = createInitializedSourceRuntime(configJson)
            .getOrElse { return false }
        val currentState = _state.value
        val currentTrack = currentState.currentTrack
        val previousRuntime = activeAudioSourceRuntime
        val normalizedConfigJson = nextRuntime.configJson
        val sourceChanged = currentState.activeAudioSourceConfigJson != normalizedConfigJson
        if (!sourceChanged) {
            return true
        }
        activeAudioSourceRuntime = nextRuntime
        persistActiveAudioSourceRuntime(nextRuntime)
        invalidateOnlinePreparationCaches(previousRuntime.adapter)
        _state.value = currentState.copy(
            activeAudioSourceConfigJson = normalizedConfigJson,
            statusText = if (sourceChanged) {
                "当前音源已更新"
            } else {
                currentState.statusText
            }
        )
        if (
            currentTrack != null &&
            !currentTrack.songId.isNullOrBlank() &&
            (
                currentState.playWhenReady ||
                    currentState.playbackState == PLAYBACK_STATE_PLAYING ||
                    currentState.playbackState == PLAYBACK_STATE_PAUSED ||
                    sourceSession.isPreparedFor(currentTrack.id)
                )
        ) {
            reprepareCurrentTrackForAudioSourceChange(
                previousState = currentState.copy(
                    activeAudioSourceConfigJson = normalizedConfigJson
                ),
                currentTrack = currentTrack
            )
        }
        return true
    }

    fun setDisplayMetadata(title: String?, subtitle: String?) {
        _state.value = _state.value.copy(
            displayTitleOverride = title?.takeIf { it.isNotBlank() },
            displaySubtitleOverride = subtitle?.takeIf { it.isNotBlank() }
        )
    }

    private fun reprepareCurrentTrackForAudioQualityChange(
        previousState: PlaybackProcessState,
        currentTrack: PlaybackTrack,
        requestedAudioQuality: PlaybackAudioQuality
    ) {
        val resumePositionMs = previousState.positionMs.coerceAtLeast(0L)
        val shouldResumePlayback = previousState.playWhenReady ||
            previousState.playbackState == PLAYBACK_STATE_PLAYING
        val shouldRestorePausedPosition = !shouldResumePlayback &&
            previousState.playbackState == PLAYBACK_STATE_PAUSED &&
            resumePositionMs > 0L

        activePlaybackTrackId = null
        pendingSeekPositionMs = null
        pendingAudioQualitySwitchTarget = requestedAudioQuality
        playbackCoordinator.stopPlayback()
        sourceSession.release()
        _state.value = previousState.copy(
            preferredAudioQuality = requestedAudioQuality,
            appliedAudioQuality = null,
            isPreparing = true,
            playbackState = if (shouldResumePlayback) {
                PLAYBACK_STATE_STOPPED
            } else {
                previousState.playbackState
            },
            statusText = formatAudioQualitySwitchingStatus(requestedAudioQuality)
        )
        serviceScope.launch {
            if (!ensurePrepared(currentTrack)) {
                return@launch
            }
            when {
                shouldResumePlayback -> playCurrent(initialPositionMs = resumePositionMs)
                shouldRestorePausedPosition -> restorePreparedPositionWithoutPlayback(
                    positionMs = resumePositionMs
                )
                else -> publishPendingAudioQualitySwitchAppliedStatus()
            }
        }
    }

    private fun reprepareCurrentTrackForAudioSourceChange(
        previousState: PlaybackProcessState,
        currentTrack: PlaybackTrack
    ) {
        val resumePositionMs = previousState.positionMs.coerceAtLeast(0L)
        val shouldResumePlayback = previousState.playWhenReady ||
            previousState.playbackState == PLAYBACK_STATE_PLAYING
        val shouldRestorePausedPosition = !shouldResumePlayback &&
            previousState.playbackState == PLAYBACK_STATE_PAUSED &&
            resumePositionMs > 0L

        activePlaybackTrackId = null
        pendingSeekPositionMs = null
        pendingAudioQualitySwitchTarget = null
        playbackCoordinator.stopPlayback()
        sourceSession.release()
        _state.value = previousState.copy(
            isPreparing = true,
            playbackState = if (shouldResumePlayback) {
                PLAYBACK_STATE_STOPPED
            } else {
                previousState.playbackState
            },
            statusText = "切换音源中"
        )
        serviceScope.launch {
            if (!ensurePrepared(currentTrack)) {
                return@launch
            }
            when {
                shouldResumePlayback -> playCurrent(initialPositionMs = resumePositionMs)
                shouldRestorePausedPosition -> restorePreparedPositionWithoutPlayback(
                    positionMs = resumePositionMs
                )
                else -> {
                    _state.value = _state.value.copy(
                        isPreparing = false,
                        statusText = "当前音源已更新"
                    )
                }
            }
        }
    }

    private fun restorePreparedPositionWithoutPlayback(positionMs: Long) {
        val source = sourceSession.currentSource() ?: return
        if (!source.supportFastSeek()) {
            _state.value = _state.value.copy(
                positionMs = 0L,
                playbackState = PLAYBACK_STATE_PAUSED,
                isPreparing = false,
                statusText = "Prepared (seek unavailable)"
            )
            return
        }
        val audioQualityStatus = consumePendingAudioQualitySwitchStatus(
            appliedAudioQuality = _state.value.appliedAudioQuality
        )
        _state.value = _state.value.copy(
            positionMs = positionMs,
            playbackState = PLAYBACK_STATE_PAUSED,
            isPreparing = false,
            statusText = audioQualityStatus ?: "Paused"
        )
    }

    suspend fun prepareCurrent() {
        val item = _state.value.currentTrack ?: return
        ensurePrepared(item)
    }

    suspend fun playCurrent(initialPositionMs: Long = 0L) {
        val item = _state.value.currentTrack ?: return
        val requestedTrackId = item.id
        val currentState = _state.value
        val startPositionMs = initialPositionMs.coerceAtLeast(0L)
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
            pendingAudioQualitySwitchTarget = null
            safeLogE("playCurrent source open failed: id=${item.id}, code=${sourceOpenCode.code}")
            _state.value = _state.value.copy(statusText = "Source open failed(${sourceOpenCode.code})")
            return
        }
        val supportsFastSeek = source.supportFastSeek()
        val effectiveStartPositionMs = if (supportsFastSeek) startPositionMs else 0L
        if (supportsFastSeek && source.seek(0L, IPlaysource.SEEK_SET) < 0L) {
            pendingAudioQualitySwitchTarget = null
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
        val audioEffectCode = playbackCoordinator.setAudioEffectPreset(_state.value.audioEffectPreset)
        if (audioEffectCode != 0) {
            _state.value = _state.value.copy(
                playWhenReady = false,
                playbackState = PLAYBACK_STATE_STOPPED,
                statusText = "Set audio effect failed($audioEffectCode): ${playbackCoordinator.lastError()}"
            )
            return
        }

        var completionAction = PlaybackCompletionAction.STOP_WITH_ERROR
        playbackCoordinator.launchPlay(
            source = source,
            onStarted = {
                safeLogI("playCurrent onStarted: id=${item.id}")
                activePlaybackTrackId = requestedTrackId
                if (effectiveStartPositionMs > 0L) {
                    pendingSeekPositionMs = effectiveStartPositionMs
                    _state.value = _state.value.copy(
                        playWhenReady = true,
                        playbackState = PLAYBACK_STATE_STOPPED,
                        positionMs = effectiveStartPositionMs,
                        isPreparing = true,
                        statusText = pendingAudioQualitySwitchTarget
                            ?.let(::formatAudioQualitySwitchingStatus)
                            ?: "Buffering"
                    )
                    launchDeferredPlaybackSeek(
                        requestedTrackId = requestedTrackId,
                        displayName = item.displayName,
                        targetPositionMs = effectiveStartPositionMs
                    )
                } else {
                    pendingSeekPositionMs = null
                    val audioQualityStatus = consumePendingAudioQualitySwitchStatus(
                        appliedAudioQuality = _state.value.appliedAudioQuality
                    )
                    _state.value = _state.value.copy(
                        playWhenReady = true,
                        playbackState = PLAYBACK_STATE_PLAYING,
                        positionMs = 0L,
                        isPreparing = false,
                        statusText = audioQualityStatus ?: "Playing: ${item.displayName}"
                    )
                }
            },
            onCompleted = { playCode ->
                if (shouldIgnorePlaybackCallback(requestedTrackId, _state.value.currentTrack?.id)) {
                    safeLogI("playCurrent ignore stale completion: callback=$requestedTrackId, current=${_state.value.currentTrack?.id}, code=$playCode")
                    return@launchPlay
                }
                safeLogI("playCurrent onCompleted: id=${item.id}, code=$playCode, error=${playbackCoordinator.lastError()}")
                activePlaybackTrackId = null
                pendingSeekPositionMs = null
                pendingAudioQualitySwitchTarget = null
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
        val audioEffectCode = playbackCoordinator.setAudioEffectPreset(_state.value.audioEffectPreset)
        if (audioEffectCode != 0) {
            _state.value = _state.value.copy(
                statusText = "Set audio effect failed($audioEffectCode): ${playbackCoordinator.lastError()}"
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
                statusText = if (shouldBuffer) {
                    pendingAudioQualitySwitchTarget?.let(::formatAudioQualitySwitchingStatus) ?: "Buffering"
                } else {
                    current.statusText
                }
            )
        } else {
            _state.value = _state.value.copy(statusText = "Seek failed($code): ${playbackCoordinator.lastError()}")
        }
    }

    fun stop() {
        activePlaybackTrackId = null
        pendingSeekPositionMs = null
        pendingAudioQualitySwitchTarget = null
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
        invalidateOnlinePreparationCaches()

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
            when (
                val preparation = trackPreparationCoordinator.prepare(
                    item = item,
                    preferredAudioQuality = _state.value.preferredAudioQuality
                )
            ) {
                is PreparationResult.Invalid -> {
                    pendingSeekPositionMs = null
                    pendingAudioQualitySwitchTarget = null
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
                        appliedAudioQuality = preparation.appliedAudioQuality,
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
            pendingAudioQualitySwitchTarget = null
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
        val currentAudioEffectPreset = _state.value.audioEffectPreset
        val currentPreferredAudioQuality = _state.value.preferredAudioQuality
        val currentActiveAudioSourceConfigJson = _state.value.activeAudioSourceConfigJson
        val currentPlaybackCacheLimitBytes = _state.value.playbackCacheLimitBytes
        stop()
        sourceSession.release()
        _state.value = PlaybackProcessState(
            playbackSpeed = currentSpeed,
            audioEffectPreset = currentAudioEffectPreset,
            preferredAudioQuality = currentPreferredAudioQuality,
            activeAudioSourceConfigJson = currentActiveAudioSourceConfigJson,
            playbackCacheLimitBytes = currentPlaybackCacheLimitBytes,
            statusText = statusText
        )
    }

    private fun readPersistedAudioEffectPreset(): AudioEffectPreset {
        return AudioEffectPreset.fromWireValue(
            audioEffectPreferences?.getString(KEY_AUDIO_EFFECT_PRESET, null)
        )
    }

    private fun persistAudioEffectPreset(audioEffectPreset: AudioEffectPreset) {
        audioEffectPreferences?.edit()
            ?.putString(KEY_AUDIO_EFFECT_PRESET, audioEffectPreset.wireValue)
            ?.apply()
    }

    private fun readPersistedPreferredAudioQuality(): PlaybackAudioQuality {
        return sanitizePreferredAudioQuality(
            PlaybackAudioQuality.fromWireValue(
                audioEffectPreferences?.getString(KEY_PREFERRED_AUDIO_QUALITY, null)
            )
        )
    }

    private fun sanitizePreferredAudioQuality(audioQuality: PlaybackAudioQuality?): PlaybackAudioQuality {
        return when (audioQuality) {
            PlaybackAudioQuality.VIVID,
            null -> PlaybackAudioQuality.EXHIGH
            else -> audioQuality
        }
    }

    private fun persistPreferredAudioQuality(audioQuality: PlaybackAudioQuality) {
        audioEffectPreferences?.edit()
            ?.putString(KEY_PREFERRED_AUDIO_QUALITY, audioQuality.wireValue)
            ?.apply()
    }

    private fun readPersistedActiveAudioSourceRuntime(): ActivePlaybackSourceRuntime {
        val persistedConfigJson = audioEffectPreferences?.getString(KEY_ACTIVE_AUDIO_SOURCE_CONFIG_JSON, null)
        persistedConfigJson
            ?.let(::createInitializedSourceRuntime)
            ?.getOrNull()
            ?.also { restoredRuntime ->
                if (persistedConfigJson != restoredRuntime.configJson) {
                    persistActiveAudioSourceRuntime(restoredRuntime)
                }
            }
            ?.let { return it }
        val legacyBaseUrl = normalizeLegacyPreferredAudioSourceBaseUrl(
            audioEffectPreferences?.getString(KEY_PREFERRED_AUDIO_SOURCE_BASE_URL, null)
        )
        if (legacyBaseUrl != null) {
            createInitializedSourceRuntime(buildNeteaseCompatibleConfigJson(legacyBaseUrl))
                .getOrNull()
                ?.also(::persistActiveAudioSourceRuntime)
                ?.let { return it }
        }
        return createInitializedSourceRuntime(null)
            .getOrElse { throw IllegalStateException("Failed to initialize built-in source adapter", it) }
            .also(::persistActiveAudioSourceRuntime)
    }

    private fun persistActiveAudioSourceRuntime(config: ActivePlaybackSourceRuntime?) {
        audioEffectPreferences?.edit()
            ?.putString(KEY_ACTIVE_AUDIO_SOURCE_CONFIG_JSON, config?.configJson)
            ?.apply()
    }

    private fun readPersistedPlaybackCacheLimitBytes(): Long {
        return audioEffectPreferences?.getLong(
            KEY_PLAYBACK_CACHE_LIMIT_BYTES,
            DEFAULT_PLAYBACK_CACHE_LIMIT_BYTES
        )?.coerceAtLeast(MIN_PLAYBACK_CACHE_LIMIT_BYTES)
            ?: DEFAULT_PLAYBACK_CACHE_LIMIT_BYTES
    }

    private fun persistPlaybackCacheLimitBytes(maxBytes: Long) {
        audioEffectPreferences?.edit()
            ?.putLong(
                KEY_PLAYBACK_CACHE_LIMIT_BYTES,
                maxBytes.coerceAtLeast(MIN_PLAYBACK_CACHE_LIMIT_BYTES)
            )
            ?.apply()
    }

    private fun buildNeteaseCompatibleConfigJson(baseUrl: String): String {
        return JSONObject().apply {
            put("type", "netease-compatible")
            put("baseUrl", baseUrl.trim().trimEnd('/'))
        }.toString()
    }

    private fun normalizeLegacyPreferredAudioSourceBaseUrl(baseUrl: String?): String? {
        return baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
    }

    private fun createInitializedSourceRuntime(
        configJson: String?
    ): Result<ActivePlaybackSourceRuntime> {
        return sourceAdapterFactory.create(configJson).mapCatching { adapter ->
            val initState = adapter.init().getOrElse { throw it }
            require(initState.enabled) {
                initState.detailMessage ?: "Source adapter is disabled"
            }
            require(initState.initError.isNullOrBlank()) {
                initState.initError ?: "Source adapter init failed"
            }
            ActivePlaybackSourceRuntime(
                configJson = adapter.normalizedConfigJson,
                adapter = adapter
            )
        }
    }

    private fun currentSourceAdapter(): SourceAdapter {
        return activeAudioSourceRuntime.adapter
    }

    private fun invalidateOnlinePreparationCaches(
        adapter: SourceAdapter = currentSourceAdapter()
    ) {
        adapter.clearCaches()
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
            pendingAudioQualitySwitchTarget = null
            playbackCoordinator.stopPlayback()
            sourceSession.release()
        }

        _state.value = previous.copy(
            tracks = tracks,
            activeIndex = nextIndex,
            playWhenReady = previous.playWhenReady,
            playbackOutputInfo = if (changedTrack) null else previous.playbackOutputInfo,
            isSeekSupported = if (changedTrack) false else previous.isSeekSupported,
            positionMs = if (changedTrack) 0L else previous.positionMs,
            durationMs = if (changedTrack) 0L else previous.durationMs,
            appliedAudioQuality = if (changedTrack) null else previous.appliedAudioQuality,
            audioMeta = if (changedTrack) null else previous.audioMeta,
            isPreparing = false,
            playbackState = if (changedTrack) PLAYBACK_STATE_STOPPED else previous.playbackState,
            statusText = statusText,
            displayTitleOverride = if (changedTrack) null else previous.displayTitleOverride,
            displaySubtitleOverride = if (changedTrack) null else previous.displaySubtitleOverride
        )
    }

    private fun ensureCacheCoreReady(forceReinitialize: Boolean = false): Boolean {
        if (CacheCore.isInitialized() && !forceReinitialize) {
            return true
        }
        val initResult = CacheCore.init(
            CacheCoreConfig(
                cacheRootDirPath = cacheRootDirPath,
                diskCacheMaxBytes = playbackCacheLimitBytes
            )
        )
        if (initResult.isSuccess) {
            return true
        }
        _state.value = _state.value.copy(
            statusText = "缓存初始化失败: ${initResult.exceptionOrNull()?.message ?: "unknown"}"
        )
        return false
    }

    private companion object {
        private const val TAG = "PlaybackProcessRuntime"
        private const val API_BASE_URL = "http://139.9.223.233:3000"
        private const val STALE_PROGRESS_REGRESSION_TOLERANCE_MS = 300L
        private const val INITIAL_PLAYBACK_SEEK_MAX_ATTEMPTS = 12
        private const val INITIAL_PLAYBACK_SEEK_RETRY_DELAY_MS = 25L
        private const val DEFAULT_PLAYBACK_CACHE_LIMIT_BYTES = 500L * 1024L * 1024L
        private const val MIN_PLAYBACK_CACHE_LIMIT_BYTES = 64L * 1024L * 1024L
        private const val AUDIO_EFFECT_PREFERENCES_NAME = "player_playback_preferences"
        private const val KEY_AUDIO_EFFECT_PRESET = "audio_effect_preset"
        private const val KEY_PREFERRED_AUDIO_QUALITY = "preferred_audio_quality"
        private const val KEY_ACTIVE_AUDIO_SOURCE_CONFIG_JSON = "active_audio_source_config_json"
        private const val KEY_PREFERRED_AUDIO_SOURCE_BASE_URL = "preferred_audio_source_base_url"
        private const val KEY_PLAYBACK_CACHE_LIMIT_BYTES = "playback_cache_limit_bytes"
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

    private fun launchDeferredPlaybackSeek(
        requestedTrackId: String,
        displayName: String,
        targetPositionMs: Long
    ) {
        if (targetPositionMs <= 0L) {
            return
        }
        serviceScope.launch {
            repeat(INITIAL_PLAYBACK_SEEK_MAX_ATTEMPTS) {
                val current = _state.value
                if (
                    shouldIgnorePlaybackCallback(requestedTrackId, current.currentTrack?.id) ||
                    activePlaybackTrackId != requestedTrackId ||
                    !current.playWhenReady
                ) {
                    return@launch
                }
                val seekCode = playbackCoordinator.seek(targetPositionMs)
                if (seekCode == 0) {
                    return@launch
                }
                delay(INITIAL_PLAYBACK_SEEK_RETRY_DELAY_MS)
            }
            val current = _state.value
            if (
                shouldIgnorePlaybackCallback(requestedTrackId, current.currentTrack?.id) ||
                activePlaybackTrackId != requestedTrackId ||
                !current.playWhenReady
            ) {
                return@launch
            }
            pendingSeekPositionMs = null
            val audioQualityStatus = consumePendingAudioQualitySwitchStatus(
                appliedAudioQuality = current.appliedAudioQuality
            )
            _state.value = current.copy(
                positionMs = 0L,
                isPreparing = false,
                playbackState = PLAYBACK_STATE_PLAYING,
                statusText = audioQualityStatus ?: "Playing: $displayName"
            )
        }
    }

    private fun publishPendingAudioQualitySwitchAppliedStatus() {
        val current = _state.value
        val audioQualityStatus = consumePendingAudioQualitySwitchStatus(
            appliedAudioQuality = current.appliedAudioQuality
        ) ?: return
        _state.value = current.copy(statusText = audioQualityStatus)
    }

    private fun consumePendingAudioQualitySwitchStatus(
        appliedAudioQuality: PlaybackAudioQuality?
    ): String? {
        val requestedAudioQuality = pendingAudioQualitySwitchTarget ?: return null
        pendingAudioQualitySwitchTarget = null
        return formatAppliedAudioQualitySwitchStatus(
            requestedAudioQuality = requestedAudioQuality,
            appliedAudioQuality = appliedAudioQuality ?: requestedAudioQuality
        )
    }

    private fun formatAudioQualitySwitchingStatus(
        requestedAudioQuality: PlaybackAudioQuality
    ): String {
        return "切换音质中：${requestedAudioQuality.displayName}"
    }

    private fun formatAppliedAudioQualitySwitchStatus(
        requestedAudioQuality: PlaybackAudioQuality,
        appliedAudioQuality: PlaybackAudioQuality
    ): String {
        return if (requestedAudioQuality == appliedAudioQuality) {
            "已切换为：${appliedAudioQuality.displayName}"
        } else {
            "当前实际使用：${appliedAudioQuality.displayName}（已选择 ${requestedAudioQuality.displayName}）"
        }
    }
}

private data class ActivePlaybackSourceRuntime(
    val configJson: String?,
    val adapter: SourceAdapter
)
