package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.player.PlaybackOutputInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerUiFormatterTest {
    @Test
    fun formatDuration_formatsMinutesAndSeconds() {
        assertEquals("00:00", PlayerUiFormatter.formatDuration(0L))
        assertEquals("01:05", PlayerUiFormatter.formatDuration(65_000L))
        assertEquals("10:00", PlayerUiFormatter.formatDuration(600_000L))
    }

    @Test
    fun formatPlaybackOutputInfo_containsInputOutputAndMode() {
        val text = PlayerUiFormatter.formatPlaybackOutputInfo(
            PlaybackOutputInfo(
                inputSampleRateHz = 48_000,
                inputChannels = 2,
                inputEncoding = "pcmFloat",
                outputSampleRateHz = 44_100,
                outputChannels = 2,
                outputEncoding = "pcm16",
                usesResampler = true
            )
        )

        assertEquals("输入 48000Hz/2ch/pcmFloat -> 输出 44100Hz/2ch/pcm16 (重采样)", text)
    }

    @Test
    fun formatPlaybackResult_fallsBackToErrorMessage() {
        val text = PlayerUiFormatter.formatPlaybackResult(
            playCode = -1234,
            lastError = "native failure"
        )
        assertEquals("Playback failed(-1234): native failure", text)
    }
}
