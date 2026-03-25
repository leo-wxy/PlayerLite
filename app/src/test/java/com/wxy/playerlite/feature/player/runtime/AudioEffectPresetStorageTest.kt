package com.wxy.playerlite.feature.player.runtime

import android.content.Context
import com.wxy.playerlite.player.AudioEffectPreset
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AudioEffectPresetStorageTest {
    private val context = RuntimeEnvironment.getApplication()
    private val preferences = context.getSharedPreferences(
        "audio_effect_preset_storage_test",
        Context.MODE_PRIVATE
    )

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @Test
    fun read_withoutStoredValue_shouldReturnDefaultPreset() {
        val storage = AudioEffectPresetStorage(preferences = preferences)

        assertEquals(AudioEffectPreset.DEFAULT, storage.read())
    }

    @Test
    fun writeThenRead_shouldRestoreStoredPreset() {
        val storage = AudioEffectPresetStorage(preferences = preferences)

        storage.write(AudioEffectPreset.WARM)

        assertEquals(AudioEffectPreset.WARM, storage.read())
    }

    @Test
    fun read_withUnknownStoredValue_shouldFallbackToDefaultPreset() {
        preferences.edit()
            .putString("audio_effect_preset", "future-preset")
            .commit()
        val storage = AudioEffectPresetStorage(preferences = preferences)

        assertEquals(AudioEffectPreset.DEFAULT, storage.read())
    }
}
