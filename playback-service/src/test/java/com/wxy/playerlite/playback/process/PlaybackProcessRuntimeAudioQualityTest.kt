package com.wxy.playerlite.playback.process

import android.content.Context
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.player.AudioMeta
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.INativePlayer
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.player.source.IPlaysource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackProcessRuntimeAudioQualityTest {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        serviceScope.cancel()
    }

    @Test
    fun init_shouldRestorePersistedPreferredAudioQualityFromSharedPreferences() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val preferences = appContext.getSharedPreferences(
            "player_playback_preferences",
            Context.MODE_PRIVATE
        )
        preferences.edit()
            .clear()
            .putString("preferred_audio_quality", "hires")
            .commit()

        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeAudioQualityNativePlayer() }
        )

        assertEquals(PlaybackAudioQuality.HIRES, runtime.state.value.preferredAudioQuality)
        assertNull(runtime.state.value.appliedAudioQuality)
    }

    @Test
    fun init_shouldFallbackWhenPersistedPreferredAudioQualityIsVivid() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val preferences = appContext.getSharedPreferences(
            "player_playback_preferences",
            Context.MODE_PRIVATE
        )
        preferences.edit()
            .clear()
            .putString("preferred_audio_quality", "vivid")
            .commit()

        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeAudioQualityNativePlayer() }
        )

        assertEquals(PlaybackAudioQuality.EXHIGH, runtime.state.value.preferredAudioQuality)
        assertNull(runtime.state.value.appliedAudioQuality)
    }

    @Test
    fun init_shouldRestorePersistedActiveAudioSourceConfigJsonFromSharedPreferences() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val preferences = appContext.getSharedPreferences(
            "player_playback_preferences",
            Context.MODE_PRIVATE
        )
        preferences.edit()
            .clear()
            .putString(
                "active_audio_source_config_json",
                neteaseCompatibleConfigJson("https://mirror.example.com/api/")
            )
            .commit()

        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeAudioQualityNativePlayer() }
        )

        assertEquals(
            neteaseCompatibleConfigJson("https://mirror.example.com/api"),
            runtime.state.value.activeAudioSourceConfigJson
        )
    }

    @Test
    fun init_shouldMigrateLegacyPreferredAudioSourceBaseUrlToActiveSourceConfigJson() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val preferences = appContext.getSharedPreferences(
            "player_playback_preferences",
            Context.MODE_PRIVATE
        )
        preferences.edit()
            .clear()
            .putString("preferred_audio_source_base_url", "https://legacy.example.com/api/")
            .commit()

        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeAudioQualityNativePlayer() }
        )

        assertEquals(
            neteaseCompatibleConfigJson("https://legacy.example.com/api"),
            runtime.state.value.activeAudioSourceConfigJson
        )
    }

    @Test
    fun setPreferredAudioQuality_shouldPersistSelection() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val preferences = appContext.getSharedPreferences(
            "player_playback_preferences",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeAudioQualityNativePlayer() }
        )

        val success = runtime.setPreferredAudioQuality(PlaybackAudioQuality.JYMASTER)

        assertTrue(success)
        assertEquals(PlaybackAudioQuality.JYMASTER, runtime.state.value.preferredAudioQuality)
        assertNull(runtime.state.value.appliedAudioQuality)
        assertEquals("jymaster", preferences.getString("preferred_audio_quality", null))
    }

    @Test
    fun setPreferredAudioQuality_whenPlayingOnlineTrack_shouldReprepareCurrentTrackFromCurrentPosition() = runBlocking {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val nativePlayer = FakeAudioQualityNativePlayer()
        val firstSource = FakeAudioQualityPlaySource(sourceId = "source-exhigh")
        val secondSource = FakeAudioQualityPlaySource(sourceId = "source-lossless")
        val trackPreparer = FakeTrackPreparer(
            results = ArrayDeque(
                listOf(
                    readyResult(
                        source = firstSource,
                        appliedAudioQuality = PlaybackAudioQuality.EXHIGH
                    ),
                    readyResult(
                        source = secondSource,
                        appliedAudioQuality = PlaybackAudioQuality.LOSSLESS
                    )
                )
            )
        )
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = testScope,
            nativePlayerFactory = { nativePlayer },
            trackPreparer = trackPreparer
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-online-quality",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 219_893L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()
        mutableState(runtime).value = runtime.state.value.copy(
            playWhenReady = true,
            playbackState = PLAYBACK_STATE_PLAYING,
            positionMs = 91_000L,
            isSeekSupported = true,
            appliedAudioQuality = PlaybackAudioQuality.EXHIGH
        )

        val success = runtime.setPreferredAudioQuality(PlaybackAudioQuality.LOSSLESS)

        assertTrue(success)
        assertEquals(
            listOf(PlaybackAudioQuality.EXHIGH, PlaybackAudioQuality.LOSSLESS),
            trackPreparer.requestedQualities
        )
        assertEquals(1, firstSource.abortCalls)
        assertEquals(1, firstSource.closeCalls)
        assertEquals(listOf(0L), secondSource.seekOffsets)
        assertEquals(PlaybackAudioQuality.LOSSLESS, runtime.state.value.preferredAudioQuality)
        assertEquals(PlaybackAudioQuality.LOSSLESS, runtime.state.value.appliedAudioQuality)
        assertEquals("切换音质中：无损", runtime.state.value.statusText)
        assertTrue(nativePlayer.stopCalls >= 1)
        assertTrue(nativePlayer.resumeCalls >= 1)

        testScope.cancel()
    }

    @Test
    fun setPreferredAudioQuality_whenPausedOnlineTrack_shouldKeepPausedPositionWithoutByteSeekingSource() = runBlocking {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val nativePlayer = FakeAudioQualityNativePlayer()
        val firstSource = FakeAudioQualityPlaySource(sourceId = "source-exhigh")
        val secondSource = FakeAudioQualityPlaySource(sourceId = "source-lossless")
        val trackPreparer = FakeTrackPreparer(
            results = ArrayDeque(
                listOf(
                    readyResult(
                        source = firstSource,
                        appliedAudioQuality = PlaybackAudioQuality.EXHIGH
                    ),
                    readyResult(
                        source = secondSource,
                        appliedAudioQuality = PlaybackAudioQuality.LOSSLESS
                    )
                )
            )
        )
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = testScope,
            nativePlayerFactory = { nativePlayer },
            trackPreparer = trackPreparer
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-online-quality-paused",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 219_893L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()
        mutableState(runtime).value = runtime.state.value.copy(
            playWhenReady = false,
            playbackState = PLAYBACK_STATE_PAUSED,
            positionMs = 42_000L,
            isSeekSupported = true,
            appliedAudioQuality = PlaybackAudioQuality.EXHIGH
        )

        val success = runtime.setPreferredAudioQuality(PlaybackAudioQuality.LOSSLESS)

        assertTrue(success)
        assertEquals(emptyList<Long>(), secondSource.seekOffsets)
        assertEquals(42_000L, runtime.state.value.positionMs)
        assertEquals(PLAYBACK_STATE_PAUSED, runtime.state.value.playbackState)
        assertEquals("已切换为：无损", runtime.state.value.statusText)

        testScope.cancel()
    }

    @Test
    fun setPreferredAudioQuality_whenFallbackApplied_shouldReportActualQualityInStatusText() = runBlocking {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val nativePlayer = FakeAudioQualityNativePlayer()
        val firstSource = FakeAudioQualityPlaySource(sourceId = "source-exhigh")
        val secondSource = FakeAudioQualityPlaySource(sourceId = "source-lossless")
        val trackPreparer = FakeTrackPreparer(
            results = ArrayDeque(
                listOf(
                    readyResult(
                        source = firstSource,
                        appliedAudioQuality = PlaybackAudioQuality.EXHIGH
                    ),
                    readyResult(
                        source = secondSource,
                        appliedAudioQuality = PlaybackAudioQuality.LOSSLESS
                    )
                )
            )
        )
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = testScope,
            nativePlayerFactory = { nativePlayer },
            trackPreparer = trackPreparer
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-online-quality-fallback",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 219_893L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()
        mutableState(runtime).value = runtime.state.value.copy(
            playWhenReady = false,
            playbackState = PLAYBACK_STATE_PAUSED,
            positionMs = 42_000L,
            isSeekSupported = true,
            appliedAudioQuality = PlaybackAudioQuality.EXHIGH
        )

        val success = runtime.setPreferredAudioQuality(PlaybackAudioQuality.HIRES)

        assertTrue(success)
        assertEquals(PlaybackAudioQuality.HIRES, runtime.state.value.preferredAudioQuality)
        assertEquals(PlaybackAudioQuality.LOSSLESS, runtime.state.value.appliedAudioQuality)
        assertEquals("当前实际使用：无损（已选择 Hi-Res）", runtime.state.value.statusText)

        testScope.cancel()
    }

    @Test
    fun setActiveAudioSourceConfigJson_whenPlayingOnlineTrack_shouldReprepareCurrentTrackFromCurrentPosition() = runBlocking {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val nativePlayer = FakeAudioQualityNativePlayer()
        val firstSource = FakeAudioQualityPlaySource(sourceId = "source-default")
        val secondSource = FakeAudioQualityPlaySource(sourceId = "source-mirror")
        val trackPreparer = FakeTrackPreparer(
            results = ArrayDeque(
                listOf(
                    readyResult(
                        source = firstSource,
                        appliedAudioQuality = PlaybackAudioQuality.EXHIGH
                    ),
                    readyResult(
                        source = secondSource,
                        appliedAudioQuality = PlaybackAudioQuality.EXHIGH
                    )
                )
            )
        )
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = testScope,
            nativePlayerFactory = { nativePlayer },
            trackPreparer = trackPreparer
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-online-source",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 219_893L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()
        mutableState(runtime).value = runtime.state.value.copy(
            playWhenReady = true,
            playbackState = PLAYBACK_STATE_PLAYING,
            positionMs = 64_000L,
            isSeekSupported = true,
            appliedAudioQuality = PlaybackAudioQuality.EXHIGH
        )

        val success = runtime.setActiveAudioSourceConfigJson(
            neteaseCompatibleConfigJson("https://mirror.example.com/api/")
        )

        assertTrue(success)
        assertEquals(2, trackPreparer.requestedQualities.size)
        assertEquals(1, firstSource.abortCalls)
        assertEquals(1, firstSource.closeCalls)
        assertEquals(listOf(0L), secondSource.seekOffsets)
        assertEquals(
            neteaseCompatibleConfigJson("https://mirror.example.com/api"),
            runtime.state.value.activeAudioSourceConfigJson
        )
        assertTrue(nativePlayer.stopCalls >= 1)
        assertTrue(nativePlayer.resumeCalls >= 1)

        testScope.cancel()
    }

    @Test
    fun setActiveAudioSourceConfigJson_whenCurrentTrackIsLocal_shouldNotReprepare() = runBlocking {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val nativePlayer = FakeAudioQualityNativePlayer()
        val localSource = FakeAudioQualityPlaySource(sourceId = "source-local")
        val trackPreparer = FakeTrackPreparer(
            results = ArrayDeque(
                listOf(
                    readyResult(
                        source = localSource,
                        appliedAudioQuality = PlaybackAudioQuality.EXHIGH
                    )
                )
            )
        )
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = testScope,
            nativePlayerFactory = { nativePlayer },
            trackPreparer = trackPreparer
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-local-source",
                    title = "本地歌曲",
                    durationMs = 180_000L,
                    playbackUri = "content://player-lite/local/1"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()
        mutableState(runtime).value = runtime.state.value.copy(
            playWhenReady = false,
            playbackState = PLAYBACK_STATE_PAUSED,
            positionMs = 12_000L,
            isSeekSupported = true
        )

        val success = runtime.setActiveAudioSourceConfigJson(
            neteaseCompatibleConfigJson("https://mirror.example.com/api")
        )

        assertTrue(success)
        assertEquals(1, trackPreparer.requestedQualities.size)
        assertEquals(0, localSource.abortCalls)
        assertEquals(0, localSource.closeCalls)
        assertEquals(
            neteaseCompatibleConfigJson("https://mirror.example.com/api"),
            runtime.state.value.activeAudioSourceConfigJson
        )

        testScope.cancel()
    }

    @Test
    fun setActiveAudioSourceConfigJson_whenConfigInvalid_shouldKeepPreviousSourceAndSkipReprepare() = runBlocking {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val nativePlayer = FakeAudioQualityNativePlayer()
        val firstSource = FakeAudioQualityPlaySource(sourceId = "source-default")
        val trackPreparer = FakeTrackPreparer(
            results = ArrayDeque(
                listOf(
                    readyResult(
                        source = firstSource,
                        appliedAudioQuality = PlaybackAudioQuality.EXHIGH
                    )
                )
            )
        )
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = testScope,
            nativePlayerFactory = { nativePlayer },
            trackPreparer = trackPreparer
        )
        val previousConfig = neteaseCompatibleConfigJson("https://default.example.com/api")
        mutableState(runtime).value = runtime.state.value.copy(
            activeAudioSourceConfigJson = previousConfig,
            statusText = "Playing"
        )

        val success = runtime.setActiveAudioSourceConfigJson("{\"type\":\"unsupported\"}")

        assertEquals(false, success)
        assertEquals(0, firstSource.abortCalls)
        assertEquals(0, firstSource.closeCalls)
        assertEquals(previousConfig, runtime.state.value.activeAudioSourceConfigJson)
        assertEquals("Playing", runtime.state.value.statusText)

        testScope.cancel()
    }

    @Test
    fun setActiveAudioSourceConfigJson_whenAdapterInitFails_shouldKeepPreviousSource() = runBlocking {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val preferences = appContext.getSharedPreferences(
            "player_playback_preferences",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val currentConfig = neteaseCompatibleConfigJson("https://default.example.com/api")
        val brokenConfig = neteaseCompatibleConfigJson("https://broken.example.com/api")
        val currentAdapter = FakeRuntimeSourceAdapter(
            adapterId = "source-default",
            normalizedConfigJson = currentConfig
        )
        val brokenAdapter = FakeRuntimeSourceAdapter(
            adapterId = "source-broken",
            normalizedConfigJson = brokenConfig,
            initResult = Result.failure(IllegalStateException("init failed"))
        )
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = testScope,
            nativePlayerFactory = { FakeAudioQualityNativePlayer() },
            sourceAdapterFactory = FakeSourceAdapterFactory(
                defaultAdapter = FakeRuntimeSourceAdapter(
                    adapterId = "builtin-default",
                    normalizedConfigJson = null
                ),
                adaptersByConfig = mapOf(
                    currentConfig to currentAdapter,
                    brokenConfig to brokenAdapter
                )
            )
        )
        assertTrue(runtime.setActiveAudioSourceConfigJson(currentConfig))
        mutableState(runtime).value = runtime.state.value.copy(statusText = "Playing")

        val success = runtime.setActiveAudioSourceConfigJson(brokenConfig)

        assertEquals(false, success)
        assertEquals(currentConfig, runtime.state.value.activeAudioSourceConfigJson)
        assertEquals("Playing", runtime.state.value.statusText)
        assertEquals(0, currentAdapter.clearCalls)
        assertEquals(0, brokenAdapter.clearCalls)

        testScope.cancel()
    }

    @Test
    fun setActiveAudioSourceConfigJson_whenSwitchSucceeds_shouldClearPreviousAdapterCaches() = runBlocking {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val preferences = appContext.getSharedPreferences(
            "player_playback_preferences",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val nativePlayer = FakeAudioQualityNativePlayer()
        val firstSource = FakeAudioQualityPlaySource(sourceId = "source-default")
        val secondSource = FakeAudioQualityPlaySource(sourceId = "source-mirror")
        val firstConfig = neteaseCompatibleConfigJson("https://default.example.com/api")
        val secondConfig = neteaseCompatibleConfigJson("https://mirror.example.com/api")
        val firstAdapter = FakeRuntimeSourceAdapter(
            adapterId = "source-default",
            normalizedConfigJson = firstConfig
        )
        val secondAdapter = FakeRuntimeSourceAdapter(
            adapterId = "source-mirror",
            normalizedConfigJson = secondConfig
        )
        val trackPreparer = FakeTrackPreparer(
            results = ArrayDeque(
                listOf(
                    readyResult(
                        source = firstSource,
                        appliedAudioQuality = PlaybackAudioQuality.EXHIGH
                    ),
                    readyResult(
                        source = secondSource,
                        appliedAudioQuality = PlaybackAudioQuality.EXHIGH
                    )
                )
            )
        )
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = testScope,
            nativePlayerFactory = { nativePlayer },
            trackPreparer = trackPreparer,
            sourceAdapterFactory = FakeSourceAdapterFactory(
                defaultAdapter = FakeRuntimeSourceAdapter(
                    adapterId = "builtin-default",
                    normalizedConfigJson = null
                ),
                adaptersByConfig = mapOf(
                    firstConfig to firstAdapter,
                    secondConfig to secondAdapter
                )
            )
        )
        assertTrue(runtime.setActiveAudioSourceConfigJson(firstConfig))
        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-online-source-switch",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 219_893L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()
        mutableState(runtime).value = runtime.state.value.copy(
            playWhenReady = true,
            playbackState = PLAYBACK_STATE_PLAYING,
            positionMs = 24_000L,
            isSeekSupported = true,
            appliedAudioQuality = PlaybackAudioQuality.EXHIGH
        )

        val success = runtime.setActiveAudioSourceConfigJson(secondConfig)

        assertTrue(success)
        assertEquals(1, firstAdapter.clearCalls)
        assertEquals(0, secondAdapter.clearCalls)
        assertEquals(secondConfig, runtime.state.value.activeAudioSourceConfigJson)
        assertEquals(
            listOf(PlaybackAudioQuality.EXHIGH, PlaybackAudioQuality.EXHIGH),
            trackPreparer.requestedQualities
        )
        assertTrue(nativePlayer.stopCalls >= 1)

        testScope.cancel()
    }

    private fun neteaseCompatibleConfigJson(baseUrl: String): String {
        return org.json.JSONObject()
            .put("type", "netease-compatible")
            .put("baseUrl", baseUrl.trim().trimEnd('/'))
            .toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun mutableState(runtime: PlaybackProcessRuntime): MutableStateFlow<PlaybackProcessState> {
        val field = runtime.javaClass.getDeclaredField("_state")
        field.isAccessible = true
        return field.get(runtime) as MutableStateFlow<PlaybackProcessState>
    }

    private fun readyResult(
        source: IPlaysource,
        appliedAudioQuality: PlaybackAudioQuality
    ): PreparationResult.Ready {
        return PreparationResult.Ready(
            source = source,
            mediaMeta = AudioMetaDisplay(
                codec = "flac",
                sampleRate = "96000 Hz",
                channels = "2",
                bitRate = "999 kbps",
                durationMs = 219_893L
            ),
            isSeekSupported = true,
            appliedAudioQuality = appliedAudioQuality
        )
    }

    private class FakeAudioQualityNativePlayer : INativePlayer {
        var stopCalls: Int = 0
        var resumeCalls: Int = 0

        override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int = 0

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

        override fun stop() {
            stopCalls += 1
        }

        override fun close() = Unit

        override fun lastError(): String = "audio-quality-error"
    }

    private class FakeTrackPreparer(
        private val results: ArrayDeque<PreparationResult>
    ) : TrackPreparer {
        val requestedQualities = mutableListOf<PlaybackAudioQuality>()

        override suspend fun prepare(
            item: PlaybackTrack,
            preferredAudioQuality: PlaybackAudioQuality
        ): PreparationResult {
            requestedQualities += preferredAudioQuality
            return results.removeFirst()
        }
    }

    private class FakeAudioQualityPlaySource(
        override val sourceId: String
    ) : IPlaysource {
        var abortCalls: Int = 0
        var closeCalls: Int = 0
        val seekOffsets = mutableListOf<Long>()

        override fun setSourceMode(mode: IPlaysource.SourceMode) = Unit

        override fun open(): IPlaysource.AudioSourceCode = IPlaysource.AudioSourceCode.ASC_SUCCESS

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

    private class FakeSourceAdapterFactory(
        private val defaultAdapter: FakeRuntimeSourceAdapter,
        private val adaptersByConfig: Map<String, FakeRuntimeSourceAdapter>
    ) : SourceAdapterFactory {
        override fun create(configJson: String?): Result<SourceAdapter> {
            return when (configJson) {
                null -> Result.success(defaultAdapter)
                else -> adaptersByConfig[configJson]?.let { Result.success(it) }
                    ?: Result.failure(IllegalArgumentException("Unknown config"))
            }
        }
    }

    private class FakeRuntimeSourceAdapter(
        adapterId: String,
        override val normalizedConfigJson: String?,
        private val initResult: Result<SourceState> = Result.success(SourceState())
    ) : SourceAdapter {
        override val metadata: SourceMetadata = SourceMetadata(
            id = adapterId,
            name = adapterId,
            type = "netease-compatible"
        )
        var clearCalls: Int = 0

        override fun init(): Result<SourceState> = initResult

        override suspend fun handle(
            action: SourceAction,
            context: SourceActionContext
        ): Result<SourceActionResult> {
            return Result.failure(UnsupportedOperationException("Not used in runtime tests"))
        }

        override fun clearCaches() {
            clearCalls += 1
        }
    }
}
