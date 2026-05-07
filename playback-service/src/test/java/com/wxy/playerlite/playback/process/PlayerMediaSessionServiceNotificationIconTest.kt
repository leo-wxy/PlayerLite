package com.wxy.playerlite.playback.process

import android.content.Context
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.model.PlaybackPrewarmSnapshot
import com.wxy.playerlite.playback.model.PlaybackPrewarmState
import com.wxy.playerlite.playback.model.PlaybackPrewarmTargetType
import com.wxy.playerlite.playback.model.PlaybackSessionCommands
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.AudioMeta
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.INativePlayer
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.player.source.IPlaysource
import com.wxy.playerlite.playback.service.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerMediaSessionServiceNotificationIconTest {
    @Test
    fun resolveNotificationSmallIcon_shouldAlwaysUseDedicatedNotificationIcon() {
        assertEquals(
            R.drawable.ic_playerlite_notification_small,
            resolveNotificationSmallIcon()
        )
    }

    @Test
    fun resolveNotificationSmallIcon_shouldNotFallBackToSystemMediaPlayIcon() {
        assertEquals(
            R.drawable.ic_playerlite_notification_small,
            resolveNotificationSmallIcon()
        )
    }

    @Test
    fun resolveNotificationTitle_shouldPreferDisplayOverride() {
        val state = PlaybackProcessState(
            tracks = listOf(
                PlaybackTrack(
                    playable = PlayableItemSnapshot(
                        id = "track-1",
                        title = "夜曲",
                        artistText = "周杰伦",
                        playbackUri = "https://example.com/night.mp3"
                    )
                )
            ),
            activeIndex = 0,
            displayTitleOverride = "第二句歌词"
        )

        assertEquals(
            "第二句歌词",
            resolveNotificationTitle(state, fallbackPackageName = "com.wxy.playerlite")
        )
    }

    @Test
    fun resolveNotificationSubtitle_shouldPreferDisplayOverride() {
        val state = PlaybackProcessState(
            tracks = listOf(
                PlaybackTrack(
                    playable = PlayableItemSnapshot(
                        id = "track-1",
                        title = "夜曲",
                        artistText = "周杰伦",
                        playbackUri = "https://example.com/night.mp3"
                    )
                )
            ),
            activeIndex = 0,
            statusText = "Playing",
            displaySubtitleOverride = "夜曲 - 周杰伦"
        )

        assertEquals("夜曲 - 周杰伦", resolveNotificationSubtitle(state))
    }

    @Test
    fun resolveNotificationSubtitle_shouldFallBackToTrackSummaryWhenOverrideMissing() {
        val state = PlaybackProcessState(
            tracks = listOf(
                PlaybackTrack(
                    playable = PlayableItemSnapshot(
                        id = "track-1",
                        title = "夜曲",
                        artistText = "周杰伦",
                        playbackUri = "https://example.com/night.mp3"
                    )
                )
            ),
            activeIndex = 0,
            statusText = "Playing"
        )

        assertEquals("夜曲 - 周杰伦", resolveNotificationSubtitle(state))
    }

    @Test
    fun buildSessionExtras_shouldPublishAudioEffectPresetAlongsidePlaybackFields() {
        val extras = buildSessionExtras(
            PlaybackProcessState(
                statusText = "Playing",
                isSeekSupported = true,
                playbackSpeed = 1.5f,
                playbackMode = PlaybackMode.SINGLE_LOOP,
                audioEffectPreset = AudioEffectPreset.WARM,
                preferredAudioQuality = PlaybackAudioQuality.HIRES,
                appliedAudioQuality = PlaybackAudioQuality.LOSSLESS,
                cacheProgress = PlaybackCacheProgressSnapshot(
                    cachedBytes = 3_000_000L,
                    totalBytes = 6_000_000L,
                    displayRatio = 0.5f,
                    isFullyCached = false,
                    isEstimated = false
                ),
                prewarmSnapshot = PlaybackPrewarmSnapshot(
                    targetId = "track-2",
                    targetType = PlaybackPrewarmTargetType.NEXT_TRACK,
                    state = PlaybackPrewarmState.READY,
                    cachedBytes = 512L * 1024L,
                    targetBytes = 8L * 1024L * 1024L,
                    cachedDurationMs = 5_000L,
                    targetDurationMs = 60_000L,
                    isReady = true,
                    isCompleted = false,
                    reason = "达到快速起播阈值"
                )
            )
        )

        assertEquals("Playing", PlaybackMetadataExtras.readStatusText(extras))
        assertEquals(true, PlaybackMetadataExtras.readSeekSupported(extras))
        assertEquals(1.5f, PlaybackMetadataExtras.readPlaybackSpeed(extras) ?: 0f, 0f)
        assertEquals(PlaybackMode.SINGLE_LOOP, PlaybackMetadataExtras.readPlaybackMode(extras))
        assertEquals(AudioEffectPreset.WARM, PlaybackMetadataExtras.readAudioEffectPreset(extras))
        assertEquals(PlaybackAudioQuality.HIRES, PlaybackMetadataExtras.readPreferredAudioQuality(extras))
        assertEquals(PlaybackAudioQuality.LOSSLESS, PlaybackMetadataExtras.readAppliedAudioQuality(extras))
        assertEquals(0.5f, PlaybackMetadataExtras.readCacheProgress(extras)?.displayRatio ?: 0f, 0f)
        val prewarmSnapshot = PlaybackMetadataExtras.readPlaybackPrewarmSnapshot(extras)
        assertEquals("track-2", prewarmSnapshot?.targetId)
        assertEquals(PlaybackPrewarmState.READY, prewarmSnapshot?.state)
        assertEquals(true, prewarmSnapshot?.isReady)
    }

    @Test
    fun appendSupportedPlaybackSessionCommands_shouldIncludePreferredAudioQualityAction() {
        val commands = appendSupportedPlaybackSessionCommands(
            SessionCommands.Builder().build()
        )

        assertTrue(
            commands.contains(
                SessionCommand(
                    PlaybackSessionCommands.ACTION_SET_PREFERRED_AUDIO_QUALITY,
                    Bundle.EMPTY
                )
            )
        )
    }

    @Test
    fun appendSupportedPlaybackSessionCommands_shouldIncludeCacheLimitAndActiveSourceConfigActions() {
        val commands = appendSupportedPlaybackSessionCommands(
            SessionCommands.Builder().build()
        )

        assertTrue(
            commands.contains(
                SessionCommand(
                    PlaybackSessionCommands.ACTION_SET_PLAYBACK_CACHE_LIMIT,
                    Bundle.EMPTY
                )
            )
        )
        assertTrue(
            commands.contains(
                SessionCommand(
                    PlaybackSessionCommands.ACTION_SET_ACTIVE_AUDIO_SOURCE_CONFIG,
                    Bundle.EMPTY
                )
            )
        )
    }

    @Test
    fun appendSupportedPlaybackSessionCommands_shouldIncludePlaybackPrewarmAction() {
        val commands = appendSupportedPlaybackSessionCommands(
            SessionCommands.Builder().build()
        )

        assertTrue(
            commands.contains(
                SessionCommand(
                    PlaybackSessionCommands.ACTION_SET_PLAYBACK_PREWARM,
                    Bundle.EMPTY
                )
            )
        )
    }

    @Test
    fun handlePlaybackCustomCommand_shouldForwardActiveSourceConfigToRuntime() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            nativePlayerFactory = {
                object : INativePlayer {
                    override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int = 0
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
                    override fun playbackState(): Int = PLAYBACK_STATE_STOPPED
                    override fun stop() = Unit
                    override fun close() = Unit
                    override fun lastError(): String = "ok"
                }
            }
        )

        val result = handlePlaybackCustomCommand(
            runtime = runtime,
            customAction = PlaybackSessionCommands.ACTION_SET_PREFERRED_AUDIO_QUALITY,
            args = Bundle().apply {
                putString(
                    PlaybackSessionCommands.EXTRA_PREFERRED_AUDIO_QUALITY,
                    PlaybackAudioQuality.HIRES.wireValue
                )
            }
        )

        assertEquals(SessionResult.RESULT_SUCCESS, result?.resultCode)
        assertEquals(PlaybackAudioQuality.HIRES, runtime.state.value.preferredAudioQuality)
    }

    @Test
    fun handlePlaybackCustomCommand_shouldForwardPlaybackCacheLimitToRuntime() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            nativePlayerFactory = {
                object : INativePlayer {
                    override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int = 0
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
                    override fun playbackState(): Int = PLAYBACK_STATE_STOPPED
                    override fun stop() = Unit
                    override fun close() = Unit
                    override fun lastError(): String = "ok"
                }
            }
        )

        val result = handlePlaybackCustomCommand(
            runtime = runtime,
            customAction = PlaybackSessionCommands.ACTION_SET_PLAYBACK_CACHE_LIMIT,
            args = Bundle().apply {
                putLong(
                    PlaybackSessionCommands.EXTRA_PLAYBACK_CACHE_LIMIT_BYTES,
                    1024L * 1024L * 1024L
                )
            }
        )

        assertEquals(SessionResult.RESULT_SUCCESS, result?.resultCode)
        assertEquals(1024L * 1024L * 1024L, runtime.state.value.playbackCacheLimitBytes)
    }

    @Test
    fun handlePlaybackCustomCommand_shouldForwardWeakNetworkRetryToRuntime() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            nativePlayerFactory = {
                object : INativePlayer {
                    override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int = 0
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
                    override fun playbackState(): Int = PLAYBACK_STATE_STOPPED
                    override fun stop() = Unit
                    override fun close() = Unit
                    override fun lastError(): String = "ok"
                }
            }
        )

        val result = handlePlaybackCustomCommand(
            runtime = runtime,
            customAction = PlaybackSessionCommands.ACTION_SET_WEAK_NETWORK_AUTO_RETRY,
            args = Bundle().apply {
                putBoolean(
                    PlaybackSessionCommands.EXTRA_WEAK_NETWORK_AUTO_RETRY_ENABLED,
                    false
                )
            }
        )

        assertEquals(SessionResult.RESULT_SUCCESS, result?.resultCode)
        assertTrue(runtime.state.value.statusText.contains("弱网自动重试已关闭"))
    }

    @Test
    fun handlePlaybackCustomCommand_shouldForwardCachePolicyToRuntime() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            nativePlayerFactory = {
                object : INativePlayer {
                    override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int = 0
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
                    override fun playbackState(): Int = PLAYBACK_STATE_STOPPED
                    override fun stop() = Unit
                    override fun close() = Unit
                    override fun lastError(): String = "ok"
                }
            }
        )

        val result = handlePlaybackCustomCommand(
            runtime = runtime,
            customAction = PlaybackSessionCommands.ACTION_SET_CACHE_POLICY,
            args = Bundle().apply {
                putBoolean(
                    PlaybackSessionCommands.EXTRA_SHOW_CACHE_FAILURE_NOTIFICATIONS,
                    false
                )
            }
        )

        assertEquals(SessionResult.RESULT_SUCCESS, result?.resultCode)
        assertEquals("缓存策略已更新", runtime.state.value.statusText)
    }

    @Test
    fun handlePlaybackCustomCommand_shouldForwardPreferredAudioSourceBaseUrlToRuntime() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            nativePlayerFactory = {
                object : INativePlayer {
                    override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int = 0
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
                    override fun playbackState(): Int = PLAYBACK_STATE_STOPPED
                    override fun stop() = Unit
                    override fun close() = Unit
                    override fun lastError(): String = "ok"
                }
            }
        )

        val result = handlePlaybackCustomCommand(
            runtime = runtime,
            customAction = PlaybackSessionCommands.ACTION_SET_ACTIVE_AUDIO_SOURCE_CONFIG,
            args = Bundle().apply {
                putString(
                    PlaybackSessionCommands.EXTRA_ACTIVE_AUDIO_SOURCE_CONFIG_JSON,
                    neteaseCompatibleConfigJson("https://mirror.example.com/api/")
                )
            }
        )

        assertEquals(SessionResult.RESULT_SUCCESS, result?.resultCode)
        assertEquals(
            neteaseCompatibleConfigJson("https://mirror.example.com/api"),
            runtime.state.value.activeAudioSourceConfigJson
        )
    }

    @Test
    fun handlePlaybackCustomCommand_shouldRejectInvalidActiveSourceConfig() {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            nativePlayerFactory = {
                object : INativePlayer {
                    override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int = 0
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
                    override fun playbackState(): Int = PLAYBACK_STATE_STOPPED
                    override fun stop() = Unit
                    override fun close() = Unit
                    override fun lastError(): String = "ok"
                }
            }
        )

        val result = handlePlaybackCustomCommand(
            runtime = runtime,
            customAction = PlaybackSessionCommands.ACTION_SET_ACTIVE_AUDIO_SOURCE_CONFIG,
            args = Bundle().apply {
                putString(
                    PlaybackSessionCommands.EXTRA_ACTIVE_AUDIO_SOURCE_CONFIG_JSON,
                    "{\"type\":\"unsupported\"}"
                )
            }
        )

        assertEquals(SessionResult.RESULT_ERROR_BAD_VALUE, result?.resultCode)
        assertEquals(null, runtime.state.value.activeAudioSourceConfigJson)
    }
}

private fun neteaseCompatibleConfigJson(baseUrl: String): String {
    return org.json.JSONObject()
        .put("type", "netease-compatible")
        .put("baseUrl", baseUrl.trim().trimEnd('/'))
        .toString()
}
