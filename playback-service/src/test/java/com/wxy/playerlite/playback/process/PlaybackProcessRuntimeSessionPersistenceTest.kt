package com.wxy.playerlite.playback.process

import android.content.Context
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.playback.session.SharedPreferencesPlaybackSessionStateStorage
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackProcessRuntimeSessionPersistenceTest {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        serviceScope.cancel()
    }

    @Test
    fun progressUpdates_shouldPersistCurrentItemPositionAndPlayIntent() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val storage = SharedPreferencesPlaybackSessionStateStorage.fromContext(appContext)
        storage.clear()
        val fakePlayer = FakeSessionPersistenceNativePlayer()
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { fakePlayer }
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue:persisted:track-1",
                    songId = "track-1",
                    title = "第一首",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/track-1.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.setPlayWhenReady(true)
        mutableState(runtime).value = runtime.state.value.copy(playbackState = PLAYBACK_STATE_PLAYING)

        fakePlayer.dispatchProgress(45_678L)

        val persisted = storage.read()
        assertEquals("queue:persisted:track-1", persisted?.activeItemId)
        assertEquals(45_678L, persisted?.positionMs)
        assertEquals(true, persisted?.playWhenReady)
    }

    @Test
    fun progressUpdates_shouldOnlyPersistWhenPositionAdvancesByFiveSeconds() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val storage = SharedPreferencesPlaybackSessionStateStorage.fromContext(appContext)
        storage.clear()
        val fakePlayer = FakeSessionPersistenceNativePlayer()
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { fakePlayer }
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue:persisted:track-1",
                    songId = "track-1",
                    title = "第一首",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/track-1.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.setPlayWhenReady(true)
        mutableState(runtime).value = runtime.state.value.copy(playbackState = PLAYBACK_STATE_PLAYING)

        fakePlayer.dispatchProgress(4_500L)
        assertEquals(0L, storage.read()?.positionMs)

        fakePlayer.dispatchProgress(5_000L)
        assertEquals(5_000L, storage.read()?.positionMs)
    }

    @Test
    fun clearCurrentMediaItem_shouldClearPersistedPlaybackSession() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val storage = SharedPreferencesPlaybackSessionStateStorage.fromContext(appContext)
        storage.write(
            com.wxy.playerlite.playback.session.PlaybackSessionState(
                activeItemId = "queue:persisted:track-1",
                positionMs = 12_345L,
                playWhenReady = false,
                savedAtMs = 1L
            )
        )
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeSessionPersistenceNativePlayer() }
        )

        runtime.clearCurrentMediaItem()

        assertNull(storage.read())
    }

    @Suppress("UNCHECKED_CAST")
    private fun mutableState(runtime: PlaybackProcessRuntime): MutableStateFlow<PlaybackProcessState> {
        val field = runtime.javaClass.getDeclaredField("_state")
        field.isAccessible = true
        return field.get(runtime) as MutableStateFlow<PlaybackProcessState>
    }

    private class FakeSessionPersistenceNativePlayer : INativePlayer {
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
}
