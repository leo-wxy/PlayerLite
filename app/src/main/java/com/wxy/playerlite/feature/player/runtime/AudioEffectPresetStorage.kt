package com.wxy.playerlite.feature.player.runtime

import android.content.SharedPreferences
import com.wxy.playerlite.player.AudioEffectPreset

internal class AudioEffectPresetStorage(
    private val preferences: SharedPreferences
) {
    fun read(): AudioEffectPreset {
        return AudioEffectPreset.fromWireValue(
            preferences.getString(KEY_AUDIO_EFFECT_PRESET, null)
        )
    }

    fun write(preset: AudioEffectPreset) {
        preferences.edit()
            .putString(KEY_AUDIO_EFFECT_PRESET, preset.wireValue)
            .apply()
    }

    internal companion object {
        const val PREFERENCES_NAME = "player_playback_preferences"
        const val KEY_AUDIO_EFFECT_PRESET = "audio_effect_preset"
    }
}
