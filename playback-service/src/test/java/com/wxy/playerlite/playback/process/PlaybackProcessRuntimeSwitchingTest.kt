package com.wxy.playerlite.playback.process

import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.player.AudioMeta
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.INativePlayer
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.player.source.IPlaysource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sun.misc.Unsafe

@RunWith(RobolectricTestRunner::class)
class PlaybackProcessRuntimeSwitchingTest {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        serviceScope.cancel()
    }

    @Test
    fun setActiveIndex_whenTrackChanges_shouldStopPlaybackBeforeReleasingPreparedSource() {
        val runtime = unsafe.allocateInstance(PlaybackProcessRuntime::class.java) as PlaybackProcessRuntime
        val player = FakeNativePlayer()
        val playbackCoordinator = PlaybackCoordinator(player = player, scope = serviceScope)
        val sourceSession = PreparedSourceSession()
        val preparedSource = FakePlaySource()
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
                playWhenReady = true,
                playbackState = PLAYBACK_STATE_PLAYING
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "sourceSession", sourceSession)
        setField(runtime, "playbackCoordinator", playbackCoordinator)

        val changed = runtime.setActiveIndex(1)

        assertTrue(changed)
        assertEquals(1, player.stopCalls)
        assertEquals(1, player.resumeCalls)
        assertEquals(1, preparedSource.abortCalls)
        assertEquals(1, preparedSource.closeCalls)
    }

    @Test
    fun playCurrent_whenSameTrackIsPreparing_shouldRelaunchInsteadOfSkipping() = runBlocking {
        val runtime = unsafe.allocateInstance(PlaybackProcessRuntime::class.java) as PlaybackProcessRuntime
        val player = FakeNativePlayer()
        val playbackCoordinator = PlaybackCoordinator(
            player = player,
            scope = serviceScope,
            queryDispatcher = Dispatchers.Main.immediate
        )
        val sourceSession = PreparedSourceSession()
        val preparedSource = FakePlaySource()
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
                playWhenReady = true,
                playbackState = PLAYBACK_STATE_STOPPED,
                isPreparing = true
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "sourceSession", sourceSession)
        setField(runtime, "playbackCoordinator", playbackCoordinator)

        runtime.playCurrent()

        assertEquals(1, preparedSource.openCalls)
        assertEquals(1, preparedSource.seekCalls)
        assertEquals(1, player.stopCalls)
        assertTrue(player.resumeCalls >= 1)
    }

    @Test
    fun playCurrent_whenPlaybackStateLooksPlayingButNoActivePlaybackTrack_shouldRelaunch() = runBlocking {
        val runtime = unsafe.allocateInstance(PlaybackProcessRuntime::class.java) as PlaybackProcessRuntime
        val player = FakeNativePlayer()
        val playbackCoordinator = PlaybackCoordinator(
            player = player,
            scope = serviceScope,
            queryDispatcher = Dispatchers.Main.immediate
        )
        val sourceSession = PreparedSourceSession()
        val preparedSource = FakePlaySource()
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
                playWhenReady = true,
                playbackState = PLAYBACK_STATE_PLAYING,
                isPreparing = false
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "sourceSession", sourceSession)
        setField(runtime, "playbackCoordinator", playbackCoordinator)

        runtime.playCurrent()

        assertEquals(1, preparedSource.openCalls)
        assertEquals(1, preparedSource.seekCalls)
        assertEquals(1, player.stopCalls)
        assertTrue(player.resumeCalls >= 1)
    }

    private fun setField(target: Any, name: String, value: Any) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private class FakeNativePlayer : INativePlayer {
        var stopCalls = 0
        var resumeCalls = 0

        override fun setProgressListener(listener: ((Long) -> Unit)?) = Unit

        override fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?) = Unit

        override fun setPlaybackSpeed(speed: Float): Int = 0

        override fun playFromSource(source: IPlaysource): Int = 0

        override fun pause(): Int = 0

        override fun resume(): Int {
            resumeCalls += 1
            return 0
        }

        override fun seek(positionMs: Long): Int = 0

        override fun getDurationFromSource(source: IPlaysource): Long = 0L

        override fun loadAudioMetaFromSource(source: IPlaysource): AudioMeta {
            return AudioMeta(
                codec = "aac",
                sampleRateHz = 44_100,
                channels = 2,
                bitRate = 128_000L,
                durationMs = 10_000L
            )
        }

        override fun loadAudioMetaDisplayFromSource(source: IPlaysource): AudioMetaDisplay {
            return AudioMetaDisplay(
                codec = "aac",
                sampleRate = "44100 Hz",
                channels = "2",
                bitRate = "128 kbps",
                durationMs = 10_000L
            )
        }

        override fun playbackState(): Int = PLAYBACK_STATE_PLAYING

        override fun stop() {
            stopCalls += 1
        }

        override fun close() = Unit

        override fun lastError(): String = "ok"
    }

    private class FakePlaySource : IPlaysource {
        var openCalls = 0
        var abortCalls = 0
        var closeCalls = 0
        var seekCalls = 0

        override val sourceId: String = "prepared-source"

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
            seekCalls += 1
            return 0L
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
