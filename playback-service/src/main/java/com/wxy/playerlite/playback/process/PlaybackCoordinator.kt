package com.wxy.playerlite.playback.process

import com.wxy.playerlite.player.INativePlayer
import com.wxy.playerlite.player.AudioEffectPreset
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
    private var activeSource: IPlaysource? = null
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

    fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int {
        val typedMethod = runCatching {
            player.javaClass.getMethod(
                "setAudioEffectPreset",
                AudioEffectPreset::class.java
            )
        }.getOrNull()
        if (typedMethod != null) {
            val result = runCatching {
                typedMethod.invoke(player, audioEffectPreset) as? Int
            }.getOrNull()
            return result ?: -1
        }

        val wireMethod = runCatching {
            player.javaClass.getMethod("setAudioEffectPreset", String::class.java)
        }.getOrNull()
        if (wireMethod != null) {
            val result = runCatching {
                wireMethod.invoke(player, audioEffectPreset.wireValue) as? Int
            }.getOrNull()
            return result ?: -1
        }

        return if (audioEffectPreset == AudioEffectPreset.DEFAULT) {
            0
        } else {
            -1
        }
    }

    fun launchPlay(
        source: IPlaysource,
        onStarted: () -> Unit,
        onCompleted: (Int) -> Unit,
        onFinally: () -> Unit
    ) {
        val token = ++playRequestToken
        if (playJob?.isActive == true) {
            activeSource?.stop()
        }
        playJob?.cancel()
        playJob = scope.launch {
            player.stop()
            player.resume()
            activeSource = source
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
                    activeSource = null
                    onFinally()
                }
            }
        }
    }

    fun stopPlayback(preparedSource: IPlaysource? = null) {
        (activeSource ?: preparedSource)?.stop()
        player.stop()
        playJob?.cancel()
        playJob = null
        activeSource = null
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
