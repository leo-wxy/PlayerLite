package com.wxy.playerlite.playback.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackModeTest {
    @Test
    fun fromWireValue_mapsLegacySequentialToListLoop() {
        assertEquals(PlaybackMode.LIST_LOOP, PlaybackMode.fromWireValue("sequential"))
    }

    @Test
    fun fromWireValue_defaultsToListLoopWhenMissing() {
        assertEquals(PlaybackMode.LIST_LOOP, PlaybackMode.fromWireValue(null))
    }
}
