package com.wxy.playerlite.playback.process

import com.wxy.playerlite.player.INativePlayer
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.player.source.IPlaysource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PlaybackCoordinator(
    private val player: INativePlayer,
    private val scope: CoroutineScope,
    private val queryDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private var playJob: Job? = null
    private var playbackStateJob: Job? = null
    private var playRequestToken: Long = 0L

    fun setProgressListener(listener: ((Long) -> Unit)?) {
        player.setProgressListener(listener)
    }

    fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?) {
        player.setPlaybackOutputInfoListener(listener)
    }

    fun setPlaybackSpeed(speed: Float): Int {
        return player.setPlaybackSpeed(speed)
    }

    fun launchPlay(
        source: IPlaysource,
        onStarted: () -> Unit,
        onCompleted: (Int) -> Unit,
        onFinally: () -> Unit
    ) {
        val token = ++playRequestToken
        playJob?.cancel()
        playJob = scope.launch {
            player.stop()
            player.resume()
            onStarted()

            try {
                val playCode = playFromSourceWithRetry(source)
                if (token == playRequestToken) {
                    onCompleted(playCode)
                }
            } catch (_: CancellationException) {
                // Caller handles cancellation state updates.
            } finally {
                if (token == playRequestToken) {
                    playJob = null
                    onFinally()
                }
            }
        }
    }

    fun stopPlayback() {
        player.stop()
        player.resume()
        playJob?.cancel()
        playJob = null
    }

    fun pause(): Int {
        return player.pause()
    }

    fun resume(): Int {
        return player.resume()
    }

    fun seek(positionMs: Long): Int {
        return player.seek(positionMs)
    }

    fun lastError(): String {
        return player.lastError()
    }

    suspend fun loadAudioMetaDisplayFromSource(source: IPlaysource) = withContext(queryDispatcher) {
        player.loadAudioMetaDisplayFromSource(source)
    }

    fun startPlaybackStateObserver(
        intervalMs: Long,
        onPlaybackState: (Int) -> Unit
    ) {
        playbackStateJob?.cancel()
        playbackStateJob = scope.launch {
            while (isActive) {
                onPlaybackState(queryPlaybackState())
                delay(intervalMs)
            }
        }
    }

    fun close() {
        playbackStateJob?.cancel()
        playbackStateJob = null
        stopPlayback()
        player.close()
    }

    private suspend fun queryPlaybackState(): Int = withContext(queryDispatcher) {
        player.playbackState()
    }

    private suspend fun playFromSourceWithRetry(source: IPlaysource): Int {
        var lastCode = -2005
        repeat(3) { attempt ->
            val code = withContext(queryDispatcher) {
                player.playFromSource(source)
            }
            if (code != -2005) {
                return code
            }

            lastCode = code
            player.stop()
            player.resume()
            if (attempt < 2) {
                delay(120)
            }
        }
        return lastCode
    }
}
