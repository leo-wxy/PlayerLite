package com.wxy.playerlite.playback.process

import android.content.Context
import android.os.Looper
import androidx.media3.common.MediaItem
import com.wxy.playerlite.playback.model.MusicInfo
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
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackProcessRuntimeRetryTest {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        serviceScope.cancel()
    }

    @Test
    fun playCurrent_whenOnlinePlaybackFails_shouldExposeRetryingStateBeforeRetryBudgetExhausted() = runBlocking {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = {
                RetryingNativePlayer(
                    playCodes = ArrayDeque(listOf(-1234, -1234, -1234)),
                    delayPerAttemptMs = 90L,
                    lastError = "transient network"
                )
            },
            trackPreparer = ReadyTrackPreparer()
        )

        runtime.setQueue(mediaItems = listOf(onlineItem().toMediaItem()), startIndex = 0)
        runtime.setPlayWhenReady(true)

        runtime.playCurrent()

        val retrying = waitForState(runtime) { state ->
            state.isPreparing && state.playWhenReady && state.statusText.startsWith("重试中")
        }

        assertTrue("state=$retrying", retrying.statusText.startsWith("重试中"))
        assertTrue("state=$retrying", retrying.isPreparing)
        assertTrue("state=$retrying", retrying.playWhenReady)
    }

    @Test
    fun playCurrent_whenOnlinePlaybackRetryBudgetExhausted_shouldPublishFinalFailureState() = runBlocking {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = {
                RetryingNativePlayer(
                    playCodes = ArrayDeque(listOf(-1234, -1234, -1234)),
                    delayPerAttemptMs = 60L,
                    lastError = "transient network"
                )
            },
            trackPreparer = ReadyTrackPreparer()
        )

        runtime.setQueue(mediaItems = listOf(onlineItem().toMediaItem()), startIndex = 0)
        runtime.setPlayWhenReady(true)

        runtime.playCurrent()

        val failed = waitForState(runtime) { state ->
            !state.isPreparing && !state.playWhenReady && state.statusText.contains("Playback failed(-1234)")
        }

        assertFalse("state=$failed", failed.isPreparing)
        assertFalse("state=$failed", failed.playWhenReady)
        assertTrue("state=$failed", failed.statusText.contains("transient network"))
    }

    @Test
    fun playCurrent_whenTransientConnectionRecovers_shouldContinueCurrentTrack() = runBlocking {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = {
                RetryingNativePlayer(
                    playCodes = ArrayDeque(listOf(-1234, 0)),
                    delayPerAttemptMs = 20L,
                    lastError = "transient network",
                    recoveredProgressMs = 42_050L,
                    successHoldMs = 300L
                )
            },
            trackPreparer = ReadyTrackPreparer()
        )

        runtime.setQueue(mediaItems = listOf(onlineItem().toMediaItem()), startIndex = 0)
        runtime.setPlayWhenReady(true)

        runtime.playCurrent(initialPositionMs = 42_000L)

        val recovered = waitForState(runtime) { state ->
            !state.isPreparing &&
                state.playWhenReady &&
                state.playbackState == PLAYBACK_STATE_PLAYING &&
                state.currentTrack?.id == "queue:test:0:track-1" &&
                state.positionMs >= 42_000L
        }

        assertTrue("state=$recovered", recovered.playWhenReady)
        assertFalse("state=$recovered", recovered.isPreparing)
        assertTrue("state=$recovered", recovered.positionMs >= 42_000L)
        assertTrue("state=$recovered", recovered.statusText.startsWith("Playing"))
        assertTrue("state=$recovered", recovered.currentTrack?.id == "queue:test:0:track-1")
    }

    private suspend fun waitForState(
        runtime: PlaybackProcessRuntime,
        predicate: (PlaybackProcessState) -> Boolean
    ): PlaybackProcessState {
        repeat(40) {
            shadowOf(Looper.getMainLooper()).idle()
            val state = runtime.state.value
            if (predicate(state)) {
                return state
            }
            delay(30L)
        }
        return runtime.state.value
    }

    private fun onlineItem(): MusicInfo {
        return MusicInfo(
            id = "queue:test:0:track-1",
            songId = "track-1",
            title = "第一首",
            playbackUri = "https://example.com/track-1.mp3"
        )
    }
}

private class ReadyTrackPreparer : TrackPreparer {
    private val source = ReadyRetrySource()

    override suspend fun prepare(
        item: PlaybackTrack,
        preferredAudioQuality: com.wxy.playerlite.playback.model.PlaybackAudioQuality
    ): PreparationResult {
        return PreparationResult.Ready(
            source = source,
            mediaMeta = AudioMetaDisplay(
                codec = "aac",
                sampleRate = "44100 Hz",
                channels = "2",
                bitRate = "128 kbps",
                durationMs = 180_000L
            ),
            isSeekSupported = true,
            appliedAudioQuality = null
        )
    }
}

private class ReadyRetrySource : IPlaysource {
    override val sourceId: String = "retry-ready-source"

    override fun setSourceMode(mode: IPlaysource.SourceMode) = Unit

    override fun open(): IPlaysource.AudioSourceCode = IPlaysource.AudioSourceCode.ASC_SUCCESS

    override fun stop() = Unit

    override fun abort() = Unit

    override fun close() = Unit

    override fun size(): Long = 180_000L

    override fun cacheSize(): Long = 180_000L

    override fun supportFastSeek(): Boolean = true

    override fun read(buffer: ByteArray, size: Int): Int = -1

    override fun seek(offset: Long, whence: Int): Long = offset
}

private class RetryingNativePlayer(
    private val playCodes: ArrayDeque<Int>,
    private val delayPerAttemptMs: Long,
    private val lastError: String,
    private val recoveredProgressMs: Long? = null,
    private val successHoldMs: Long = 0L
) : INativePlayer {
    private var progressListener: ((Long) -> Unit)? = null

    override fun setProgressListener(listener: ((Long) -> Unit)?) {
        progressListener = listener
    }

    override fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?) = Unit

    override fun setPlaybackSpeed(speed: Float): Int = 0

    override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int = 0

    override fun playFromSource(source: IPlaysource): Int {
        Thread.sleep(delayPerAttemptMs)
        val code = playCodes.removeFirstOrNull() ?: -1234
        if (code == 0) {
            recoveredProgressMs?.let { progressListener?.invoke(it) }
            if (successHoldMs > 0L) {
                Thread.sleep(successHoldMs)
            }
        }
        return code
    }

    override fun pause(): Int = 0

    override fun resume(): Int = 0

    override fun seek(positionMs: Long): Int = 0

    override fun getDurationFromSource(source: IPlaysource): Long = 180_000L

    override fun loadAudioMetaFromSource(source: IPlaysource): AudioMeta {
        return AudioMeta(
            codec = "aac",
            sampleRateHz = 44_100,
            channels = 2,
            bitRate = 128_000L,
            durationMs = 180_000L
        )
    }

    override fun loadAudioMetaDisplayFromSource(source: IPlaysource): AudioMetaDisplay {
        return AudioMetaDisplay(
            codec = "aac",
            sampleRate = "44100 Hz",
            channels = "2",
            bitRate = "128 kbps",
            durationMs = 180_000L
        )
    }

    override fun playbackState(): Int = PLAYBACK_STATE_STOPPED

    override fun stop() = Unit

    override fun close() = Unit

    override fun lastError(): String = lastError
}
