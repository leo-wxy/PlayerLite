package com.wxy.playerlite.playback.process

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.player.AudioMeta
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.AudioEffectPreset
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
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import sun.misc.Unsafe

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
    fun getState_writesAudioEffectPresetIntoCurrentItemExtras() {
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
                audioEffectPreset = AudioEffectPreset.BRIGHT
            )
        )

        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)
        val extrasRef = AtomicReference<android.os.Bundle?>()

        Handler(Looper.getMainLooper()).post {
            extrasRef.set(player.currentMediaItem?.mediaMetadata?.extras)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            AudioEffectPreset.BRIGHT,
            PlaybackMetadataExtras.readAudioEffectPreset(extrasRef.get())
        )
    }

    @Test
    fun getState_writesPreferredAndAppliedAudioQualityIntoCurrentItemExtras() {
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
                preferredAudioQuality = PlaybackAudioQuality.HIRES,
                appliedAudioQuality = PlaybackAudioQuality.LOSSLESS
            )
        )

        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)
        val extrasRef = AtomicReference<android.os.Bundle?>()

        Handler(Looper.getMainLooper()).post {
            extrasRef.set(player.currentMediaItem?.mediaMetadata?.extras)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            PlaybackAudioQuality.HIRES,
            PlaybackMetadataExtras.readPreferredAudioQuality(extrasRef.get())
        )
        assertEquals(
            PlaybackAudioQuality.LOSSLESS,
            PlaybackMetadataExtras.readAppliedAudioQuality(extrasRef.get())
        )
    }

    @Test
    fun getState_projectsDisplayMetadataIntoCurrentItemWhilePreservingOriginalTrackMetadata() {
        val runtime = createRuntime(
            PlaybackProcessState(
                tracks = listOf(
                    PlaybackTrack(
                        playable = MusicInfo(
                            id = "track-1",
                            songId = "1969519579",
                            title = "夜曲",
                            artistNames = listOf("周杰伦"),
                            albumTitle = "十一月的萧邦",
                            playbackUri = "https://example.com/track-1.mp3"
                        )
                    )
                ),
                activeIndex = 0,
                displayTitleOverride = "为你弹奏肖邦的夜曲",
                displaySubtitleOverride = "夜曲 - 周杰伦"
            )
        )

        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)
        val titleRef = AtomicReference<String?>()
        val artistRef = AtomicReference<String?>()
        val extrasRef = AtomicReference<android.os.Bundle?>()

        Handler(Looper.getMainLooper()).post {
            titleRef.set(player.currentMediaItem?.mediaMetadata?.title?.toString())
            artistRef.set(player.currentMediaItem?.mediaMetadata?.artist?.toString())
            extrasRef.set(player.currentMediaItem?.mediaMetadata?.extras)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("为你弹奏肖邦的夜曲", titleRef.get())
        assertEquals("夜曲 - 周杰伦", artistRef.get())
        assertEquals("夜曲", extrasRef.get()?.getString("original_title"))
        assertEquals("周杰伦", extrasRef.get()?.getString("original_artist_text"))
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
    fun getState_keepsPreviousCommandAvailableOnSingleLoopHeadWhenQueueHasMultipleTracks() {
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
                activeIndex = 0,
                playbackMode = PlaybackMode.SINGLE_LOOP
            )
        )

        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)
        val commandsRef = AtomicReference<Player.Commands>()

        Handler(Looper.getMainLooper()).post {
            commandsRef.set(player.availableCommands)
        }
        shadowOf(Looper.getMainLooper()).idle()

        val commands = commandsRef.get()
        assertEquals(true, commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
    }

    @Test
    fun getState_keepsNextCommandAvailableOnShuffleTailWhenQueueHasMultipleTracks() {
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
                playbackMode = PlaybackMode.SHUFFLE
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
    fun handleSetMediaItems_whenContinuingPlaybackWithRestoredPosition_shouldStartFromRestoredPosition() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val restoredTrack = MusicInfo(
            id = "new-track",
            title = "New Track",
            playbackUri = "https://example.com/new-track.mp3"
        )
        val (runtime, fakePlayer, stateFlow) = createRuntimeWithSessionPlayerSupport(
            appContext = appContext,
            state = PlaybackProcessState(
                tracks = listOf(
                    PlaybackTrack(
                        playable = MusicInfo(
                            id = "old-track",
                            title = "Old Track",
                            playbackUri = "https://example.com/old-track.mp3"
                        )
                    )
                ),
                activeIndex = 0,
                playWhenReady = true,
                playbackState = PLAYBACK_STATE_PLAYING,
                isSeekSupported = true,
                positionMs = 12_000L,
                durationMs = 240_000L
            ),
            trackPreparer = object : TrackPreparer {
                override suspend fun prepare(
                    item: PlaybackTrack,
                    preferredAudioQuality: PlaybackAudioQuality
                ): PreparationResult {
                    return PreparationResult.Ready(
                        source = FakeSessionPreparedSource(),
                        mediaMeta = AudioMetaDisplay(
                            codec = "aac",
                            sampleRate = "44100 Hz",
                            channels = "2",
                            bitRate = "128 kbps",
                            durationMs = 240_000L
                        ),
                        isSeekSupported = true,
                        appliedAudioQuality = null
                    )
                }
            }
        )
        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)
        fakePlayer.seekResult = -6

        invokeHandleSetMediaItems(
            player = player,
            mediaItems = listOf(restoredTrack.toMediaItem()),
            startPositionMs = 54_000L
        )

        assertEquals("new-track", stateFlow.value.currentTrack?.id)
        assertTrue(fakePlayer.seekPositions.isEmpty())
        assertTrue(fakePlayer.playStartPositions.isNotEmpty())
        assertTrue(fakePlayer.playStartPositions.all { it == 54_000L })
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

    @Test
    fun handleSetMediaItems_whenRestorePrepareFails_shouldKeepQueueCurrentTrackAndPosition() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val restoredTrack = MusicInfo(
            id = "restored-track",
            title = "Restored Track",
            playbackUri = "https://example.com/restored-track.mp3"
        )
        val (runtime, _, stateFlow) = createRuntimeWithSessionPlayerSupport(
            appContext = appContext,
            state = PlaybackProcessState(
                tracks = listOf(PlaybackTrack(playable = restoredTrack)),
                activeIndex = 0,
                playWhenReady = false,
                playbackState = PLAYBACK_STATE_PAUSED,
                isSeekSupported = true,
                positionMs = 54_000L,
                durationMs = 240_000L
            ),
            trackPreparer = object : TrackPreparer {
                override suspend fun prepare(
                    item: PlaybackTrack,
                    preferredAudioQuality: PlaybackAudioQuality
                ): PreparationResult {
                    return PreparationResult.Invalid("Temporary prepare failure")
                }
            }
        )
        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)

        invokeHandleSetMediaItems(
            player = player,
            mediaItems = listOf(restoredTrack.toMediaItem()),
            startPositionMs = 54_000L
        )

        assertEquals(1, stateFlow.value.tracks.size)
        assertEquals("restored-track", stateFlow.value.currentTrack?.id)
        assertEquals(0, stateFlow.value.activeIndex)
        assertEquals(54_000L, stateFlow.value.positionMs)
    }

    @Test
    fun handleSetMediaItems_whenColdStartRestoreSeekFails_shouldStillStartFromRestoredPositionLater() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val restoredTrack = MusicInfo(
            id = "restored-track",
            title = "Restored Track",
            playbackUri = "https://example.com/restored-track.mp3"
        )
        val (runtime, fakePlayer, stateFlow) = createRuntimeWithSessionPlayerSupport(
            appContext = appContext,
            state = PlaybackProcessState(
                tracks = emptyList(),
                activeIndex = androidx.media3.common.C.INDEX_UNSET,
                playWhenReady = false,
                playbackState = PLAYBACK_STATE_STOPPED,
                isSeekSupported = false,
                positionMs = 0L,
                durationMs = 0L
            ),
            trackPreparer = object : TrackPreparer {
                override suspend fun prepare(
                    item: PlaybackTrack,
                    preferredAudioQuality: PlaybackAudioQuality
                ): PreparationResult {
                    return PreparationResult.Ready(
                        source = FakeSessionPreparedSource(),
                        mediaMeta = AudioMetaDisplay(
                            codec = "aac",
                            sampleRate = "44100 Hz",
                            channels = "2",
                            bitRate = "128 kbps",
                            durationMs = 240_000L
                        ),
                        isSeekSupported = true,
                        appliedAudioQuality = null
                    )
                }
            }
        )
        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)
        fakePlayer.seekResult = -6

        invokeHandleSetMediaItems(
            player = player,
            mediaItems = listOf(restoredTrack.toMediaItem()),
            startPositionMs = 54_000L
        )
        invokeHandleSetPlayWhenReady(player = player, playWhenReady = true)

        assertTrue(fakePlayer.seekPositions.isEmpty())
        assertTrue(fakePlayer.playStartPositions.isNotEmpty())
        assertTrue(fakePlayer.playStartPositions.all { it == 54_000L })
        assertEquals(54_000L, stateFlow.value.positionMs)
    }

    @Test
    fun handleSetPlayWhenReady_afterRestoredPosition_shouldStartFromRestoredPosition() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val restoredTrack = MusicInfo(
            id = "restored-track",
            title = "Restored Track",
            playbackUri = "https://example.com/restored-track.mp3"
        )
        val (runtime, fakePlayer, stateFlow) = createRuntimeWithSessionPlayerSupport(
            appContext = appContext,
            state = PlaybackProcessState(
                tracks = listOf(PlaybackTrack(playable = restoredTrack)),
                activeIndex = 0,
                playWhenReady = false,
                playbackState = PLAYBACK_STATE_STOPPED,
                isSeekSupported = true,
                positionMs = 54_000L,
                durationMs = 240_000L
            ),
            trackPreparer = object : TrackPreparer {
                override suspend fun prepare(
                    item: PlaybackTrack,
                    preferredAudioQuality: PlaybackAudioQuality
                ): PreparationResult {
                    return PreparationResult.Ready(
                        source = FakeSessionPreparedSource(),
                        mediaMeta = AudioMetaDisplay(
                            codec = "aac",
                            sampleRate = "44100 Hz",
                            channels = "2",
                            bitRate = "128 kbps",
                            durationMs = 240_000L
                        ),
                        isSeekSupported = true,
                        appliedAudioQuality = null
                    )
                }
            }
        )
        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)

        invokeHandleSetMediaItems(
            player = player,
            mediaItems = listOf(restoredTrack.toMediaItem()),
            startPositionMs = 54_000L
        )
        assertEquals(54_000L, stateFlow.value.positionMs)
        assertTrue(fakePlayer.seekPositions.isEmpty())

        invokeHandleSetPlayWhenReady(player = player, playWhenReady = true)

        assertTrue(fakePlayer.playFromSourceCalls >= 1)
        assertTrue(fakePlayer.playStartPositions.isNotEmpty())
        assertTrue(fakePlayer.playStartPositions.all { it == 54_000L })
        assertEquals(54_000L, stateFlow.value.positionMs)
    }

    @Test
    fun handleSetMediaItems_whenSameTrackRequestsRestoredPosition_shouldRealignPosition() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val restoredTrack = MusicInfo(
            id = "restored-track",
            title = "Restored Track",
            playbackUri = "https://example.com/restored-track.mp3"
        )
        val (runtime, fakePlayer, stateFlow) = createRuntimeWithSessionPlayerSupport(
            appContext = appContext,
            state = PlaybackProcessState(
                tracks = listOf(PlaybackTrack(playable = restoredTrack)),
                activeIndex = 0,
                playWhenReady = false,
                playbackState = PLAYBACK_STATE_STOPPED,
                isSeekSupported = true,
                positionMs = 0L,
                durationMs = 240_000L
            ),
            trackPreparer = object : TrackPreparer {
                override suspend fun prepare(
                    item: PlaybackTrack,
                    preferredAudioQuality: PlaybackAudioQuality
                ): PreparationResult {
                    return PreparationResult.Ready(
                        source = FakeSessionPreparedSource(),
                        mediaMeta = AudioMetaDisplay(
                            codec = "aac",
                            sampleRate = "44100 Hz",
                            channels = "2",
                            bitRate = "128 kbps",
                            durationMs = 240_000L
                        ),
                        isSeekSupported = true,
                        appliedAudioQuality = null
                    )
                }
            }
        )
        val player = PlayerSessionPlayer(runtime = runtime, serviceScope = serviceScope)

        invokeHandleSetMediaItems(
            player = player,
            mediaItems = listOf(restoredTrack.toMediaItem()),
            startPositionMs = 54_000L
        )

        assertEquals(54_000L, stateFlow.value.positionMs)
        assertTrue(fakePlayer.seekPositions.isEmpty())

        invokeHandleSetPlayWhenReady(player = player, playWhenReady = true)

        assertTrue(fakePlayer.playStartPositions.isNotEmpty())
        assertTrue(fakePlayer.playStartPositions.all { it == 54_000L })
    }

    private fun createRuntimeWithSessionPlayerSupport(
        appContext: Context,
        state: PlaybackProcessState,
        trackPreparer: TrackPreparer? = null
    ): Triple<PlaybackProcessRuntime, FakeSessionNativePlayer, MutableStateFlow<PlaybackProcessState>> {
        val runtime = unsafe.allocateInstance(PlaybackProcessRuntime::class.java) as PlaybackProcessRuntime
        val fakePlayer = FakeSessionNativePlayer()
        val playbackCoordinator = PlaybackCoordinator(
            player = fakePlayer,
            scope = serviceScope,
            queryDispatcher = Dispatchers.Main.immediate
        )
        val trackPreparationCoordinator = trackPreparer ?: TrackPreparationCoordinator(
            sourceRepository = MediaSourceRepository(appContext),
            playbackCoordinator = playbackCoordinator,
            ioDispatcher = Dispatchers.Main.immediate
        )
        val stateFlow = MutableStateFlow(state)

        fakePlayer.statePositionProvider = { stateFlow.value.positionMs }
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
        mediaItems: List<androidx.media3.common.MediaItem>,
        startPositionMs: Long = androidx.media3.common.C.TIME_UNSET
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
        val future = method.invoke(player, mediaItems, 0, startPositionMs)
            as com.google.common.util.concurrent.ListenableFuture<*>
        shadowOf(Looper.getMainLooper()).idle()
        future.get()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun invokeHandleSetPlayWhenReady(
        player: PlayerSessionPlayer,
        playWhenReady: Boolean
    ) {
        val method = PlayerSessionPlayer::class.java.getDeclaredMethod(
            "handleSetPlayWhenReady",
            Boolean::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }

        @Suppress("UNCHECKED_CAST")
        val future = method.invoke(player, playWhenReady)
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
        val playStartPositions = mutableListOf<Long>()
        val seekPositions = mutableListOf<Long>()
        var seekResult: Int = 0
        var statePositionProvider: (() -> Long)? = null

        override fun setProgressListener(listener: ((Long) -> Unit)?) = Unit

        override fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?) = Unit

        override fun setPlaybackSpeed(speed: Float): Int = 0

        override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int = 0

        override fun playFromSource(source: IPlaysource): Int {
            playFromSourceCalls += 1
            statePositionProvider?.invoke()?.let(playStartPositions::add)
            return -5
        }

        override fun pause(): Int = 0

        override fun resume(): Int = 0

        override fun seek(positionMs: Long): Int {
            seekPositions += positionMs
            return seekResult
        }

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

    private class FakeSessionPreparedSource : IPlaysource {
        override val sourceId: String = "prepared-source"

        override fun setSourceMode(mode: IPlaysource.SourceMode) = Unit

        override fun open(): IPlaysource.AudioSourceCode = IPlaysource.AudioSourceCode.ASC_SUCCESS

        override fun stop() = Unit

        override fun abort() = Unit

        override fun close() = Unit

        override fun size(): Long = 240_000L

        override fun cacheSize(): Long = 240_000L

        override fun supportFastSeek(): Boolean = true

        override fun read(buffer: ByteArray, size: Int): Int = -1

        override fun seek(offset: Long, whence: Int): Long = offset
    }

    private companion object {
        val unsafe: Unsafe by lazy {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null) as Unsafe
        }
    }
}
