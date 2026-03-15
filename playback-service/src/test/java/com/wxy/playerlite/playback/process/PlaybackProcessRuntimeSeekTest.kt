package com.wxy.playerlite.playback.process

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
import org.robolectric.RobolectricTestRunner
import sun.misc.Unsafe

@RunWith(RobolectricTestRunner::class)
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

    private fun setField(target: Any, name: String, value: Any) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private class FakeSeekNativePlayer : INativePlayer {
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

        override fun playbackState(): Int = PLAYBACK_STATE_PLAYING

        override fun stop() = Unit

        override fun close() = Unit

        override fun lastError(): String = "ok"
    }

    private companion object {
        val unsafe: Unsafe by lazy {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as Unsafe
        }
    }
}
