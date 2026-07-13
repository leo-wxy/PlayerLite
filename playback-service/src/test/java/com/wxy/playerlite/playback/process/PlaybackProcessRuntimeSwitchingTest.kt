package com.wxy.playerlite.playback.process

import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.playback.model.PlaybackMode
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sun.misc.Unsafe

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
        sourceSession.markPlaybackStarting(itemId = "track-1")
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
        assertEquals(0, player.resumeCalls)
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
        sourceSession.markPlaybackStarting(itemId = "track-1")
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
    fun playCurrent_whenSourceWasJustPrepared_shouldNotCancelInitialPrefetchWithRewind() = runBlocking {
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
                isPreparing = false
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "sourceSession", sourceSession)
        setField(runtime, "playbackCoordinator", playbackCoordinator)

        runtime.playCurrent()

        assertEquals(1, preparedSource.openCalls)
        assertEquals(0, preparedSource.seekCalls)
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
        sourceSession.markPlaybackStarting(itemId = "track-1")
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

    @Test
    fun launchDeferredPlaybackSeek_whenSameTrackHasNewGeneration_shouldIgnoreStaleFallback() = runBlocking {
        val runtime = PlaybackProcessRuntime(
            appContext = org.robolectric.RuntimeEnvironment.getApplication(),
            serviceScope = serviceScope,
            nativePlayerFactory = { SeekFailingNativePlayer() }
        )
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
                isPreparing = true,
                positionMs = 1_000L
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "activePlaybackTrackId", "track-1")
        setField(runtime, "pendingSeekPositionMs", 1_000L)

        invokeDeferredSeek(
            runtime = runtime,
            requestedTrackId = "track-1",
            displayName = "第一首",
            targetPositionMs = 1_000L
        )

        setField(runtime, "pendingSeekPositionMs", 5_000L)
        state.value = state.value.copy(
            isPreparing = true,
            positionMs = 5_000L,
            statusText = "Buffering"
        )
        setOptionalGeneration(runtime, generation = 2L)

        delay(450L)

        assertEquals(5_000L, state.value.positionMs)
        assertTrue(state.value.isPreparing)
        assertFalse(state.value.statusText.startsWith("Playing:"))
    }

    @Test
    fun moveToPrevious_shouldWrapFromFirstTrackWhenQueueHasMultipleItems() {
        val runtime = unsafe.allocateInstance(PlaybackProcessRuntime::class.java) as PlaybackProcessRuntime
        val player = FakeNativePlayer()
        val playbackCoordinator = PlaybackCoordinator(player = player, scope = serviceScope)
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
                playbackMode = PlaybackMode.SINGLE_LOOP
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "sourceSession", PreparedSourceSession())
        setField(runtime, "playbackCoordinator", playbackCoordinator)

        val changed = runtime.moveToPrevious()

        assertTrue(changed)
        assertEquals(1, state.value.activeIndex)
    }

    @Test
    fun moveToNext_shouldWrapFromTailWhenQueueHasMultipleItems() {
        val runtime = unsafe.allocateInstance(PlaybackProcessRuntime::class.java) as PlaybackProcessRuntime
        val player = FakeNativePlayer()
        val playbackCoordinator = PlaybackCoordinator(player = player, scope = serviceScope)
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
                activeIndex = 1,
                playbackMode = PlaybackMode.SHUFFLE
            )
        )

        setField(runtime, "_state", state)
        setField(runtime, "sourceSession", PreparedSourceSession())
        setField(runtime, "playbackCoordinator", playbackCoordinator)

        val changed = runtime.moveToNext()

        assertTrue(changed)
        assertEquals(0, state.value.activeIndex)
    }

    private fun setField(target: Any, name: String, value: Any) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun invokeDeferredSeek(
        runtime: PlaybackProcessRuntime,
        requestedTrackId: String,
        displayName: String,
        targetPositionMs: Long
    ) {
        val method = PlaybackProcessRuntime::class.java.getDeclaredMethod(
            "launchDeferredPlaybackSeek",
            String::class.java,
            Long::class.javaPrimitiveType,
            String::class.java,
            Long::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(runtime, requestedTrackId, 1L, displayName, targetPositionMs)
    }

    private fun setOptionalGeneration(target: Any, generation: Long) {
        val field = runCatching {
            target.javaClass.getDeclaredField("playbackGeneration")
        }.getOrNull() ?: return
        field.isAccessible = true
        when (val current = field.get(target)) {
            is java.util.concurrent.atomic.AtomicLong -> current.set(generation)
            is Long -> field.setLong(target, generation)
        }
    }

    private class FakeNativePlayer : INativePlayer {
        var stopCalls = 0
        var resumeCalls = 0

        override fun setProgressListener(listener: ((Long) -> Unit)?) = Unit

        override fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?) = Unit

        override fun setPlaybackSpeed(speed: Float): Int = 0

        override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int = 0

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

    private class SeekFailingNativePlayer : INativePlayer {
        override fun setProgressListener(listener: ((Long) -> Unit)?) = Unit

        override fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?) = Unit

        override fun setPlaybackSpeed(speed: Float): Int = 0

        override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int = 0

        override fun playFromSource(source: IPlaysource): Int = 0

        override fun pause(): Int = 0

        override fun resume(): Int = 0

        override fun seek(positionMs: Long): Int = -1

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

        override fun stop() = Unit

        override fun close() = Unit

        override fun lastError(): String = "seek failed"
    }

    private companion object {
        val unsafe: Unsafe by lazy {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as Unsafe
        }
    }
}
