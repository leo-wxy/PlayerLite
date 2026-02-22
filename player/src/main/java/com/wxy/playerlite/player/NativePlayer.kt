package com.wxy.playerlite.player

import android.os.Handler
import android.os.Looper
import com.wxy.playerlite.player.source.IPlaysource

class NativePlayer : INativePlayer {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var progressListener: ((Long) -> Unit)? = null
    @Suppress("unused")
    private var nativeContextHandle: Long = 0L

    private external fun nativePlayFromSource(source: IPlaysource): Int

    private external fun nativePause(): Int

    private external fun nativeResume(): Int

    private external fun nativeSeek(positionMs: Long): Int

    private external fun nativeGetDurationFromSource(source: IPlaysource): Long

    private external fun nativeGetAudioMetadataFromSource(source: IPlaysource): AudioMeta?

    private external fun nativeGetPlaybackState(): Int

    private external fun nativeStop()

    private external fun nativeRelease()

    private external fun nativeLastError(): String

    override fun setProgressListener(listener: ((Long) -> Unit)?) {
        progressListener = listener
    }

    override fun playFromSource(source: IPlaysource): Int {
        return nativePlayFromSource(source)
    }

    override fun pause(): Int {
        return nativePause()
    }

    override fun resume(): Int {
        return nativeResume()
    }

    override fun seek(positionMs: Long): Int {
        return nativeSeek(positionMs)
    }

    override fun getDurationFromSource(source: IPlaysource): Long {
        return nativeGetDurationFromSource(source)
    }

    override fun loadAudioMetaFromSource(source: IPlaysource): AudioMeta {
        source.seek(0L, IPlaysource.SEEK_SET)
        var meta = nativeGetAudioMetadataFromSource(source) ?: AudioMeta(
            codec = "-",
            sampleRateHz = 0,
            channels = 0,
            bitRate = 0L,
            durationMs = 0L
        )
        if (meta.durationMs <= 0L) {
            source.seek(0L, IPlaysource.SEEK_SET)
            val durationMs = nativeGetDurationFromSource(source)
            if (durationMs > 0L) {
                meta = meta.copy(durationMs = durationMs)
            }
        }
        return meta
    }

    override fun loadAudioMetaDisplayFromSource(source: IPlaysource): AudioMetaDisplay {
        return toDisplay(loadAudioMetaFromSource(source))
    }

    override fun playbackState(): Int {
        return nativeGetPlaybackState()
    }

    override fun stop() {
        nativeStop()
    }

    override fun close() {
        nativeStop()
        nativeResume()
        nativeRelease()
        progressListener = null
    }

    override fun lastError(): String {
        return nativeLastError()
    }

    private fun toDisplay(meta: AudioMeta): AudioMetaDisplay {
        return AudioMetaDisplay(
            codec = meta.codec.ifBlank { "-" },
            sampleRate = if (meta.sampleRateHz > 0) "${meta.sampleRateHz} Hz" else "-",
            channels = if (meta.channels > 0) meta.channels.toString() else "-",
            bitRate = if (meta.bitRate > 0L) "${meta.bitRate} bps" else "-",
            durationMs = meta.durationMs
        )
    }

    @Suppress("unused")
    fun onNativeProgress(progressMs: Long) {
        val listener = progressListener ?: return
        mainHandler.post {
            listener(progressMs)
        }
    }

    companion object {
        init {
            System.loadLibrary("player")
        }
    }
}
