package com.wxy.playerlite.playback.process

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.player.AudioMeta
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.INativePlayer
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.player.source.IPlaysource
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import sun.misc.Unsafe

@RunWith(RobolectricTestRunner::class)
class PlayerSessionPlayerTest {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        serviceScope.cancel()
    }

    @Test
    fun getState_exposesPlaybackParametersFromRuntimeSpeed() {
        val runtime = createRuntime(
            PlaybackProcessState(
                tracks = listOf(
                    PlaybackTrack(
                        playable = MusicInfo(
                            id = "track-1",
                            title = "Track 1",
                            playbackUri = "https://example.com/track-1.mp3"
                        )
                    )
                ),
                activeIndex = 0,
                playWhenReady = true,
                playbackSpeed = 2.0f,
                playbackState = PLAYBACK_STATE_PLAYING,
                positionMs = 1_000L,
                durationMs = 5_000L
            )
        )

        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)
        val playbackParameters = AtomicReference<PlaybackParameters>()

        Handler(Looper.getMainLooper()).post {
            playbackParameters.set(player.playbackParameters)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(PlaybackParameters(2.0f), playbackParameters.get())
    }

    @Test
    fun getState_writesCurrentAudioMetaIntoCurrentItemExtras() {
        val runtime = createRuntime(
            PlaybackProcessState(
                tracks = listOf(
                    PlaybackTrack(
                        playable = MusicInfo(
                            id = "track-1",
                            title = "Track 1",
                            playbackUri = "https://example.com/track-1.mp3"
                        )
                    )
                ),
                activeIndex = 0,
                audioMeta = AudioMetaDisplay(
                    codec = "FLAC",
                    sampleRate = "96000 Hz",
                    channels = "2",
                    bitRate = "Lossless",
                    durationMs = 321_000L
                )
            )
        )

        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)
        val extrasRef = AtomicReference<android.os.Bundle?>()

        Handler(Looper.getMainLooper()).post {
            extrasRef.set(player.currentMediaItem?.mediaMetadata?.extras)
        }
        shadowOf(Looper.getMainLooper()).idle()

        val audioMeta = PlaybackMetadataExtras.readAudioMeta(extrasRef.get())
        assertNotNull(audioMeta)
        assertEquals("FLAC", audioMeta?.codec)
        assertEquals(321_000L, audioMeta?.durationMs)
    }

    @Test
    fun getState_writesPlaybackModeIntoCurrentItemExtras() {
        val runtime = createRuntime(
            PlaybackProcessState(
                tracks = listOf(
                    PlaybackTrack(
                        playable = MusicInfo(
                            id = "track-1",
                            title = "Track 1",
                            playbackUri = "https://example.com/track-1.mp3"
                        )
                    )
                ),
                activeIndex = 0,
                playbackMode = PlaybackMode.SHUFFLE
            )
        )

        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)
        val extrasRef = AtomicReference<android.os.Bundle?>()

        Handler(Looper.getMainLooper()).post {
            extrasRef.set(player.currentMediaItem?.mediaMetadata?.extras)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(PlaybackMode.SHUFFLE, PlaybackMetadataExtras.readPlaybackMode(extrasRef.get()))
    }

    @Test
    fun getState_keepsNextCommandAvailableOnListLoopTail() {
        val runtime = createRuntime(
            PlaybackProcessState(
                tracks = listOf(
                    PlaybackTrack(
                        playable = MusicInfo(
                            id = "track-1",
                            title = "Track 1",
                            playbackUri = "https://example.com/track-1.mp3"
                        )
                    ),
                    PlaybackTrack(
                        playable = MusicInfo(
                            id = "track-2",
                            title = "Track 2",
                            playbackUri = "https://example.com/track-2.mp3"
                        )
                    )
                ),
                activeIndex = 1,
                playbackMode = PlaybackMode.LIST_LOOP
            )
        )

        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)
        val commandsRef = AtomicReference<Player.Commands>()

        Handler(Looper.getMainLooper()).post {
            commandsRef.set(player.availableCommands)
        }
        shadowOf(Looper.getMainLooper()).idle()

        val commands = commandsRef.get()
        assertEquals(true, commands.contains(Player.COMMAND_SEEK_TO_NEXT))
    }

    @Test
    fun handleSetMediaItems_whenReplacingQueueWhileAlreadyPlaying_shouldLaunchNewTrack() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val (runtime, fakePlayer, stateFlow) = createRuntimeWithSessionPlayerSupport(
            appContext = appContext,
            state = PlaybackProcessState(
                tracks = listOf(
                    PlaybackTrack(
                        playable = MusicInfo(
                            id = "old-track",
                            title = "Old Track",
                            playbackUri = "file:///tmp/old-track.mp3"
                        )
                    )
                ),
                activeIndex = 0,
                playWhenReady = true,
                playbackState = PLAYBACK_STATE_PLAYING
            )
        )
        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)
        val sourceFile = File.createTempFile("player-session-new", ".mp3").apply {
            writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
            deleteOnExit()
        }

        invokeHandleSetMediaItems(
            player = player,
            mediaItems = listOf(
                MusicInfo(
                    id = "new-track",
                    title = "New Track",
                    playbackUri = sourceFile.toURI().toString()
                ).toMediaItem()
            )
        )

        assertEquals("new-track", stateFlow.value.currentTrack?.id)
        assertTrue(fakePlayer.playFromSourceCalls >= 1)
    }

    @Test
    fun handleSetMediaItems_whenReplacingQueueWhilePaused_shouldNotAutoplayNewTrack() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val (runtime, fakePlayer, stateFlow) = createRuntimeWithSessionPlayerSupport(
            appContext = appContext,
            state = PlaybackProcessState(
                tracks = listOf(
                    PlaybackTrack(
                        playable = MusicInfo(
                            id = "old-track",
                            title = "Old Track",
                            playbackUri = "file:///tmp/old-track.mp3"
                        )
                    )
                ),
                activeIndex = 0,
                playWhenReady = false,
                playbackState = PLAYBACK_STATE_PAUSED
            )
        )
        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)
        val sourceFile = File.createTempFile("player-session-paused", ".mp3").apply {
            writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
            deleteOnExit()
        }

        invokeHandleSetMediaItems(
            player = player,
            mediaItems = listOf(
                MusicInfo(
                    id = "new-track",
                    title = "New Track",
                    playbackUri = sourceFile.toURI().toString()
                ).toMediaItem()
            )
        )

        assertEquals("new-track", stateFlow.value.currentTrack?.id)
        assertEquals(0, fakePlayer.playFromSourceCalls)
    }

    private fun createRuntimeWithSessionPlayerSupport(
        appContext: Context,
        state: PlaybackProcessState
    ): Triple<PlaybackProcessRuntime, FakeSessionNativePlayer, MutableStateFlow<PlaybackProcessState>> {
        val runtime = unsafe.allocateInstance(PlaybackProcessRuntime::class.java) as PlaybackProcessRuntime
        val fakePlayer = FakeSessionNativePlayer()
        val playbackCoordinator = PlaybackCoordinator(
            player = fakePlayer,
            scope = serviceScope,
            queryDispatcher = Dispatchers.Main.immediate
        )
        val trackPreparationCoordinator = TrackPreparationCoordinator(
            sourceRepository = MediaSourceRepository(appContext),
            playbackCoordinator = playbackCoordinator,
            ioDispatcher = Dispatchers.Main.immediate
        )
        val stateFlow = MutableStateFlow(state)

        setField(runtime, "_state", stateFlow)
        setField(runtime, "state", stateFlow.asStateFlow())
        setField(runtime, "serviceScope", serviceScope)
        setField(runtime, "playbackCoordinator", playbackCoordinator)
        setField(runtime, "trackPreparationCoordinator", trackPreparationCoordinator)
        setField(runtime, "sourceSession", PreparedSourceSession())
        return Triple(runtime, fakePlayer, stateFlow)
    }

    private fun invokeHandleSetMediaItems(
        player: PlayerSessionPlayer,
        mediaItems: List<androidx.media3.common.MediaItem>
    ) {
        val method = PlayerSessionPlayer::class.java.getDeclaredMethod(
            "handleSetMediaItems",
            List::class.java,
            Int::class.javaPrimitiveType,
            Long::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }

        @Suppress("UNCHECKED_CAST")
        val future = method.invoke(player, mediaItems, 0, androidx.media3.common.C.TIME_UNSET)
            as com.google.common.util.concurrent.ListenableFuture<*>
        shadowOf(Looper.getMainLooper()).idle()
        future.get()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun createRuntime(state: PlaybackProcessState): PlaybackProcessRuntime {
        val runtime = unsafe.allocateInstance(PlaybackProcessRuntime::class.java) as PlaybackProcessRuntime
        val stateFlow = MutableStateFlow(state)
        setField(runtime, "_state", stateFlow)
        setField(runtime, "state", stateFlow.asStateFlow())
        return runtime
    }

    private fun setField(target: Any, name: String, value: Any) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private class FakeSessionNativePlayer : INativePlayer {
        var playFromSourceCalls = 0

        override fun setProgressListener(listener: ((Long) -> Unit)?) = Unit

        override fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?) = Unit

        override fun setPlaybackSpeed(speed: Float): Int = 0

        override fun playFromSource(source: IPlaysource): Int {
            playFromSourceCalls += 1
            return -5
        }

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
