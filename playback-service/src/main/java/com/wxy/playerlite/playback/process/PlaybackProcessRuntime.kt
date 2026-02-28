package com.wxy.playerlite.playback.process

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.wxy.playerlite.player.NativePlayer
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.player.source.IPlaysource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PlaybackProcessState(
    val tracks: List<PlaybackTrack> = emptyList(),
    val activeIndex: Int = C.INDEX_UNSET,
    val playWhenReady: Boolean = false,
    val playbackOutputInfo: PlaybackOutputInfo? = null,
    val playbackState: Int = PLAYBACK_STATE_STOPPED,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPreparing: Boolean = false,
    val statusText: String = "Idle"
) {
    val currentTrack: PlaybackTrack?
        get() = tracks.getOrNull(activeIndex)
}

internal class PlaybackProcessRuntime(
    appContext: Context,
    serviceScope: CoroutineScope
) {
    private val mediaSourceRepository = MediaSourceRepository(appContext)
    private val playbackCoordinator = PlaybackCoordinator(
        player = NativePlayer(),
        scope = serviceScope
    )
    private val trackPreparationCoordinator = TrackPreparationCoordinator(
        sourceRepository = mediaSourceRepository,
        playbackCoordinator = playbackCoordinator
    )
    private val sourceSession = PreparedSourceSession()

    private val _state = MutableStateFlow(PlaybackProcessState())
    val state: StateFlow<PlaybackProcessState> = _state.asStateFlow()

    init {
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
            _state.value = base.copy(positionMs = bounded)
        }

        playbackCoordinator.startPlaybackStateObserver(intervalMs = 200L) { playState ->
            _state.value = _state.value.copy(playbackState = playState)
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
        val nextIndex = _state.value.activeIndex + 1
        if (nextIndex !in _state.value.tracks.indices) {
            return false
        }
        return setActiveIndex(nextIndex)
    }

    fun moveToPrevious(): Boolean {
        val nextIndex = _state.value.activeIndex - 1
        if (nextIndex !in _state.value.tracks.indices) {
            return false
        }
        return setActiveIndex(nextIndex)
    }

    fun canMoveToNext(): Boolean {
        val value = _state.value
        return value.activeIndex in value.tracks.indices && value.activeIndex < value.tracks.lastIndex
    }

    fun canMoveToPrevious(): Boolean {
        return _state.value.activeIndex > 0
    }

    fun currentMediaId(): String? {
        return _state.value.currentTrack?.id
    }

    fun setPlayWhenReady(playWhenReady: Boolean) {
        _state.value = _state.value.copy(playWhenReady = playWhenReady)
    }

    suspend fun prepareCurrent() {
        val item = _state.value.currentTrack ?: return
        ensurePrepared(item)
    }

    suspend fun playCurrent() {
        val item = _state.value.currentTrack ?: return
        if (!ensurePrepared(item)) {
            return
        }

        val source = sourceSession.currentSource() ?: return
        val sourceOpenCode = source.open()
        if (sourceOpenCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
            _state.value = _state.value.copy(statusText = "Source open failed(${sourceOpenCode.code})")
            return
        }
        if (source.seek(0L, IPlaysource.SEEK_SET) < 0L) {
            _state.value = _state.value.copy(statusText = "Source rewind failed")
            return
        }

        playbackCoordinator.launchPlay(
            source = source,
            onStarted = {
                _state.value = _state.value.copy(
                    playWhenReady = true,
                    playbackState = PLAYBACK_STATE_PLAYING,
                    positionMs = 0L,
                    statusText = "Playing: ${item.displayName}"
                )
            },
            onCompleted = { playCode ->
                if (playCode == 0) {
                    val durationMs = _state.value.durationMs
                    _state.value = _state.value.copy(
                        playWhenReady = false,
                        playbackState = PLAYBACK_STATE_STOPPED,
                        positionMs = if (durationMs > 0L) durationMs else _state.value.positionMs,
                        statusText = "Playback finished"
                    )
                } else {
                    _state.value = _state.value.copy(
                        playWhenReady = false,
                        playbackState = PLAYBACK_STATE_STOPPED,
                        statusText = PlaybackFormatter.formatPlaybackResult(
                            playCode = playCode,
                            lastError = playbackCoordinator.lastError()
                        )
                    )
                }
            },
            onFinally = {}
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
        val durationMs = _state.value.durationMs
        val bounded = if (durationMs > 0L) positionMs.coerceIn(0L, durationMs) else positionMs.coerceAtLeast(0L)
        val code = playbackCoordinator.seek(bounded)
        if (code == 0) {
            _state.value = _state.value.copy(positionMs = bounded)
        } else {
            _state.value = _state.value.copy(statusText = "Seek failed($code): ${playbackCoordinator.lastError()}")
        }
    }

    fun stop() {
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

    fun release() {
        sourceSession.release()
        playbackCoordinator.close()
    }

    private suspend fun ensurePrepared(item: PlaybackTrack): Boolean {
        if (sourceSession.isPreparedFor(item.id)) {
            return true
        }

        _state.value = _state.value.copy(isPreparing = true, statusText = "Preparing")
        return try {
            when (val preparation = trackPreparationCoordinator.prepare(item)) {
                is PreparationResult.Invalid -> {
                    _state.value = _state.value.copy(
                        playWhenReady = false,
                        isPreparing = false,
                        playbackState = PLAYBACK_STATE_STOPPED,
                        statusText = preparation.message
                    )
                    false
                }

                is PreparationResult.Ready -> {
                    sourceSession.markPrepared(item.id, preparation.source)
                    val duration = preparation.mediaMeta.durationMs.coerceAtLeast(0L)
                    _state.value = _state.value.copy(
                        isPreparing = false,
                        durationMs = duration,
                        statusText = if (duration > 0L) "Prepared" else "Prepared (duration unavailable)"
                    )
                    true
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.value = _state.value.copy(
                playWhenReady = false,
                isPreparing = false,
                playbackState = PLAYBACK_STATE_STOPPED,
                statusText = "Failed to prepare media"
            )
            false
        }
    }

    private fun clearQueue(statusText: String) {
        stop()
        sourceSession.release()
        _state.value = PlaybackProcessState(statusText = statusText)
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
            sourceSession.release()
        }

        _state.value = previous.copy(
            tracks = tracks,
            activeIndex = nextIndex,
            playWhenReady = if (changedTrack) false else previous.playWhenReady,
            playbackOutputInfo = if (changedTrack) null else previous.playbackOutputInfo,
            positionMs = if (changedTrack) 0L else previous.positionMs,
            durationMs = if (changedTrack) 0L else previous.durationMs,
            isPreparing = false,
            statusText = statusText
        )
    }
}
