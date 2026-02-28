package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.player.PlaybackOutputInfo
import java.util.Locale

internal object PlayerUiFormatter {
    fun formatDuration(durationMs: Long): String {
        if (durationMs < 0L) {
            return "00:00"
        }
        val totalSeconds = durationMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun formatPlaybackOutputInfo(info: PlaybackOutputInfo): String {
        val inputText = "${formatSampleRate(info.inputSampleRateHz)}/${formatChannels(info.inputChannels)}/${info.inputEncoding}"
        val outputText = "${formatSampleRate(info.outputSampleRateHz)}/${formatChannels(info.outputChannels)}/${info.outputEncoding}"
        val modeText = if (info.usesResampler) "重采样" else "直通"
        return "输入 $inputText -> 输出 $outputText ($modeText)"
    }

    fun formatPlaybackResult(playCode: Int, lastError: String): String {
        return when (playCode) {
            0 -> "Playback finished"
            -2001 -> "Stopped"
            -2005 -> "Playback already in progress"
            -2006 -> "Seek is available only while playback is active"
            else -> "Playback failed($playCode): $lastError"
        }
    }

    private fun formatSampleRate(sampleRateHz: Int): String {
        return if (sampleRateHz > 0) {
            "${sampleRateHz}Hz"
        } else {
            "?Hz"
        }
    }

    private fun formatChannels(channels: Int): String {
        return if (channels > 0) {
            "${channels}ch"
        } else {
            "?ch"
        }
    }
}
