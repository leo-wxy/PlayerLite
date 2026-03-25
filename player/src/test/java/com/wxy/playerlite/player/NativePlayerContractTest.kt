package com.wxy.playerlite.player

import org.junit.Assert.assertTrue
import org.junit.Test

class NativePlayerContractTest {
    @Test
    fun nativePlayerContract_exposesAudioEffectSetter() {
        val interfaceMethods = INativePlayer::class.java.methods
        val nativePlayerMethods = NativePlayer::class.java.methods

        assertTrue(
            "INativePlayer 缺少 setAudioEffectPreset(AudioEffectPreset) 契约",
            interfaceMethods.any { method ->
                method.name == "setAudioEffectPreset" &&
                    method.parameterTypes.contentEquals(arrayOf(AudioEffectPreset::class.java))
            }
        )
        assertTrue(
            "NativePlayer 缺少 setAudioEffectPreset(AudioEffectPreset) 实现",
            nativePlayerMethods.any { method ->
                method.name == "setAudioEffectPreset" &&
                    method.parameterTypes.contentEquals(arrayOf(AudioEffectPreset::class.java))
            }
        )
    }
}
