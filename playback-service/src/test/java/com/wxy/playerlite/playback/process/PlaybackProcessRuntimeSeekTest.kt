package com.wxy.playerlite.playback.process

import android.content.Context
import com.wxy.playerlite.player.AudioMeta
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.INativePlayer
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.player.source.IPlaysource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import sun.misc.Unsafe

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackProcessRuntimeSeekTest {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        serviceScope.cancel()
    }

    @Test
    fun seekTo_whenPlaying_shouldEnterBufferingUntilNewProgressArrives() {
        val runtime = unsafe.allocateInstance(PlaybackProcessRuntime::class.java) as PlaybackProcessRuntime
        val playbackCoordinator = PlaybackCoordinator(
            player = FakeSeekNativePlayer(),
            scope = serviceScope
        )
        val state = MutableStateFlow(
            PlaybackProcessState(
                playWhenReady = true,
                playbackState = PLAYBACK_STATE_PLAYING,
                isSeekSupported = true,
                positionMs = 12_000L,
                durationMs = 240_000L
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "playbackCoordinator", playbackCoordinator)

        runtime.seekTo(90_000L)

        val updated = state.value
        assertTrue(updated.isPreparing)
        assertEquals(PLAYBACK_STATE_STOPPED, updated.playbackState)
        assertEquals(90_000L, updated.positionMs)
        assertEquals("Buffering", updated.statusText)
    }

    @Test
    fun seekTo_whenPaused_shouldUpdatePositionWithoutEnteringBuffering() {
        val runtime = unsafe.allocateInstance(PlaybackProcessRuntime::class.java) as PlaybackProcessRuntime
        val playbackCoordinator = PlaybackCoordinator(
            player = FakeSeekNativePlayer(),
            scope = serviceScope
        )
        val state = MutableStateFlow(
            PlaybackProcessState(
                playWhenReady = false,
                playbackState = PLAYBACK_STATE_PAUSED,
                isSeekSupported = true,
                positionMs = 12_000L,
                durationMs = 240_000L
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "playbackCoordinator", playbackCoordinator)

        runtime.seekTo(30_000L)

        val updated = state.value
        assertFalse(updated.isPreparing)
        assertEquals(PLAYBACK_STATE_PAUSED, updated.playbackState)
        assertEquals(30_000L, updated.positionMs)
    }

    @Test
    fun progressListener_whenPlayingAndProgressRegressesFarBehind_shouldIgnoreStaleUpdate() {
        val fakePlayer = FakeSeekNativePlayer()
        val appContext = RuntimeEnvironment.getApplication() as Context
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { fakePlayer }
        )
        val state = MutableStateFlow(
            PlaybackProcessState(
                playWhenReady = true,
                playbackState = PLAYBACK_STATE_PLAYING,
                isPreparing = false,
                isSeekSupported = true,
                positionMs = 14_610L,
                durationMs = 240_000L
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "pendingSeekPositionMs", null)

        fakePlayer.dispatchProgress(2_845L)

        val updated = state.value
        assertEquals(14_610L, updated.positionMs)
        assertEquals(PLAYBACK_STATE_PLAYING, updated.playbackState)
        assertFalse(updated.isPreparing)
        assertNull(readField(runtime, "pendingSeekPositionMs"))
    }

    @Test
    fun progressListener_whenSeekPending_shouldIgnoreProgressFarFromSeekTarget() {
        val fakePlayer = FakeSeekNativePlayer()
        val appContext = RuntimeEnvironment.getApplication() as Context
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { fakePlayer }
        )
        val state = MutableStateFlow(
            PlaybackProcessState(
                playWhenReady = true,
                playbackState = PLAYBACK_STATE_STOPPED,
                isPreparing = true,
                isSeekSupported = true,
                positionMs = 90_000L,
                durationMs = 240_000L
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "pendingSeekPositionMs", 90_000L)

        fakePlayer.dispatchProgress(85_000L)

        assertTrue(state.value.isPreparing)
        assertEquals(PLAYBACK_STATE_STOPPED, state.value.playbackState)
        assertEquals(90_000L, state.value.positionMs)
        assertEquals(90_000L, readField(runtime, "pendingSeekPositionMs"))

        fakePlayer.dispatchProgress(90_250L)

        assertFalse(state.value.isPreparing)
        assertEquals(PLAYBACK_STATE_PLAYING, state.value.playbackState)
        assertEquals(90_250L, state.value.positionMs)
        assertNull(readField(runtime, "pendingSeekPositionMs"))
    }

    private fun setField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun readField(target: Any, name: String): Any? {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target)
    }

    private class FakeSeekNativePlayer : INativePlayer {
        private var progressListener: ((Long) -> Unit)? = null

        override fun setProgressListener(listener: ((Long) -> Unit)?) {
            progressListener = listener
        }

        override fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?) = Unit

        override fun setPlaybackSpeed(speed: Float): Int = 0

        override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int = 0

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

        override fun playbackState(): Int = PLAYBACK_STATE_PLAYING

        override fun stop() = Unit

        override fun close() = Unit

        override fun lastError(): String = "ok"

        fun dispatchProgress(positionMs: Long) {
            progressListener?.invoke(positionMs)
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
