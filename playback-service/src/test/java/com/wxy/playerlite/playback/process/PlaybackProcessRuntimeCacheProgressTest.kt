package com.wxy.playerlite.playback.process

import android.content.Context
import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot
import com.wxy.playerlite.playback.model.PlaybackPrewarmTargetType
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackProcessRuntimeCacheProgressTest {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun tearDown() {
        CacheCore.shutdown()
        resolveCacheRoot().deleteRecursively()
        serviceScope.cancel()
    }

    @Test
    fun prepareCurrent_whenPreparationProvidesInitialCacheProgress_shouldExposeItImmediately() = runBlocking {
        val initialCacheProgress = PlaybackCacheProgressSnapshot(
            cachedBytes = 1_024_000L,
            totalBytes = 1_024_000L,
            displayRatio = 1f,
            isFullyCached = true,
            isEstimated = false
        )
        val nativePlayer = FakeCacheProgressNativePlayer()
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { nativePlayer },
            trackPreparer = FakeCacheProgressTrackPreparer(initialCacheProgress)
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-cache-progress",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 219_893L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )

        runtime.prepareCurrent()

        val cacheProgress = requireNotNull(runtime.state.value.cacheProgress)
        assertNotNull(cacheProgress)
        assertEquals(1f, cacheProgress.displayRatio, 0f)
        assertEquals(true, cacheProgress.isFullyCached)
    }

    @Test
    fun progressUpdate_whenPlaybackPositionHitsBufferedRange_shouldExposeResolvedCacheProgress() = runBlocking {
        val appContext = RuntimeEnvironment.getApplication() as Context
        val resourceKey = "queue-cache-progress-resume"
        seedCacheSnapshot(
            resourceKey = resourceKey,
            contentLength = 1_000L,
            durationMs = 200_000L,
            completedRangeStart = 200L,
            completedRangeEndExclusive = 500L
        )
        val source = FakeCacheProgressObservableSource()
        val runtime = PlaybackProcessRuntime(
            appContext = appContext,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = null,
                source = source,
                cacheResourceKey = resourceKey,
                cacheContentLengthHintBytes = 1_000L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = resourceKey,
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )

        runtime.prepareCurrent()
        val emitted = requireNotNull(
            resolvePlaybackCacheProgressSnapshot(
                snapshot = CacheCore.lookup(resourceKey).getOrNull(),
                totalBytesHint = 1_000L,
                playbackPositionMs = 50_000L,
                durationMs = 200_000L
            )
        )
        source.dispatchCacheProgress(emitted)

        val cacheProgress = requireNotNull(runtime.state.value.cacheProgress)
        assertNotNull(cacheProgress)
        assertEquals(300L, cacheProgress.cachedBytes)
        assertEquals(1_000L, cacheProgress.totalBytes)
        assertEquals(0.5f, cacheProgress.displayRatio, 0.0001f)
        assertFalse(cacheProgress.isFullyCached)
    }

    @Test
    fun cacheProgressCallbackFromPreparedSource_shouldUpdateRuntimeStateImmediately() = runBlocking {
        val cacheProgress = PlaybackCacheProgressSnapshot(
            cachedBytes = 512L,
            totalBytes = 1_024L,
            displayRatio = 0.5f,
            isFullyCached = false,
            isEstimated = false
        )
        val source = FakeCacheProgressObservableSource()
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = null,
                source = source,
                cacheResourceKey = "queue-cache-progress-callback",
                cacheContentLengthHintBytes = 1_024L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-cache-progress-callback",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()

        source.dispatchCacheProgress(cacheProgress)

        assertEquals(cacheProgress, runtime.state.value.cacheProgress)
    }

    @Test
    fun cacheProgressCallback_whenDiskSnapshotIsFullyCached_shouldPreferDiskSnapshot() = runBlocking {
        val resourceKey = "queue-cache-progress-full-disk"
        seedCacheSnapshot(
            resourceKey = resourceKey,
            contentLength = 1_000L,
            durationMs = 200_000L,
            completedRangeStart = 0L,
            completedRangeEndExclusive = 1_000L
        )
        val source = FakeCacheProgressObservableSource()
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = null,
                source = source,
                cacheResourceKey = resourceKey,
                cacheContentLengthHintBytes = 1_000L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = resourceKey,
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()

        source.dispatchCacheProgress(
            PlaybackCacheProgressSnapshot(
                cachedBytes = 300L,
                totalBytes = 1_000L,
                displayRatio = 0.3f,
                isFullyCached = false,
                isEstimated = true
            )
        )

        val cacheProgress = requireNotNull(runtime.state.value.cacheProgress)
        assertEquals(1_000L, cacheProgress.cachedBytes)
        assertEquals(1_000L, cacheProgress.totalBytes)
        assertEquals(1f, cacheProgress.displayRatio, 0.0001f)
        assertEquals(true, cacheProgress.isFullyCached)
    }

    @Test
    fun cacheProgressCallback_whenAlreadyFullyCached_shouldNotDowngradeToPartialEmission() = runBlocking {
        val resourceKey = "queue-cache-progress-full-stable"
        seedCacheSnapshot(
            resourceKey = resourceKey,
            contentLength = 1_000L,
            durationMs = 200_000L,
            completedRangeStart = 0L,
            completedRangeEndExclusive = 1_000L
        )
        val source = FakeCacheProgressObservableSource()
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = null,
                source = source,
                cacheResourceKey = resourceKey,
                cacheContentLengthHintBytes = 1_000L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = resourceKey,
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()

        source.dispatchCacheProgress(
            PlaybackCacheProgressSnapshot(
                cachedBytes = 300L,
                totalBytes = 1_000L,
                displayRatio = 0.3f,
                isFullyCached = false,
                isEstimated = true
            )
        )
        resolveCacheRoot().deleteRecursively()
        CacheCore.shutdown()
        source.dispatchCacheProgress(
            PlaybackCacheProgressSnapshot(
                cachedBytes = 350L,
                totalBytes = 1_000L,
                displayRatio = 0.35f,
                isFullyCached = false,
                isEstimated = true
            )
        )

        val cacheProgress = requireNotNull(runtime.state.value.cacheProgress)
        assertEquals(1_000L, cacheProgress.cachedBytes)
        assertEquals(1_000L, cacheProgress.totalBytes)
        assertEquals(1f, cacheProgress.displayRatio, 0.0001f)
        assertEquals(true, cacheProgress.isFullyCached)
    }

    @Test
    fun cacheProgressCallbackNull_shouldNotFallbackToRuntimeLookupResolution() = runBlocking {
        val resourceKey = "queue-cache-progress-no-runtime-fallback"
        seedCacheSnapshot(
            resourceKey = resourceKey,
            contentLength = 1_000L,
            durationMs = 200_000L,
            completedRangeStart = 200L,
            completedRangeEndExclusive = 500L
        )
        val source = FakeCacheProgressObservableSource()
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = null,
                source = source,
                cacheResourceKey = resourceKey,
                cacheContentLengthHintBytes = 1_000L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = resourceKey,
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()
        assertEquals(null, runtime.state.value.cacheProgress)

        source.dispatchCacheProgress(null)

        assertEquals(null, runtime.state.value.cacheProgress)
    }

    @Test
    fun seekTo_whenPaused_shouldResolveCacheProgressForNewPosition() = runBlocking {
        val resourceKey = "queue-cache-progress-paused-seek"
        seedCacheSnapshot(
            resourceKey = resourceKey,
            contentLength = 1_000L,
            durationMs = 200_000L,
            completedRangeStart = 200L,
            completedRangeEndExclusive = 500L
        )
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = null,
                cacheResourceKey = resourceKey,
                cacheContentLengthHintBytes = 1_000L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = resourceKey,
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()

        runtime.seekTo(50_000L)

        val cacheProgress = requireNotNull(runtime.state.value.cacheProgress)
        assertEquals(300L, cacheProgress.cachedBytes)
        assertEquals(1_000L, cacheProgress.totalBytes)
        assertEquals(0.5f, cacheProgress.displayRatio, 0.0001f)
        assertFalse(cacheProgress.isFullyCached)
    }

    @Test
    fun seekTo_whenPaused_shouldNotifyPreparedSourceToReanchorCacheProgress() = runBlocking {
        val source = FakeCacheProgressObservableSource()
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = null,
                source = source,
                cacheResourceKey = "queue-cache-progress-user-seek-reanchor",
                cacheContentLengthHintBytes = 1_000L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-cache-progress-user-seek-reanchor",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()

        runtime.seekTo(50_000L)

        assertEquals(50_000L to 200_000L, source.lastSeekAnchorRequest)
        val cacheProgress = requireNotNull(runtime.state.value.cacheProgress)
        assertEquals(0.5f, cacheProgress.displayRatio, 0.0001f)
    }

    @Test
    fun seekTo_whenAlreadyFullyCached_shouldNotDowngradeCacheProgress() = runBlocking {
        val source = FakeCacheProgressObservableSource()
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = PlaybackCacheProgressSnapshot(
                    cachedBytes = 1_000L,
                    totalBytes = 1_000L,
                    displayRatio = 1f,
                    isFullyCached = true,
                    isEstimated = false
                ),
                source = source,
                cacheResourceKey = "queue-cache-progress-full-seek",
                cacheContentLengthHintBytes = 1_000L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-cache-progress-full-seek",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()
        assertEquals(true, runtime.state.value.cacheProgress?.isFullyCached)

        resolveCacheRoot().deleteRecursively()
        CacheCore.shutdown()
        runtime.seekTo(50_000L)

        val cacheProgress = requireNotNull(runtime.state.value.cacheProgress)
        assertEquals(1_000L, cacheProgress.cachedBytes)
        assertEquals(1_000L, cacheProgress.totalBytes)
        assertEquals(1f, cacheProgress.displayRatio, 0.0001f)
        assertEquals(true, cacheProgress.isFullyCached)
    }

    @Test
    fun cacheProgressCallback_whenSameResourceReportsLowerProgress_shouldNotDowngrade() = runBlocking {
        val source = FakeCacheProgressObservableSource()
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = PlaybackCacheProgressSnapshot(
                    cachedBytes = 700L,
                    totalBytes = 1_000L,
                    displayRatio = 0.7f,
                    isFullyCached = false,
                    isEstimated = false
                ),
                source = source,
                cacheResourceKey = "queue-cache-progress-monotonic",
                cacheContentLengthHintBytes = 1_000L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-cache-progress-monotonic",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()

        source.dispatchCacheProgress(
            PlaybackCacheProgressSnapshot(
                cachedBytes = 390L,
                totalBytes = 1_000L,
                displayRatio = 0.39f,
                isFullyCached = false,
                isEstimated = false
            )
        )

        val cacheProgress = requireNotNull(runtime.state.value.cacheProgress)
        assertEquals(700L, cacheProgress.cachedBytes)
        assertEquals(0.7f, cacheProgress.displayRatio, 0.0001f)
    }

    @Test
    fun cacheProgressCallback_whenSeekReanchorsToLowerRange_shouldAcceptLowerProgress() = runBlocking {
        val source = FakeCacheProgressObservableSource(emitSeekCacheProgress = false)
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = PlaybackCacheProgressSnapshot(
                    cachedBytes = 700L,
                    totalBytes = 1_000L,
                    displayRatio = 0.7f,
                    isFullyCached = false,
                    isEstimated = false
                ),
                source = source,
                cacheResourceKey = "queue-cache-progress-reanchor",
                cacheContentLengthHintBytes = 1_000L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-cache-progress-reanchor",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()

        runtime.seekTo(120_000L)

        source.dispatchCacheProgress(
            PlaybackCacheProgressSnapshot(
                cachedBytes = 200L,
                totalBytes = 1_000L,
                displayStartRatio = 0.6f,
                displayRatio = 0.62f,
                isFullyCached = false,
                isEstimated = false
            )
        )

        val cacheProgress = requireNotNull(runtime.state.value.cacheProgress)
        assertEquals(200L, cacheProgress.cachedBytes)
        assertEquals(0.6f, cacheProgress.displayStartRatio, 0.0001f)
        assertEquals(0.62f, cacheProgress.displayRatio, 0.0001f)
    }

    @Test
    fun seekTo_whenReanchoredSnapshotMissing_shouldNotLeavePreviousCacheProgressStuck() = runBlocking {
        val source = FakeCacheProgressObservableSource(emitSeekCacheProgress = false)
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = PlaybackCacheProgressSnapshot(
                    cachedBytes = 700L,
                    totalBytes = 1_000L,
                    displayRatio = 0.7f,
                    isFullyCached = false,
                    isEstimated = false
                ),
                source = source,
                cacheResourceKey = "queue-cache-progress-seek-null",
                cacheContentLengthHintBytes = 1_000L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-cache-progress-seek-null",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()

        runtime.seekTo(120_000L)

        assertEquals(null, runtime.state.value.cacheProgress)
    }

    @Test
    fun cacheProgressCallback_whenCurrentTrackBecomesFullyCached_shouldScheduleNextPrewarm() = runBlocking {
        val source = FakeCacheProgressObservableSource()
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = PlaybackCacheProgressSnapshot(
                    cachedBytes = 700L,
                    totalBytes = 1_000L,
                    displayRatio = 0.7f,
                    isFullyCached = false,
                    isEstimated = false
                ),
                source = source,
                cacheResourceKey = "queue-cache-progress-prewarm-current",
                cacheContentLengthHintBytes = 1_000L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-cache-progress-prewarm-current",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem(),
                MusicInfo(
                    id = "queue-cache-progress-prewarm-next",
                    songId = "1969519580",
                    title = "晴天",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/sunny.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()

        source.dispatchCacheProgress(
            PlaybackCacheProgressSnapshot(
                cachedBytes = 1_000L,
                totalBytes = 1_000L,
                displayRatio = 1f,
                isFullyCached = true,
                isEstimated = false
            )
        )

        val prewarmSnapshot = requireNotNull(runtime.state.value.prewarmSnapshot)
        assertEquals("queue-cache-progress-prewarm-next", prewarmSnapshot.targetId)
        assertEquals(PlaybackPrewarmTargetType.NEXT_TRACK, prewarmSnapshot.targetType)
    }

    @Test
    fun clearCache_shouldDetachCacheProgressListenerFromPreparedSource() = runBlocking {
        val source = FakeCacheProgressObservableSource()
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = null,
                source = source,
                cacheResourceKey = "queue-cache-progress-clear",
                cacheContentLengthHintBytes = 1_024L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-cache-progress-clear",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()
        assertEquals(true, source.hasCacheProgressListener)

        runtime.clearCache()

        assertEquals(false, source.hasCacheProgressListener)
    }

    @Test
    fun restorePreparedPositionWithoutPlayback_whenFastSeekUnavailable_shouldNotKeepStaleCacheProgress() = runBlocking {
        val resourceKey = "queue-cache-progress-no-fast-seek"
        seedCacheSnapshot(
            resourceKey = resourceKey,
            contentLength = 1_000L,
            durationMs = 200_000L,
            completedRangeStart = 200L,
            completedRangeEndExclusive = 500L
        )
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = PlaybackCacheProgressSnapshot(
                    cachedBytes = 300L,
                    totalBytes = 1_000L,
                    displayRatio = 0.5f,
                    isFullyCached = false,
                    isEstimated = false
                ),
                source = FakeCacheProgressNonSeekableSource(),
                cacheResourceKey = resourceKey,
                cacheContentLengthHintBytes = 1_000L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = resourceKey,
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()

        runtime.restorePreparedPositionWithoutPlayback(50_000L)

        assertEquals(0L, runtime.state.value.positionMs)
        assertEquals(PLAYBACK_STATE_PAUSED, runtime.state.value.playbackState)
        assertEquals("Prepared (seek unavailable)", runtime.state.value.statusText)
        assertEquals(null, runtime.state.value.cacheProgress)
    }

    @Test
    fun restorePreparedPositionWithoutPlayback_whenReanchoredToLowerRange_shouldAcceptLowerProgress() = runBlocking {
        val source = FakeCacheProgressObservableSource(emitSeekCacheProgress = false)
        val runtime = PlaybackProcessRuntime(
            appContext = RuntimeEnvironment.getApplication() as Context,
            serviceScope = serviceScope,
            nativePlayerFactory = { FakeCacheProgressNativePlayer() },
            trackPreparer = FakeCacheProgressTrackPreparer(
                initialCacheProgress = PlaybackCacheProgressSnapshot(
                    cachedBytes = 700L,
                    totalBytes = 1_000L,
                    displayRatio = 0.7f,
                    isFullyCached = false,
                    isEstimated = false
                ),
                source = source,
                cacheResourceKey = "queue-cache-progress-restore-reanchor",
                cacheContentLengthHintBytes = 1_000L,
                durationMs = 200_000L
            )
        )

        runtime.setQueue(
            mediaItems = listOf(
                MusicInfo(
                    id = "queue-cache-progress-restore-reanchor",
                    songId = "1969519579",
                    title = "夜曲",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/night.mp3"
                ).toMediaItem()
            ),
            startIndex = 0
        )
        runtime.prepareCurrent()

        runtime.restorePreparedPositionWithoutPlayback(120_000L)
        source.dispatchCacheProgress(
            PlaybackCacheProgressSnapshot(
                cachedBytes = 200L,
                totalBytes = 1_000L,
                displayStartRatio = 0.6f,
                displayRatio = 0.62f,
                isFullyCached = false,
                isEstimated = false
            )
        )

        val cacheProgress = requireNotNull(runtime.state.value.cacheProgress)
        assertEquals(200L, cacheProgress.cachedBytes)
        assertEquals(0.6f, cacheProgress.displayStartRatio, 0.0001f)
        assertEquals(0.62f, cacheProgress.displayRatio, 0.0001f)
    }

    private fun seedCacheSnapshot(
        resourceKey: String,
        contentLength: Long,
        durationMs: Long,
        completedRangeStart: Long,
        completedRangeEndExclusive: Long
    ) {
        val cacheRoot = resolveCacheRoot()
        cacheRoot.mkdirs()
        File(cacheRoot, "$resourceKey.data").writeBytes(ByteArray(completedRangeEndExclusive.toInt()))
        File(cacheRoot, "${resourceKey}_extra.json").writeText("{\n}\n")
        File(cacheRoot, "${resourceKey}_config.json").writeText(
            """
            {
              "version": 1,
              "resourceKey": "$resourceKey",
              "contentLength": $contentLength,
              "durationMs": $durationMs,
              "blockSizeBytes": 100,
              "blocks": [],
              "completedRanges": [{"start":$completedRangeStart,"end":$completedRangeEndExclusive}],
              "lastAccessEpochMs": 1
            }
            """.trimIndent()
        )
    }

    private fun resolveCacheRoot(): File {
        val appContext = RuntimeEnvironment.getApplication() as Context
        return File(appContext.cacheDir, "cache_core")
    }
}

private class FakeCacheProgressTrackPreparer(
    private val initialCacheProgress: PlaybackCacheProgressSnapshot? = null,
    private val source: IPlaysource = FakeCacheProgressPlaySource(),
    private val cacheResourceKey: String = "queue-cache-progress",
    private val cacheContentLengthHintBytes: Long = 1_024_000L,
    private val durationMs: Long = 219_893L
) : TrackPreparer {
    override suspend fun prepare(
        item: PlaybackTrack,
        preferredAudioQuality: PlaybackAudioQuality
    ): PreparationResult {
        return PreparationResult.Ready(
            source = source,
            mediaMeta = AudioMetaDisplay(
                codec = "flac",
                sampleRate = "96000 Hz",
                channels = "2",
                bitRate = "999 kbps",
                durationMs = durationMs
            ),
            isSeekSupported = true,
            appliedAudioQuality = PlaybackAudioQuality.EXHIGH,
            cacheResourceKey = cacheResourceKey,
            cacheContentLengthHintBytes = cacheContentLengthHintBytes,
            initialCacheProgress = initialCacheProgress
        )
    }
}

private class FakeCacheProgressObservableSource(
    private val seekCacheProgress: PlaybackCacheProgressSnapshot? = PlaybackCacheProgressSnapshot(
        cachedBytes = 300L,
        totalBytes = 1_000L,
        displayRatio = 0.5f,
        isFullyCached = false,
        isEstimated = false
    ),
    private val emitSeekCacheProgress: Boolean = true
) : FakeCacheProgressPlaySource(), PlaybackCacheProgressEmitter {
    private var listener: ((PlaybackCacheProgressSnapshot?) -> Unit)? = null
    var lastSeekAnchorRequest: Pair<Long, Long>? = null
        private set

    val hasCacheProgressListener: Boolean
        get() = listener != null

    override fun setCacheProgressListener(listener: ((PlaybackCacheProgressSnapshot?) -> Unit)?) {
        this.listener = listener
    }

    override fun onPlaybackSeekPositionChanged(positionMs: Long, durationMs: Long) {
        lastSeekAnchorRequest = positionMs to durationMs
        if (emitSeekCacheProgress) {
            listener?.invoke(seekCacheProgress)
        }
    }

    fun dispatchCacheProgress(progress: PlaybackCacheProgressSnapshot?) {
        listener?.invoke(progress)
    }
}

private open class FakeCacheProgressPlaySource : IPlaysource {
    override val sourceId: String = "cache-progress-source"

    override fun setSourceMode(mode: IPlaysource.SourceMode) = Unit

    override fun open(): IPlaysource.AudioSourceCode = IPlaysource.AudioSourceCode.ASC_SUCCESS

    override fun stop() = Unit

    override fun abort() = Unit

    override fun close() = Unit

    override fun size(): Long = 0L

    override fun cacheSize(): Long = 0L

    override fun supportFastSeek(): Boolean = true

    override fun read(buffer: ByteArray, size: Int): Int = 0

    override fun seek(offset: Long, whence: Int): Long = offset
}

private class FakeCacheProgressNonSeekableSource : FakeCacheProgressPlaySource() {
    override fun supportFastSeek(): Boolean = false
}

private class FakeCacheProgressNativePlayer : INativePlayer {
    private var progressListener: ((Long) -> Unit)? = null

    override fun setAudioEffectPreset(audioEffectPreset: AudioEffectPreset): Int = 0

    override fun setProgressListener(listener: ((Long) -> Unit)?) {
        progressListener = listener
    }

    override fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?) = Unit

    override fun setPlaybackSpeed(speed: Float): Int = 0

    override fun playFromSource(source: IPlaysource): Int = 0

    override fun pause(): Int = 0

    override fun resume(): Int = 0

    override fun seek(positionMs: Long): Int = 0

    override fun getDurationFromSource(source: IPlaysource): Long = 219_893L

    override fun loadAudioMetaFromSource(source: IPlaysource): AudioMeta {
        return AudioMeta(
            codec = "flac",
            sampleRateHz = 96_000,
            channels = 2,
            bitRate = 999_000L,
            durationMs = 219_893L
        )
    }

    override fun loadAudioMetaDisplayFromSource(source: IPlaysource): AudioMetaDisplay {
        return AudioMetaDisplay(
            codec = "flac",
            sampleRate = "96000 Hz",
            channels = "2",
            bitRate = "999 kbps",
            durationMs = 219_893L
        )
    }

    override fun playbackState(): Int = PLAYBACK_STATE_STOPPED

    override fun stop() = Unit

    override fun close() = Unit

    override fun lastError(): String = ""

    fun dispatchProgress(positionMs: Long) {
        progressListener?.invoke(positionMs)
    }
}
