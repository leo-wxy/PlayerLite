package com.wxy.playerlite.playback.model

import android.os.Bundle
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.PlaybackOutputInfo

object PlaybackMetadataExtras {
    private const val KEY_STATUS_TEXT = "status_text"
    private const val KEY_OUTPUT_INPUT_SAMPLE_RATE = "output_input_sample_rate"
    private const val KEY_OUTPUT_INPUT_CHANNELS = "output_input_channels"
    private const val KEY_OUTPUT_INPUT_ENCODING = "output_input_encoding"
    private const val KEY_OUTPUT_SAMPLE_RATE = "output_sample_rate"
    private const val KEY_OUTPUT_CHANNELS = "output_channels"
    private const val KEY_OUTPUT_ENCODING = "output_encoding"
    private const val KEY_OUTPUT_RESAMPLER = "output_uses_resampler"
    private const val KEY_SEEK_SUPPORTED = "seek_supported"
    private const val KEY_PLAYBACK_SPEED = "playback_speed"
    private const val KEY_PLAYBACK_MODE = "playback_mode"
    private const val KEY_AUDIO_META_CODEC = "audio_meta_codec"
    private const val KEY_AUDIO_META_SAMPLE_RATE = "audio_meta_sample_rate"
    private const val KEY_AUDIO_META_CHANNELS = "audio_meta_channels"
    private const val KEY_AUDIO_META_BIT_RATE = "audio_meta_bit_rate"
    private const val KEY_AUDIO_META_DURATION_MS = "audio_meta_duration_ms"

    fun writePlaybackOutputInfo(extras: Bundle, info: PlaybackOutputInfo) {
        extras.putInt(KEY_OUTPUT_INPUT_SAMPLE_RATE, info.inputSampleRateHz)
        extras.putInt(KEY_OUTPUT_INPUT_CHANNELS, info.inputChannels)
        extras.putString(KEY_OUTPUT_INPUT_ENCODING, info.inputEncoding)
        extras.putInt(KEY_OUTPUT_SAMPLE_RATE, info.outputSampleRateHz)
        extras.putInt(KEY_OUTPUT_CHANNELS, info.outputChannels)
        extras.putString(KEY_OUTPUT_ENCODING, info.outputEncoding)
        extras.putBoolean(KEY_OUTPUT_RESAMPLER, info.usesResampler)
    }

    fun writeStatusText(extras: Bundle, statusText: String) {
        extras.putString(KEY_STATUS_TEXT, statusText)
    }

    fun writeSeekSupported(extras: Bundle, seekSupported: Boolean) {
        extras.putBoolean(KEY_SEEK_SUPPORTED, seekSupported)
    }

    fun writePlaybackSpeed(extras: Bundle, playbackSpeed: Float) {
        extras.putFloat(KEY_PLAYBACK_SPEED, playbackSpeed)
    }

    fun writePlaybackMode(extras: Bundle, playbackMode: PlaybackMode) {
        extras.putString(KEY_PLAYBACK_MODE, playbackMode.wireValue)
    }

    fun writeAudioMeta(extras: Bundle, audioMeta: AudioMetaDisplay) {
        extras.putString(KEY_AUDIO_META_CODEC, audioMeta.codec)
        extras.putString(KEY_AUDIO_META_SAMPLE_RATE, audioMeta.sampleRate)
        extras.putString(KEY_AUDIO_META_CHANNELS, audioMeta.channels)
        extras.putString(KEY_AUDIO_META_BIT_RATE, audioMeta.bitRate)
        extras.putLong(KEY_AUDIO_META_DURATION_MS, audioMeta.durationMs)
    }

    fun readStatusText(extras: Bundle?): String? {
        return extras?.getString(KEY_STATUS_TEXT)
    }

    fun readSeekSupported(extras: Bundle?): Boolean? {
        if (extras == null || !extras.containsKey(KEY_SEEK_SUPPORTED)) {
            return null
        }
        return extras.getBoolean(KEY_SEEK_SUPPORTED)
    }

    fun readPlaybackSpeed(extras: Bundle?): Float? {
        if (extras == null || !extras.containsKey(KEY_PLAYBACK_SPEED)) {
            return null
        }
        return extras.getFloat(KEY_PLAYBACK_SPEED)
    }

    fun readPlaybackMode(extras: Bundle?): PlaybackMode? {
        if (extras == null || !extras.containsKey(KEY_PLAYBACK_MODE)) {
            return null
        }
        return PlaybackMode.fromWireValue(extras.getString(KEY_PLAYBACK_MODE))
    }

    fun readAudioMeta(extras: Bundle?): AudioMetaDisplay? {
        if (extras == null || !extras.containsKey(KEY_AUDIO_META_CODEC)) {
            return null
        }
        return AudioMetaDisplay(
            codec = extras.getString(KEY_AUDIO_META_CODEC).orEmpty(),
            sampleRate = extras.getString(KEY_AUDIO_META_SAMPLE_RATE).orEmpty(),
            channels = extras.getString(KEY_AUDIO_META_CHANNELS).orEmpty(),
            bitRate = extras.getString(KEY_AUDIO_META_BIT_RATE).orEmpty(),
            durationMs = extras.getLong(KEY_AUDIO_META_DURATION_MS, 0L)
        )
    }

    fun readPlaybackOutputInfo(extras: Bundle?): PlaybackOutputInfo? {
        if (extras == null || !extras.containsKey(KEY_OUTPUT_SAMPLE_RATE)) {
            return null
        }

        return PlaybackOutputInfo(
            inputSampleRateHz = extras.getInt(KEY_OUTPUT_INPUT_SAMPLE_RATE, 0),
            inputChannels = extras.getInt(KEY_OUTPUT_INPUT_CHANNELS, 0),
            inputEncoding = extras.getString(KEY_OUTPUT_INPUT_ENCODING).orEmpty(),
            outputSampleRateHz = extras.getInt(KEY_OUTPUT_SAMPLE_RATE, 0),
            outputChannels = extras.getInt(KEY_OUTPUT_CHANNELS, 0),
            outputEncoding = extras.getString(KEY_OUTPUT_ENCODING).orEmpty(),
            usesResampler = extras.getBoolean(KEY_OUTPUT_RESAMPLER, false)
        )
    }
}
