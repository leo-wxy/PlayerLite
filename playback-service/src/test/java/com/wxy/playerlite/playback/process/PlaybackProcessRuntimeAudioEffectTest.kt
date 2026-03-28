package com.wxy.playerlite.playback.process

import android.content.Context
import com.wxy.playerlite.playback.model.MusicInfo
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

    @Test
    fun setAudioEffectPreset_whenNativeRejectsFromDefault_shouldFallbackToOff() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val preferences = appContext.getSharedPreferences(
            "player_playback_preferences",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val nativePlayer = FakeAudioEffectNativePlayer(setAudioEffectResult = -7)
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { nativePlayer }
        )

        val success = runtime.setAudioEffectPreset(AudioEffectPreset.BRIGHT)

        assertFalse(success)
        assertEquals(AudioEffectPreset.OFF, runtime.state.value.audioEffectPreset)
        assertEquals(AudioEffectPreset.BRIGHT, nativePlayer.lastRequestedPreset)
        assertEquals("off", preferences.getString("audio_effect_preset", "off"))
    }

    @Test
    fun resume_shouldReapplyPlaybackSpeedAndAudioEffectPresetBeforeResuming() {
        val runtime = unsafe.allocateInstance(PlaybackProcessRuntime::class.java) as PlaybackProcessRuntime
        val nativePlayer = FakeAudioEffectNativePlayer(setAudioEffectResult = 0)
        val playbackCoordinator = PlaybackCoordinator(
            player = nativePlayer,
            scope = serviceScope
        )
        val state = MutableStateFlow(
            PlaybackProcessState(
                playbackSpeed = 1.5f,
                audioEffectPreset = AudioEffectPreset.WARM,
                playbackState = PLAYBACK_STATE_PAUSED
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "playbackCoordinator", playbackCoordinator)

        runtime.resume()

        val updated = state.value
        assertEquals(listOf(1.5f), nativePlayer.requestedSpeeds)
        assertEquals(listOf(AudioEffectPreset.WARM), nativePlayer.requestedPresets)
        assertEquals(1, nativePlayer.resumeCalls)
        assertTrue(updated.playWhenReady)
        assertEquals(PLAYBACK_STATE_PLAYING, updated.playbackState)
        assertEquals(AudioEffectPreset.WARM, updated.audioEffectPreset)
    }

    @Test
    fun seekTo_whenPlaying_shouldKeepPlaybackSpeedAndAudioEffectPreset() {
        val runtime = unsafe.allocateInstance(PlaybackProcessRuntime::class.java) as PlaybackProcessRuntime
        val nativePlayer = FakeAudioEffectNativePlayer(setAudioEffectResult = 0)
        val playbackCoordinator = PlaybackCoordinator(
            player = nativePlayer,
            scope = serviceScope
        )
        val state = MutableStateFlow(
            PlaybackProcessState(
                playWhenReady = true,
                playbackState = PLAYBACK_STATE_PLAYING,
                isSeekSupported = true,
                positionMs = 12_000L,
                durationMs = 240_000L,
                playbackSpeed = 1.3f,
                audioEffectPreset = AudioEffectPreset.VOCAL_BOOST
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "playbackCoordinator", playbackCoordinator)

        runtime.seekTo(90_000L)

        val updated = state.value
        assertEquals(listOf(90_000L), nativePlayer.seekPositions)
        assertTrue(updated.isPreparing)
        assertEquals(90_000L, updated.positionMs)
        assertEquals(1.3f, updated.playbackSpeed, 0.0001f)
        assertEquals(AudioEffectPreset.VOCAL_BOOST, updated.audioEffectPreset)
    }

    @Test
    fun playCurrent_shouldApplyPlaybackSpeedAndAudioEffectPresetBeforeStartingPreparedTrack() {
        val runtime = unsafe.allocateInstance(PlaybackProcessRuntime::class.java) as PlaybackProcessRuntime
        val nativePlayer = FakeAudioEffectNativePlayer(
            setAudioEffectResult = 0,
            playFromSourceResult = -2001
        )
        val playbackCoordinator = PlaybackCoordinator(
            player = nativePlayer,
            scope = serviceScope,
            queryDispatcher = Dispatchers.Main.immediate
        )
        val sourceSession = PreparedSourceSession()
        val preparedSource = FakePreparedPlaySource()
        sourceSession.markPrepared(itemId = "track-1", source = preparedSource)
        val state = MutableStateFlow(
            PlaybackProcessState(
                tracks = listOf(
                    PlaybackTrack(
                        playable = MusicInfo(
                            id = "track-1",
                            title = "第一首",
                            playbackUri = "https://example.com/1.mp3"
                        )
                    )
                ),
                activeIndex = 0,
                playbackSpeed = 1.2f,
                audioEffectPreset = AudioEffectPreset.BASS_BOOST
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "playbackCoordinator", playbackCoordinator)
        setField(runtime, "sourceSession", sourceSession)

        kotlinx.coroutines.runBlocking {
            runtime.playCurrent()
        }

        assertEquals(1, preparedSource.openCalls)
        assertEquals(listOf(0L), preparedSource.seekOffsets)
        assertEquals(listOf(1.2f), nativePlayer.requestedSpeeds)
        assertEquals(listOf(AudioEffectPreset.BASS_BOOST), nativePlayer.requestedPresets)
        assertEquals(1, nativePlayer.playFromSourceCalls)
    }

    @Test
    fun playCurrent_afterTrackSwitch_shouldReapplyPlaybackSpeedAndAudioEffectPreset() {
        val runtime = unsafe.allocateInstance(PlaybackProcessRuntime::class.java) as PlaybackProcessRuntime
        val nativePlayer = FakeAudioEffectNativePlayer(
            setAudioEffectResult = 0,
            playFromSourceResult = -2001
        )
        val playbackCoordinator = PlaybackCoordinator(
            player = nativePlayer,
            scope = serviceScope,
            queryDispatcher = Dispatchers.Main.immediate
        )
        val sourceSession = PreparedSourceSession()
        val firstSource = FakePreparedPlaySource(sourceId = "track-1-source")
        sourceSession.markPrepared(itemId = "track-1", source = firstSource)
        val state = MutableStateFlow(
            PlaybackProcessState(
                tracks = listOf(
                    PlaybackTrack(
                        playable = MusicInfo(
                            id = "track-1",
                            title = "第一首",
                            playbackUri = "https://example.com/1.mp3"
                        )
                    ),
                    PlaybackTrack(
                        playable = MusicInfo(
                            id = "track-2",
                            title = "第二首",
                            playbackUri = "https://example.com/2.mp3"
                        )
                    )
                ),
                activeIndex = 0,
                playbackSpeed = 1.4f,
                audioEffectPreset = AudioEffectPreset.BRIGHT
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "playbackCoordinator", playbackCoordinator)
        setField(runtime, "sourceSession", sourceSession)

        kotlinx.coroutines.runBlocking {
            runtime.playCurrent()
        }

        val secondSource = FakePreparedPlaySource(sourceId = "track-2-source")
        val changed = runtime.setActiveIndex(1)
        sourceSession.markPrepared(itemId = "track-2", source = secondSource)

        kotlinx.coroutines.runBlocking {
            runtime.playCurrent()
        }

        assertTrue(changed)
        assertEquals(1, firstSource.abortCalls)
        assertEquals(1, firstSource.closeCalls)
        assertEquals(listOf(1.4f, 1.4f), nativePlayer.requestedSpeeds)
        assertEquals(
            listOf(AudioEffectPreset.BRIGHT, AudioEffectPreset.BRIGHT),
            nativePlayer.requestedPresets
        )
        assertEquals(2, nativePlayer.playFromSourceCalls)
        assertEquals(1, secondSource.openCalls)
    }

    private fun setField(target: Any, name: String, value: Any) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private class FakeAudioEffectNativePlayer(
        private val setAudioEffectResult: Int,
        private val playFromSourceResult: Int = 0
    ) : INativePlayer {
        var lastRequestedPreset: AudioEffectPreset? = null
        val requestedPresets = mutableListOf<AudioEffectPreset>()
        val requestedSpeeds = mutableListOf<Float>()
        val seekPositions = mutableListOf<Long>()
        var playFromSourceCalls: Int = 0
        var resumeCalls: Int = 0

        override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int {
            lastRequestedPreset = audioEffectPreset
            requestedPresets += audioEffectPreset
            return setAudioEffectResult
        }

        override fun setProgressListener(listener: ((Long) -> Unit)?) = Unit

        override fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?) = Unit

        override fun setPlaybackSpeed(speed: Float): Int {
            requestedSpeeds += speed
            return 0
        }

        override fun playFromSource(source: IPlaysource): Int {
            playFromSourceCalls += 1
            return playFromSourceResult
        }

        override fun pause(): Int = 0

        override fun resume(): Int {
            resumeCalls += 1
            return 0
        }

        override fun seek(positionMs: Long): Int {
            seekPositions += positionMs
            return 0
        }

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

    private class FakePreparedPlaySource(
        override val sourceId: String = "prepared-source"
    ) : IPlaysource {
        var openCalls: Int = 0
        var abortCalls: Int = 0
        var closeCalls: Int = 0
        val seekOffsets = mutableListOf<Long>()

        override fun setSourceMode(mode: IPlaysource.SourceMode) = Unit

        override fun open(): IPlaysource.AudioSourceCode {
            openCalls += 1
            return IPlaysource.AudioSourceCode.ASC_SUCCESS
        }

        override fun stop() = Unit

        override fun abort() {
            abortCalls += 1
        }

        override fun close() {
            closeCalls += 1
        }

        override fun size(): Long = 0L

        override fun cacheSize(): Long = 0L

        override fun supportFastSeek(): Boolean = true

        override fun read(buffer: ByteArray, size: Int): Int = 0

        override fun seek(offset: Long, whence: Int): Long {
            seekOffsets += offset
            return offset
        }
    }

    private companion object {
        val unsafe: Unsafe by lazy {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as Unsafe
        }
    }
}
