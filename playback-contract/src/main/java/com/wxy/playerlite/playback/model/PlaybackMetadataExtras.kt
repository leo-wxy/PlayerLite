package com.wxy.playerlite.playback.model

import android.os.Bundle
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.AudioEffectPreset
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
    private const val KEY_AUDIO_EFFECT_PRESET = "audio_effect_preset"
    private const val KEY_PREFERRED_AUDIO_QUALITY = "preferred_audio_quality"
    private const val KEY_APPLIED_AUDIO_QUALITY = "applied_audio_quality"
    private const val KEY_AUDIO_META_CODEC = "audio_meta_codec"
    private const val KEY_AUDIO_META_SAMPLE_RATE = "audio_meta_sample_rate"
    private const val KEY_AUDIO_META_CHANNELS = "audio_meta_channels"
    private const val KEY_AUDIO_META_BIT_RATE = "audio_meta_bit_rate"
    private const val KEY_AUDIO_META_DURATION_MS = "audio_meta_duration_ms"
    private const val KEY_CACHE_PROGRESS_CACHED_BYTES = "cache_progress_cached_bytes"
    private const val KEY_CACHE_PROGRESS_TOTAL_BYTES = "cache_progress_total_bytes"
    private const val KEY_CACHE_PROGRESS_DISPLAY_START_RATIO = "cache_progress_display_start_ratio"
    private const val KEY_CACHE_PROGRESS_DISPLAY_RATIO = "cache_progress_display_ratio"
    private const val KEY_CACHE_PROGRESS_IS_FULLY_CACHED = "cache_progress_is_fully_cached"
    private const val KEY_CACHE_PROGRESS_IS_ESTIMATED = "cache_progress_is_estimated"

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

    fun writeAudioEffectPreset(extras: Bundle, audioEffectPreset: AudioEffectPreset) {
        extras.putString(KEY_AUDIO_EFFECT_PRESET, audioEffectPreset.wireValue)
    }

    fun writePreferredAudioQuality(extras: Bundle, audioQuality: PlaybackAudioQuality) {
        extras.putString(KEY_PREFERRED_AUDIO_QUALITY, audioQuality.wireValue)
    }

    fun writeAppliedAudioQuality(extras: Bundle, audioQuality: PlaybackAudioQuality) {
        extras.putString(KEY_APPLIED_AUDIO_QUALITY, audioQuality.wireValue)
    }

    fun writeAudioMeta(extras: Bundle, audioMeta: AudioMetaDisplay) {
        extras.putString(KEY_AUDIO_META_CODEC, audioMeta.codec)
        extras.putString(KEY_AUDIO_META_SAMPLE_RATE, audioMeta.sampleRate)
        extras.putString(KEY_AUDIO_META_CHANNELS, audioMeta.channels)
        extras.putString(KEY_AUDIO_META_BIT_RATE, audioMeta.bitRate)
        extras.putLong(KEY_AUDIO_META_DURATION_MS, audioMeta.durationMs)
    }

    fun writeCacheProgress(extras: Bundle, cacheProgress: PlaybackCacheProgressSnapshot) {
        extras.putLong(KEY_CACHE_PROGRESS_CACHED_BYTES, cacheProgress.cachedBytes.coerceAtLeast(0L))
        if (cacheProgress.totalBytes != null && cacheProgress.totalBytes > 0L) {
            extras.putLong(KEY_CACHE_PROGRESS_TOTAL_BYTES, cacheProgress.totalBytes)
        } else {
            extras.remove(KEY_CACHE_PROGRESS_TOTAL_BYTES)
        }
        extras.putFloat(KEY_CACHE_PROGRESS_DISPLAY_START_RATIO, cacheProgress.normalizedDisplayStartRatio)
        extras.putFloat(KEY_CACHE_PROGRESS_DISPLAY_RATIO, cacheProgress.normalizedDisplayRatio)
        extras.putBoolean(KEY_CACHE_PROGRESS_IS_FULLY_CACHED, cacheProgress.isFullyCached)
        extras.putBoolean(KEY_CACHE_PROGRESS_IS_ESTIMATED, cacheProgress.isEstimated)
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

    fun readAudioEffectPreset(extras: Bundle?): AudioEffectPreset? {
        if (extras == null || !extras.containsKey(KEY_AUDIO_EFFECT_PRESET)) {
            return null
        }
        return AudioEffectPreset.fromWireValue(extras.getString(KEY_AUDIO_EFFECT_PRESET))
    }

    fun readPreferredAudioQuality(extras: Bundle?): PlaybackAudioQuality? {
        if (extras == null || !extras.containsKey(KEY_PREFERRED_AUDIO_QUALITY)) {
            return null
        }
        return PlaybackAudioQuality.fromWireValue(extras.getString(KEY_PREFERRED_AUDIO_QUALITY))
    }

    fun readAppliedAudioQuality(extras: Bundle?): PlaybackAudioQuality? {
        if (extras == null || !extras.containsKey(KEY_APPLIED_AUDIO_QUALITY)) {
            return null
        }
        return PlaybackAudioQuality.fromWireValue(extras.getString(KEY_APPLIED_AUDIO_QUALITY))
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

    fun readCacheProgress(extras: Bundle?): PlaybackCacheProgressSnapshot? {
        if (extras == null || !extras.containsKey(KEY_CACHE_PROGRESS_DISPLAY_RATIO)) {
            return null
        }
        val totalBytes = if (extras.containsKey(KEY_CACHE_PROGRESS_TOTAL_BYTES)) {
            extras.getLong(KEY_CACHE_PROGRESS_TOTAL_BYTES).takeIf { it > 0L }
        } else {
            null
        }
        val isFullyCached = extras.getBoolean(KEY_CACHE_PROGRESS_IS_FULLY_CACHED, false)
        return PlaybackCacheProgressSnapshot(
            cachedBytes = extras.getLong(KEY_CACHE_PROGRESS_CACHED_BYTES, 0L).coerceAtLeast(0L),
            totalBytes = totalBytes,
            displayStartRatio = if (isFullyCached) {
                0f
            } else {
                extras.getFloat(KEY_CACHE_PROGRESS_DISPLAY_START_RATIO, 0f).coerceIn(0f, 1f)
            },
            displayRatio = if (isFullyCached) {
                1f
            } else {
                extras.getFloat(KEY_CACHE_PROGRESS_DISPLAY_RATIO, 0f).coerceIn(0f, 1f)
            },
            isFullyCached = isFullyCached,
            isEstimated = extras.getBoolean(KEY_CACHE_PROGRESS_IS_ESTIMATED, false)
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
