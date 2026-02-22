package com.wxy.playerlite.player

import com.wxy.playerlite.player.source.IPlaysource

data class AudioMeta(
    val codec: String,
    val sampleRateHz: Int,
    val channels: Int,
    val bitRate: Long,
    val durationMs: Long
)

data class AudioMetaDisplay(
    val codec: String,
    val sampleRate: String,
    val channels: String,
    val bitRate: String,
    val durationMs: Long
)

data class PlaybackOutputInfo(
    val inputSampleRateHz: Int,
    val inputChannels: Int,
    val inputEncoding: String,
    val outputSampleRateHz: Int,
    val outputChannels: Int,
    val outputEncoding: String,
    val usesResampler: Boolean
)

interface INativePlayer {
    fun setProgressListener(listener: ((Long) -> Unit)?)

    fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?)

    fun playFromSource(source: IPlaysource): Int

    fun pause(): Int

    fun resume(): Int

    fun seek(positionMs: Long): Int

    fun getDurationFromSource(source: IPlaysource): Long

    fun loadAudioMetaFromSource(source: IPlaysource): AudioMeta

    fun loadAudioMetaDisplayFromSource(source: IPlaysource): AudioMetaDisplay

    fun playbackState(): Int

    fun stop()

    fun close()

    fun lastError(): String
}
