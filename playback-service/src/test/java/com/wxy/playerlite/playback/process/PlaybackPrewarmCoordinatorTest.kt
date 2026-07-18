package com.wxy.playerlite.playback.process

import com.wxy.playerlite.cache.core.CacheCompletedRange
import com.wxy.playerlite.cache.core.CacheLookupSnapshot
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.CacheSession
import com.wxy.playerlite.cache.core.session.OpenSessionParams
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.model.PlaybackPrewarmPreferences
import com.wxy.playerlite.playback.model.PlaybackPrewarmSnapshot
import com.wxy.playerlite.playback.model.PlaybackPrewarmState
import com.wxy.playerlite.playback.model.PlaybackPrewarmTargetType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPrewarmCoordinatorTest {
    @Test
    fun resolveCandidate_whenCurrentCacheIncomplete_shouldLetCoreOwnCurrentAheadWindow() {
        val context = prewarmContext(currentItemCacheCompleteOrNoWrite = false)

        val candidate = resolvePlaybackPrewarmCandidate(context)

        assertNull(candidate)
    }

    @Test
    fun resolveCandidate_whenCurrentCacheComplete_shouldUseNextTrack() {
        val context = prewarmContext(currentItemCacheCompleteOrNoWrite = true)

        val candidate = resolvePlaybackPrewarmCandidate(context)

        assertEquals("track-2", candidate?.track?.id)
        assertEquals(PlaybackPrewarmTargetType.NEXT_TRACK, candidate?.targetType)
    }

    @Test
    fun resolveCandidate_whenCurrentHasStableAheadBuffer_shouldUseNextTrack() {
        val context = prewarmContext(
            currentItemCacheCompleteOrNoWrite = false,
            currentPositionMs = 44_000L,
            currentDurationMs = 198_000L,
            currentCacheProgress = cacheProgress(displayRatio = 0.39f)
        )

        val candidate = resolvePlaybackPrewarmCandidate(context)

        assertEquals("track-2", candidate?.track?.id)
        assertEquals(PlaybackPrewarmTargetType.NEXT_TRACK, candidate?.targetType)
    }

    @Test
    fun resolveCandidate_whenCurrentAheadBufferIsShort_shouldNotOpenDuplicateSession() {
        val context = prewarmContext(
            currentItemCacheCompleteOrNoWrite = false,
            currentPositionMs = 44_000L,
            currentDurationMs = 198_000L,
            currentCacheProgress = cacheProgress(displayRatio = 0.28f)
        )

        val candidate = resolvePlaybackPrewarmCandidate(context)

        assertNull(candidate)
    }

    @Test
    fun resolveCandidate_whenCurrentTrackHasNotStarted_shouldNotCompeteWithPlaybackPreparation() {
        val context = prewarmContext(
            currentItemCacheCompleteOrNoWrite = false,
            currentPositionMs = 0L,
            currentCacheProgress = null
        )

        assertNull(resolvePlaybackPrewarmCandidate(context))
    }

    @Test
    fun resolveCandidate_whenCurrentCacheIncompleteAndCurrentTrackOffline_shouldReturnNull() {
        val context = prewarmContext(
            currentItemCacheCompleteOrNoWrite = false,
            currentTrackSongId = null,
            currentTrackUri = "file:///music/local.m4a"
        )

        assertNull(resolvePlaybackPrewarmCandidate(context))
    }

    @Test
    fun resolveCandidate_whenSingleLoop_shouldNotPrewarmNextTrack() {
        val context = prewarmContext(
            playbackMode = PlaybackMode.SINGLE_LOOP,
            currentItemCacheCompleteOrNoWrite = true
        )

        assertNull(resolvePlaybackPrewarmCandidate(context))
    }

    @Test
    fun resolveSkipSnapshot_whenPreferencesEnabled_shouldNotSkipForNetworkPolicy() {
        val context = prewarmContext()

        val snapshot = resolvePrewarmSkipSnapshot(context)

        assertNull(snapshot)
    }

    @Test
    fun completeCacheDetection_shouldRequireContiguousRangeFromStartToContentLength() {
        assertTrue(
            isCompleteCachedContent(
                cacheSnapshot(
                    contentLength = 10L,
                    ranges = listOf(CacheCompletedRange(0L, 10L))
                )
            )
        )
        assertFalse(
            isCompleteCachedContent(
                cacheSnapshot(
                    contentLength = 10L,
                    ranges = listOf(
                        CacheCompletedRange(0L, 4L),
                        CacheCompletedRange(5L, 10L)
                    )
                )
            )
        )
    }

    @Test
    fun schedule_whenOldPrewarmCompletesAfterContextSwitch_shouldKeepNewSnapshot() = runBlocking {
        val snapshots = SnapshotRecorder(
            awaitedTargetId = "track-3",
            awaitedState = PlaybackPrewarmState.COMPLETED
        )
        val oldResolverEntered = CountDownLatch(1)
        val planner = OnlinePlaybackPreparationPlanner(
            cacheLookup = { Result.success(null) },
            sourceAdapterProvider = {
                FakePrewarmSourceAdapter(
                    blockingSongId = "song-2",
                    blockingEntered = oldResolverEntered,
                    blockingDelayMs = 80L
                )
            }
        )
        val coordinator = PlaybackPrewarmCoordinator(
            scope = CoroutineScope(Dispatchers.Default),
            planner = planner,
            cacheLookup = { Result.success(null) },
            openSession = { params ->
                Result.success(
                    DelayedCacheSession(
                        resourceKey = params.resourceKey
                    )
                )
            },
            providerFactory = { StaticRangeDataProvider(contentLength = 1_048_576L) },
            onSnapshot = snapshots::record,
            ioDispatcher = Dispatchers.Default
        )

        coordinator.schedule(prewarmContext(currentItemCacheCompleteOrNoWrite = true))
        assertTrue(oldResolverEntered.await(2, TimeUnit.SECONDS))
        coordinator.schedule(
            prewarmContext(
                currentItemCacheCompleteOrNoWrite = true,
                preferredAudioQuality = PlaybackAudioQuality.HIRES,
                nextSongId = "song-3",
                nextTrackId = "track-3"
            )
        )
        assertTrue(snapshots.await())

        val last = snapshots.last()
        assertEquals("track-3", last.targetId)
        assertEquals(PlaybackPrewarmState.COMPLETED, last.state)
        assertTrue(
            snapshots.none { snapshot ->
                snapshot.targetId == "track-2" &&
                    (
                        snapshot.state == PlaybackPrewarmState.READY ||
                            snapshot.state == PlaybackPrewarmState.COMPLETED ||
                            snapshot.state == PlaybackPrewarmState.FAILED
                    )
            }
        )
    }

    @Test
    fun schedule_whenAudioQualityChanges_shouldNotLetOldQualityOverwriteNewSnapshot() = runBlocking {
        val snapshots = SnapshotRecorder(
            awaitedTargetId = "track-2",
            awaitedState = PlaybackPrewarmState.COMPLETED
        )
        val oldResolverEntered = CountDownLatch(1)
        val planner = OnlinePlaybackPreparationPlanner(
            cacheLookup = { Result.success(null) },
            sourceAdapterProvider = {
                FakePrewarmSourceAdapter(
                    blockingSongId = "song-2",
                    blockingQuality = PlaybackAudioQuality.EXHIGH,
                    blockingEntered = oldResolverEntered,
                    blockingDelayMs = 80L
                )
            }
        )
        val coordinator = PlaybackPrewarmCoordinator(
            scope = CoroutineScope(Dispatchers.Default),
            planner = planner,
            cacheLookup = { Result.success(null) },
            openSession = { params ->
                Result.success(DelayedCacheSession(resourceKey = params.resourceKey))
            },
            providerFactory = { StaticRangeDataProvider(contentLength = 1_048_576L) },
            onSnapshot = snapshots::record,
            ioDispatcher = Dispatchers.Default
        )

        coordinator.schedule(
            prewarmContext(
                currentItemCacheCompleteOrNoWrite = true,
                preferredAudioQuality = PlaybackAudioQuality.EXHIGH
            )
        )
        assertTrue(oldResolverEntered.await(2, TimeUnit.SECONDS))
        coordinator.schedule(
            prewarmContext(
                currentItemCacheCompleteOrNoWrite = true,
                preferredAudioQuality = PlaybackAudioQuality.HIRES
            )
        )
        assertTrue(snapshots.await())

        val completed = snapshots.filter { it.state == PlaybackPrewarmState.COMPLETED }
        assertEquals(1, completed.size)
        assertEquals("track-2", completed.last().targetId)
    }

    @Test
    fun schedule_whenOldPrewarmFailsAfterContextSwitch_shouldKeepNewSnapshot() = runBlocking {
        val snapshots = SnapshotRecorder(
            awaitedTargetId = "track-3",
            awaitedState = PlaybackPrewarmState.COMPLETED
        )
        val oldResolverEntered = CountDownLatch(1)
        val planner = OnlinePlaybackPreparationPlanner(
            cacheLookup = { Result.success(null) },
            sourceAdapterProvider = {
                FakePrewarmSourceAdapter(
                    blockingSongId = "song-2",
                    blockingEntered = oldResolverEntered,
                    blockingDelayMs = 80L,
                    failingSongIds = setOf("song-2")
                )
            }
        )
        val coordinator = PlaybackPrewarmCoordinator(
            scope = CoroutineScope(Dispatchers.Default),
            planner = planner,
            cacheLookup = { Result.success(null) },
            openSession = { params ->
                Result.success(DelayedCacheSession(resourceKey = params.resourceKey))
            },
            providerFactory = { StaticRangeDataProvider(contentLength = 1_048_576L) },
            onSnapshot = snapshots::record,
            ioDispatcher = Dispatchers.Default
        )

        coordinator.schedule(prewarmContext(currentItemCacheCompleteOrNoWrite = true))
        assertTrue(oldResolverEntered.await(2, TimeUnit.SECONDS))
        coordinator.schedule(
            prewarmContext(
                currentItemCacheCompleteOrNoWrite = true,
                nextSongId = "song-3",
                nextTrackId = "track-3"
            )
        )
        assertTrue(snapshots.await())

        val last = snapshots.last()
        assertEquals("track-3", last.targetId)
        assertEquals(PlaybackPrewarmState.COMPLETED, last.state)
        assertTrue(
            snapshots.none { snapshot ->
                snapshot.targetId == "track-2" &&
                    snapshot.state == PlaybackPrewarmState.FAILED
            }
        )
    }

    @Test
    fun schedule_whenContextSwitchCancelsOldPrewarm_shouldNotLeaveCanceledAsFinalSnapshot() = runBlocking {
        val snapshots = SnapshotRecorder(
            awaitedTargetId = "track-3",
            awaitedState = PlaybackPrewarmState.COMPLETED
        )
        val oldResolverEntered = CountDownLatch(1)
        val logs = mutableListOf<String>()
        val planner = OnlinePlaybackPreparationPlanner(
            cacheLookup = { Result.success(null) },
            sourceAdapterProvider = {
                FakePrewarmSourceAdapter(
                    blockingSongId = "song-2",
                    blockingEntered = oldResolverEntered,
                    blockingDelayMs = 80L
                )
            }
        )
        val coordinator = PlaybackPrewarmCoordinator(
            scope = CoroutineScope(Dispatchers.Default),
            planner = planner,
            cacheLookup = { Result.success(null) },
            openSession = { params ->
                Result.success(DelayedCacheSession(resourceKey = params.resourceKey))
            },
            providerFactory = { StaticRangeDataProvider(contentLength = 1_048_576L) },
            onSnapshot = snapshots::record,
            debugLogger = logs::add,
            ioDispatcher = Dispatchers.Default
        )

        coordinator.schedule(prewarmContext(currentItemCacheCompleteOrNoWrite = true))
        assertTrue(oldResolverEntered.await(2, TimeUnit.SECONDS))
        coordinator.schedule(
            prewarmContext(
                currentItemCacheCompleteOrNoWrite = true,
                nextSongId = "song-3",
                nextTrackId = "track-3"
            )
        )
        assertTrue(snapshots.await())

        assertTrue(
            snapshots.any { snapshot ->
                snapshot.targetId == "track-2" &&
                    snapshot.state == PlaybackPrewarmState.CANCELED
            }
        )
        assertTrue(
            logs.any { log ->
                log.contains("state=canceled") &&
                    log.contains("key=<resolving>")
            }
        )
        val last = snapshots.last()
        assertEquals("track-3", last.targetId)
        assertEquals(PlaybackPrewarmState.COMPLETED, last.state)
    }

    @Test
    fun schedule_shouldLogResourceKeyForPrewarmStateBoundaries() = runBlocking {
        val snapshots = SnapshotRecorder(
            awaitedTargetId = "track-2",
            awaitedState = PlaybackPrewarmState.COMPLETED
        )
        val logs = mutableListOf<String>()
        val coordinator = PlaybackPrewarmCoordinator(
            scope = CoroutineScope(Dispatchers.Default),
            planner = OnlinePlaybackPreparationPlanner(
                cacheLookup = { Result.success(null) },
                sourceAdapterProvider = {
                    FakePrewarmSourceAdapter(
                        blockingSongId = "never-block",
                        blockingEntered = CountDownLatch(1),
                        blockingDelayMs = 0L
                    )
                }
            ),
            cacheLookup = { Result.success(null) },
            openSession = { params ->
                Result.success(DelayedCacheSession(resourceKey = params.resourceKey))
            },
            providerFactory = { StaticRangeDataProvider(contentLength = 1_048_576L) },
            onSnapshot = snapshots::record,
            debugLogger = logs::add,
            ioDispatcher = Dispatchers.Default
        )

        coordinator.schedule(prewarmContext(currentItemCacheCompleteOrNoWrite = true))

        assertTrue(snapshots.await())
        assertTrue(
            logs.any { log ->
                log.contains("state=running") &&
                    log.contains("key=song_song-2_exhigh_full")
            }
        )
        assertTrue(
            logs.any { log ->
                log.contains("state=completed") &&
                    log.contains("key=song_song-2_exhigh_full")
            }
        )
    }

    @Test
    fun schedule_shouldThrottleRunningProgressLogsByTenPercentBuckets() = runBlocking {
        val snapshots = SnapshotRecorder(
            awaitedTargetId = "track-2",
            awaitedState = PlaybackPrewarmState.COMPLETED
        )
        val logs = mutableListOf<String>()
        val coordinator = PlaybackPrewarmCoordinator(
            scope = CoroutineScope(Dispatchers.Default),
            planner = OnlinePlaybackPreparationPlanner(
                cacheLookup = { Result.success(null) },
                sourceAdapterProvider = {
                    FakePrewarmSourceAdapter(
                        blockingSongId = "never-block",
                        blockingEntered = CountDownLatch(1),
                        blockingDelayMs = 0L
                    )
                }
            ),
            cacheLookup = { Result.success(null) },
            openSession = { params ->
                Result.success(SmallChunkCacheSession(resourceKey = params.resourceKey))
            },
            providerFactory = { StaticRangeDataProvider(contentLength = 1_048_576L) },
            onSnapshot = snapshots::record,
            debugLogger = logs::add,
            ioDispatcher = Dispatchers.Default
        )

        coordinator.schedule(prewarmContext(currentItemCacheCompleteOrNoWrite = true))

        assertTrue(snapshots.await())
        val progressLogs = logs.filter { log ->
            log.startsWith("prewarm snapshot:") &&
                log.contains("target=track-2") &&
                (log.contains("state=running") || log.contains("state=ready"))
        }
        assertTrue("Expected bucketed logs, actual=${progressLogs.size}", progressLogs.size <= 13)
        assertTrue(logs.any { it.contains("state=completed") })
    }

    @Test
    fun schedule_whenCurrentCacheCompletionChanges_shouldNotRepeatNextTrackPrewarm() = runBlocking {
        val snapshots = SnapshotRecorder(
            awaitedTargetId = "track-2",
            awaitedState = PlaybackPrewarmState.COMPLETED
        )
        val adapter = FakePrewarmSourceAdapter(
            blockingSongId = "never-block",
            blockingEntered = CountDownLatch(1),
            blockingDelayMs = 0L
        )
        val coordinator = PlaybackPrewarmCoordinator(
            scope = CoroutineScope(Dispatchers.Default),
            planner = OnlinePlaybackPreparationPlanner(
                cacheLookup = { Result.success(null) },
                sourceAdapterProvider = { adapter }
            ),
            cacheLookup = { Result.success(null) },
            openSession = { params ->
                Result.success(DelayedCacheSession(resourceKey = params.resourceKey))
            },
            providerFactory = { StaticRangeDataProvider(contentLength = 1_048_576L) },
            onSnapshot = snapshots::record,
            ioDispatcher = Dispatchers.Default
        )
        val bufferedContext = prewarmContext(
            currentItemCacheCompleteOrNoWrite = false,
            currentCacheProgress = cacheProgress(displayRatio = 0.6f)
        )

        coordinator.schedule(bufferedContext)
        assertTrue(snapshots.await())
        coordinator.schedule(bufferedContext.copy(currentItemCacheCompleteOrNoWrite = true))
        delay(100L)

        assertEquals(1, adapter.handleCalls.get())
    }

    @Test
    fun schedule_whenOldResolveReturnsAfterNewStarts_shouldNotLogOldResourceKeyForNewSnapshots() = runBlocking {
        val snapshots = SnapshotRecorder(
            awaitedTargetId = "track-3",
            awaitedState = PlaybackPrewarmState.COMPLETED
        )
        val logs = LogRecorder()
        val oldResolverEntered = CountDownLatch(1)
        val planner = OnlinePlaybackPreparationPlanner(
            cacheLookup = { Result.success(null) },
            sourceAdapterProvider = {
                FakePrewarmSourceAdapter(
                    blockingSongId = "song-2",
                    blockingEntered = oldResolverEntered,
                    blockingDelayMs = 80L
                )
            }
        )
        val coordinator = PlaybackPrewarmCoordinator(
            scope = CoroutineScope(Dispatchers.Default),
            planner = planner,
            cacheLookup = { Result.success(null) },
            openSession = { params ->
                val session = if (params.resourceKey == "song_song-3_exhigh_full") {
                    SlowFirstReadCacheSession(
                        resourceKey = params.resourceKey,
                        firstReadDelayMs = 140L
                    )
                } else {
                    DelayedCacheSession(resourceKey = params.resourceKey)
                }
                Result.success(session)
            },
            providerFactory = { StaticRangeDataProvider(contentLength = 1_048_576L) },
            onSnapshot = snapshots::record,
            debugLogger = logs::record,
            ioDispatcher = Dispatchers.Default
        )

        coordinator.schedule(prewarmContext(currentItemCacheCompleteOrNoWrite = true))
        assertTrue(oldResolverEntered.await(2, TimeUnit.SECONDS))
        coordinator.schedule(
            prewarmContext(
                currentItemCacheCompleteOrNoWrite = true,
                nextSongId = "song-3",
                nextTrackId = "track-3"
            )
        )

        assertTrue(snapshots.await())
        assertTrue(
            logs.none { log ->
                log.contains("target=track-3") &&
                    log.contains("key=song_song-2_exhigh_full")
            }
        )
    }

    private fun prewarmContext(
        playbackMode: PlaybackMode = PlaybackMode.LIST_LOOP,
        currentItemCacheCompleteOrNoWrite: Boolean = false,
        currentPositionMs: Long = 10_000L,
        currentDurationMs: Long = 180_000L,
        currentCacheProgress: PlaybackCacheProgressSnapshot? = null,
        preferredAudioQuality: PlaybackAudioQuality = PlaybackAudioQuality.EXHIGH,
        currentTrackSongId: String? = "song-1",
        currentTrackUri: String = "https://example.com/track-1.m4a",
        nextSongId: String = "song-2",
        nextTrackId: String = "track-2"
    ): PlaybackPrewarmContext {
        return PlaybackPrewarmContext(
            tracks = listOf(
                track("track-1", songId = currentTrackSongId, uri = currentTrackUri),
                track(nextTrackId, songId = nextSongId)
            ),
            activeIndex = 0,
            playbackMode = playbackMode,
            currentPositionMs = currentPositionMs,
            currentDurationMs = currentDurationMs,
            currentCacheResourceKey = "online_song-1_exhigh_full",
            currentCacheContentLengthHintBytes = 9_000_000L,
            currentCacheProgress = currentCacheProgress,
            currentItemCacheCompleteOrNoWrite = currentItemCacheCompleteOrNoWrite,
            preferredAudioQuality = preferredAudioQuality,
            preferences = PlaybackPrewarmPreferences()
        )
    }

    private fun cacheProgress(displayRatio: Float): PlaybackCacheProgressSnapshot {
        return PlaybackCacheProgressSnapshot(
            cachedBytes = (displayRatio * 10_000_000L).toLong(),
            totalBytes = 10_000_000L,
            displayRatio = displayRatio,
            isFullyCached = false,
            isEstimated = false
        )
    }

    private fun track(
        id: String,
        songId: String?,
        uri: String = "https://example.com/$id.m4a"
    ): PlaybackTrack {
        return PlaybackTrack(
            playable = PlayableItemSnapshot(
                id = id,
                songId = songId,
                title = id,
                artistText = "artist",
                durationMs = 180_000L,
                playbackUri = uri
            )
        )
    }

    private fun cacheSnapshot(
        contentLength: Long,
        ranges: List<CacheCompletedRange>
    ): CacheLookupSnapshot {
        return CacheLookupSnapshot(
            resourceKey = "key",
            dataFilePath = "",
            configFilePath = "",
            extraFilePath = "",
            dataFileSizeBytes = contentLength,
            blockSizeBytes = 64 * 1024,
            contentLength = contentLength,
            durationMs = 180_000L,
            cachedBlocks = emptySet(),
            lastAccessEpochMs = 0L,
            completedRanges = ranges
        )
    }

    private class DelayedCacheSession(
        override val resourceKey: String
    ) : CacheSession {
        override val sessionId: Long = resourceKey.hashCode().toLong()

        override fun read(size: Int): Result<ByteArray> {
            return readAt(0L, size)
        }

        override fun readAt(offset: Long, size: Int): Result<ByteArray> {
            return Result.success(ByteArray(size.coerceAtMost(256 * 1024)))
        }

        override fun seek(offset: Long, whence: Int): Result<Long> {
            return Result.success(offset)
        }

        override fun cancelPendingRead() = Unit

        override fun close() = Unit
    }

    private class SlowFirstReadCacheSession(
        override val resourceKey: String,
        private val firstReadDelayMs: Long
    ) : CacheSession {
        override val sessionId: Long = resourceKey.hashCode().toLong()
        private var readCount = 0

        override fun read(size: Int): Result<ByteArray> {
            return readAt(0L, size)
        }

        override fun readAt(offset: Long, size: Int): Result<ByteArray> {
            readCount += 1
            if (readCount == 1) {
                Thread.sleep(firstReadDelayMs)
            }
            return Result.success(ByteArray(size.coerceAtMost(256 * 1024)))
        }

        override fun seek(offset: Long, whence: Int): Result<Long> {
            return Result.success(offset)
        }

        override fun cancelPendingRead() = Unit

        override fun close() = Unit
    }

    private class SmallChunkCacheSession(
        override val resourceKey: String
    ) : CacheSession {
        override val sessionId: Long = resourceKey.hashCode().toLong()

        override fun read(size: Int): Result<ByteArray> {
            return readAt(0L, size)
        }

        override fun readAt(offset: Long, size: Int): Result<ByteArray> {
            return Result.success(ByteArray(size.coerceAtMost(32 * 1024)))
        }

        override fun seek(offset: Long, whence: Int): Result<Long> = Result.success(offset)

        override fun cancelPendingRead() = Unit

        override fun close() = Unit
    }

    private class StaticRangeDataProvider(
        private val contentLength: Long
    ) : RangeDataProvider {
        override fun readAt(offset: Long, size: Int, callback: RangeDataProvider.ReadCallback) {
            callback.onDataBegin(offset, size)
            callback.onDataSend(ByteArray(size.coerceAtMost(256 * 1024)), size.coerceAtMost(256 * 1024))
            callback.onDataEnd(true)
        }

        override fun cancelInFlightRead() = Unit

        override fun queryContentLength(): Long = contentLength
    }

    private class FakePrewarmSourceAdapter(
        private val blockingSongId: String,
        private val blockingQuality: PlaybackAudioQuality? = null,
        private val blockingEntered: CountDownLatch,
        private val blockingDelayMs: Long,
        private val failingSongIds: Set<String> = emptySet()
    ) : SourceAdapter {
        val handleCalls = AtomicInteger(0)
        override val metadata: SourceMetadata = SourceMetadata(
            id = "fake-prewarm-source",
            name = "Fake Prewarm Source"
        )
        override val normalizedConfigJson: String? = null

        override fun init(): Result<SourceState> = Result.success(SourceState())

        override suspend fun handle(
            action: SourceAction,
            context: SourceActionContext
        ): Result<SourceActionResult> {
            handleCalls.incrementAndGet()
            if (
                context.songId == blockingSongId &&
                (blockingQuality == null || context.preferredAudioQuality == blockingQuality)
            ) {
                blockingEntered.countDown()
                Thread.sleep(blockingDelayMs)
            }
            if (context.songId in failingSongIds) {
                return Result.failure(IllegalStateException("forced failure for ${context.songId}"))
            }
            return Result.success(
                SourceActionResult.MusicUrl(
                    playbackUrl = "https://example.com/${context.songId}.m4a",
                    requestHeaders = emptyMap(),
                    contentLengthBytes = 1_048_576L,
                    durationMs = 180_000L,
                    expiresAtMs = 5_000L,
                    previewClip = null,
                    appliedAudioQuality = context.preferredAudioQuality
                )
            )
        }
    }

    private class SnapshotRecorder(
        private val awaitedTargetId: String,
        private val awaitedState: PlaybackPrewarmState
    ) {
        private val lock = Any()
        private val snapshots = mutableListOf<PlaybackPrewarmSnapshot>()
        private val awaitedSnapshot = CountDownLatch(1)

        fun record(snapshot: PlaybackPrewarmSnapshot) {
            synchronized(lock) {
                snapshots += snapshot
            }
            if (snapshot.targetId == awaitedTargetId && snapshot.state == awaitedState) {
                awaitedSnapshot.countDown()
            }
        }

        fun await(): Boolean {
            return awaitedSnapshot.await(2, TimeUnit.SECONDS)
        }

        fun last(): PlaybackPrewarmSnapshot {
            return synchronized(lock) { snapshots.last() }
        }

        fun any(predicate: (PlaybackPrewarmSnapshot) -> Boolean): Boolean {
            return synchronized(lock) { snapshots.any(predicate) }
        }

        fun none(predicate: (PlaybackPrewarmSnapshot) -> Boolean): Boolean {
            return synchronized(lock) { snapshots.none(predicate) }
        }

        fun filter(predicate: (PlaybackPrewarmSnapshot) -> Boolean): List<PlaybackPrewarmSnapshot> {
            return synchronized(lock) { snapshots.filter(predicate) }
        }
    }

    private class LogRecorder {
        private val lock = Any()
        private val logs = mutableListOf<String>()

        fun record(log: String) {
            synchronized(lock) {
                logs += log
            }
        }

        fun none(predicate: (String) -> Boolean): Boolean {
            return synchronized(lock) { logs.none(predicate) }
        }
    }
}
