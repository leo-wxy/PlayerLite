package com.wxy.playerlite.playback.process

import android.content.Context
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.AudioMeta
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.INativePlayer
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.player.source.IPlaysource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import sun.misc.Unsafe

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackProcessRuntimeAudioEffectTest {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        serviceScope.cancel()
    }

    @Test
    fun setAudioEffectPreset_whenNativeAccepts_shouldPersistPreset() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val preferences = appContext.getSharedPreferences(
            "player_playback_preferences",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val nativePlayer = FakeAudioEffectNativePlayer(setAudioEffectResult = 0)
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { nativePlayer }
        )

        val success = runtime.setAudioEffectPreset(AudioEffectPreset.BASS_BOOST)

        assertTrue(success)
        assertEquals(AudioEffectPreset.BASS_BOOST, runtime.state.value.audioEffectPreset)
        assertEquals(AudioEffectPreset.BASS_BOOST, nativePlayer.lastRequestedPreset)
        assertEquals("bass-boost", preferences.getString("audio_effect_preset", null))
    }

    @Test
    fun setAudioEffectPreset_whenNativeRejects_shouldKeepPreviousPreset() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val preferences = appContext.getSharedPreferences(
            "player_playback_preferences",
            Context.MODE_PRIVATE
        )
        preferences.edit()
            .clear()
            .putString("audio_effect_preset", "warm")
            .commit()
        val nativePlayer = FakeAudioEffectNativePlayer(setAudioEffectResult = -7)
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { nativePlayer }
        )

        val success = runtime.setAudioEffectPreset(AudioEffectPreset.BRIGHT)

        assertFalse(success)
        assertEquals(AudioEffectPreset.WARM, runtime.state.value.audioEffectPreset)
        assertEquals(AudioEffectPreset.BRIGHT, nativePlayer.lastRequestedPreset)
        assertEquals("Set audio effect failed(-7): audio-effect-error", runtime.state.value.statusText)
        assertEquals("warm", preferences.getString("audio_effect_preset", null))
    }

    @Test
    fun init_shouldRestorePersistedAudioEffectPresetFromSharedPreferences() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val preferences = appContext.getSharedPreferences(
            "player_playback_preferences",
            Context.MODE_PRIVATE
        )
        preferences.edit()
            .clear()
            .putString("audio_effect_preset", "vocal-boost")
            .commit()

        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeAudioEffectNativePlayer(setAudioEffectResult = 0) }
        )

        assertEquals(AudioEffectPreset.VOCAL_BOOST, runtime.state.value.audioEffectPreset)
    }

    private fun setField(target: Any, name: String, value: Any) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private class FakeAudioEffectNativePlayer(
        private val setAudioEffectResult: Int
    ) : INativePlayer {
        var lastRequestedPreset: AudioEffectPreset? = null

        override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int {
            lastRequestedPreset = audioEffectPreset
            return setAudioEffectResult
        }

        override fun setProgressListener(listener: ((Long) -> Unit)?) = Unit

        override fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?) = Unit

        override fun setPlaybackSpeed(speed: Float): Int = 0

        override fun playFromSource(source: IPlaysource): Int = 0

        override fun pause(): Int = 0

        override fun resume(): Int = 0

        override fun seek(positionMs: Long): Int = 0

        override fun getDurationFromSource(source: IPlaysource): Long = 0L

        override fun loadAudioMetaFromSource(source: IPlaysource): AudioMeta {
            return AudioMeta(
                codec = "aac",
                sampleRateHz = 44_100,
                channels = 2,
                bitRate = 128_000L,
                durationMs = 0L
            )
        }

        override fun loadAudioMetaDisplayFromSource(source: IPlaysource): AudioMetaDisplay {
            return AudioMetaDisplay(
                codec = "aac",
                sampleRate = "44100 Hz",
                channels = "2",
                bitRate = "128 kbps",
                durationMs = 0L
            )
        }

        override fun playbackState(): Int = PLAYBACK_STATE_STOPPED

        override fun stop() = Unit

        override fun close() = Unit

        override fun lastError(): String = "audio-effect-error"
    }

    private companion object {
        val unsafe: Unsafe by lazy {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as Unsafe
        }
    }
}
