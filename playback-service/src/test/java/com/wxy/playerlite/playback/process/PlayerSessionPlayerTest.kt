package com.wxy.playerlite.playback.process

import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.player.AudioMetaDisplay
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
                        music = MusicInfo(
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
                        music = MusicInfo(
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
                        music = MusicInfo(
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
                        music = MusicInfo(
                            id = "track-1",
                            title = "Track 1",
                            playbackUri = "https://example.com/track-1.mp3"
                        )
                    ),
                    PlaybackTrack(
                        music = MusicInfo(
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

    private companion object {
        val unsafe: Unsafe by lazy {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as Unsafe
        }
    }
}
