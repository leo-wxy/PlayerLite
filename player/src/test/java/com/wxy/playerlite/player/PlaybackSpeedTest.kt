package com.wxy.playerlite.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedTest {
    @Test
    fun fromIndex_returnsDiscreteSpeedOption() {
        val option = PlaybackSpeed.fromIndex(7)

        assertEquals(7, option.index)
        assertEquals(1.2f, option.value)
        assertEquals("1.2X", option.label)
    }

    @Test
    fun normalizeValue_roundsToNearestSupportedStep() {
        assertEquals(0.5f, PlaybackSpeed.normalizeValue(0.42f))
        assertEquals(1.3f, PlaybackSpeed.normalizeValue(1.26f))
        assertEquals(2.0f, PlaybackSpeed.normalizeValue(2.21f))
    }

    @Test
    fun defaultOption_isOneX() {
        assertEquals(5, PlaybackSpeed.DEFAULT.index)
        assertEquals(1.0f, PlaybackSpeed.DEFAULT.value)
        assertEquals("1.0X", PlaybackSpeed.DEFAULT.label)
    }
}
