package com.wxy.playerlite.playback.model

import android.os.Bundle
import com.wxy.playerlite.player.AudioMetaDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackMetadataExtrasTest {
    @Test
    fun readPlaybackSpeed_returnsNullWhenAbsent() {
        assertNull(PlaybackMetadataExtras.readPlaybackSpeed(Bundle()))
    }

    @Test
    fun writePlaybackSpeed_roundTripsValue() {
        val extras = Bundle()

        PlaybackMetadataExtras.writePlaybackSpeed(extras, 1.3f)

        assertEquals(1.3f, PlaybackMetadataExtras.readPlaybackSpeed(extras) ?: 0f, 0f)
    }

    @Test
    fun writeAudioMeta_roundTripsValue() {
        val extras = Bundle()
        val audioMeta = AudioMetaDisplay(
            codec = "AAC",
            sampleRate = "44100 Hz",
            channels = "2",
            bitRate = "256 kbps",
            durationMs = 123_000L
        )

        PlaybackMetadataExtras.writeAudioMeta(extras, audioMeta)

        assertEquals(audioMeta, PlaybackMetadataExtras.readAudioMeta(extras))
    }
}
