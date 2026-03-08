package com.wxy.playerlite.playback.model

import android.os.Bundle
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
}
