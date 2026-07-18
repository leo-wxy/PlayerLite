package com.wxy.playerlite.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.wxy.playerlite.player.source.IPlaysource

class NativePlayer : INativePlayer {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var progressListener: ((Long) -> Unit)? = null
    @Volatile
    private var playbackOutputInfoListener: ((PlaybackOutputInfo) -> Unit)? = null
    private val startupMetricsLock = Any()
    private var startupSourceId: String = ""
    private var startupStartNs: Long = 0L
    private var outputConfigLogged = false
    private var firstProgressLogged = false
    @Suppress("unused")
    private var nativeContextHandle: Long = 0L

    private external fun nativePlayFromSource(source: IPlaysource): Int

    private external fun nativePause(): Int

    private external fun nativeResume(): Int

    private external fun nativeSetPlaybackSpeed(speed: Float): Int

    private external fun nativeSetAudioEffectPreset(audioEffectPresetCode: Int): Int

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

    override fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?) {
        playbackOutputInfoListener = listener
    }

    override fun playFromSource(source: IPlaysource): Int {
        synchronized(startupMetricsLock) {
            startupSourceId = source.sourceId
            startupStartNs = System.nanoTime()
            outputConfigLogged = false
            firstProgressLogged = false
        }
        safeLogI("play start: source=${source.sourceId}")
        return nativePlayFromSource(source)
    }

    override fun setPlaybackSpeed(speed: Float): Int {
        return nativeSetPlaybackSpeed(speed)
    }

    override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int {
        return nativeSetAudioEffectPreset(audioEffectPreset.nativeCode)
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
        return nativeGetAudioMetadataFromSource(source) ?: AudioMeta(
            codec = "-",
            sampleRateHz = 0,
            channels = 0,
            bitRate = 0L,
            durationMs = 0L
        )
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
        playbackOutputInfoListener = null
    }

    override fun lastError(): String {
        return nativeLastError()
    }

    private fun toDisplay(meta: AudioMeta): AudioMetaDisplay {
        return AudioMetaDisplay(
            codec = meta.codec.ifBlank { "-" },
            sampleRate = if (meta.sampleRateHz > 0) "${meta.sampleRateHz} Hz" else "-",
            channels = if (meta.channels > 0) meta.channels.toString() else "-",
            bitRate = formatBitRateKbps(meta.bitRate),
            durationMs = meta.durationMs
        )
    }

    private fun formatBitRateKbps(bitRate: Long): String {
        if (bitRate <= 0L) {
            return "-"
        }
        val kbpsTimes10 = (bitRate * 10L + 500L) / 1000L
        val whole = kbpsTimes10 / 10L
        val decimal = kbpsTimes10 % 10L
        return if (decimal == 0L) {
            "${whole} kbps"
        } else {
            "${whole}.${decimal} kbps"
        }
    }

    @Suppress("unused")
    fun onNativeProgress(progressMs: Long) {
        reportStartupMilestone("first_progress", progressMs)
        val listener = progressListener ?: return
        mainHandler.post {
            listener(progressMs)
        }
    }

    @Suppress("unused")
    fun onNativeOutputConfig(
        inputSampleRateHz: Int,
        inputChannels: Int,
        inputEncodingCode: Int,
        outputSampleRateHz: Int,
        outputChannels: Int,
        outputEncodingCode: Int,
        usesResampler: Boolean
    ) {
        reportStartupMilestone("output_config")
        val listener = playbackOutputInfoListener ?: return
        val info = PlaybackOutputInfo(
            inputSampleRateHz = inputSampleRateHz,
            inputChannels = inputChannels,
            inputEncoding = encodeName(inputEncodingCode),
            outputSampleRateHz = outputSampleRateHz,
            outputChannels = outputChannels,
            outputEncoding = encodeName(outputEncodingCode),
            usesResampler = usesResampler
        )
        mainHandler.post {
            listener(info)
        }
    }

    private fun encodeName(code: Int): String {
        return when (code) {
            ENCODING_PCM_16 -> "pcm16"
            ENCODING_PCM_FLOAT -> "pcmFloat"
            else -> "unknown"
        }
    }

    private fun reportStartupMilestone(name: String, progressMs: Long? = null) {
        val message = synchronized(startupMetricsLock) {
            val shouldLog = when (name) {
                "output_config" -> markFirstOutputConfig()
                "first_progress" -> markFirstProgress()
                else -> false
            }
            if (!shouldLog || startupStartNs <= 0L) {
                return
            }
            val elapsedMs = (System.nanoTime() - startupStartNs) / 1_000_000L
            buildString {
                append("startup milestone=")
                append(name)
                append(" source=")
                append(startupSourceId)
                append(" elapsedMs=")
                append(elapsedMs)
                if (progressMs != null) {
                    append(" progressMs=")
                    append(progressMs)
                }
            }
        }
        safeLogI(message)
    }

    private fun markFirstOutputConfig(): Boolean {
        if (outputConfigLogged) {
            return false
        }
        outputConfigLogged = true
        return true
    }

    private fun markFirstProgress(): Boolean {
        if (firstProgressLogged) {
            return false
        }
        firstProgressLogged = true
        return true
    }

    private fun safeLogI(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    companion object {
        private const val TAG = "NativePlayer"
        private const val ENCODING_PCM_16 = 0
        private const val ENCODING_PCM_FLOAT = 1

        init {
            System.loadLibrary("player")
        }
    }
}
