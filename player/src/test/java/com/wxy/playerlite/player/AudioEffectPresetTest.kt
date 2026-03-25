package com.wxy.playerlite.player

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioEffectPresetTest {
    @Test
    fun fromWireValue_returnsMatchingPresetAndFallsBackToOff() {
        assertEquals(AudioEffectPreset.BASS_BOOST, AudioEffectPreset.fromWireValue("bass-boost"))
        assertEquals(AudioEffectPreset.WARM, AudioEffectPreset.fromWireValue("warm"))
        assertEquals(AudioEffectPreset.OFF, AudioEffectPreset.fromWireValue("unknown"))
        assertEquals(AudioEffectPreset.OFF, AudioEffectPreset.fromWireValue(null))
    }

    @Test
    fun presets_exposeStableDisplayNames() {
        assertEquals("原声", AudioEffectPreset.OFF.displayName)
        assertEquals("低音增强", AudioEffectPreset.BASS_BOOST.displayName)
        assertEquals("人声增强", AudioEffectPreset.VOCAL_BOOST.displayName)
        assertEquals("清亮高频", AudioEffectPreset.BRIGHT.displayName)
        assertEquals("温暖柔和", AudioEffectPreset.WARM.displayName)
    }

    @Test
    fun nativeCode_isStableAndFallsBackToOff() {
        assertEquals(0, AudioEffectPreset.OFF.nativeCode)
        assertEquals(1, AudioEffectPreset.BASS_BOOST.nativeCode)
        assertEquals(2, AudioEffectPreset.VOCAL_BOOST.nativeCode)
        assertEquals(3, AudioEffectPreset.BRIGHT.nativeCode)
        assertEquals(4, AudioEffectPreset.WARM.nativeCode)

        assertEquals(AudioEffectPreset.OFF, AudioEffectPreset.fromNativeCode(-1))
        assertEquals(AudioEffectPreset.WARM, AudioEffectPreset.fromNativeCode(4))
        assertEquals(AudioEffectPreset.OFF, AudioEffectPreset.fromNativeCode(999))
    }
}
