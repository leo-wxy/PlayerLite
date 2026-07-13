package com.wxy.playerlite.playback.process

import android.util.Log
import com.wxy.playerlite.cache.core.CacheCompletedRange
import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.CacheLookupSnapshot
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.CacheSession
import com.wxy.playerlite.cache.core.session.OpenSessionParams
import com.wxy.playerlite.cache.core.session.SessionCacheConfig
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.model.PlaybackPrewarmPreferences
import com.wxy.playerlite.playback.model.PlaybackPrewarmSnapshot
import com.wxy.playerlite.playback.model.PlaybackPrewarmState
import com.wxy.playerlite.playback.model.PlaybackPrewarmTargetType
import com.wxy.playerlite.playback.process.source.HttpRangeDataProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal data class PlaybackPrewarmContext(
    val tracks: List<PlaybackTrack>,
    val activeIndex: Int,
    val playbackMode: PlaybackMode,
    val currentPositionMs: Long,
    val currentDurationMs: Long,
    val currentCacheResourceKey: String?,
    val currentCacheContentLengthHintBytes: Long?,
    val currentCacheProgress: PlaybackCacheProgressSnapshot?,
    val currentItemCacheCompleteOrNoWrite: Boolean,
    val preferredAudioQuality: PlaybackAudioQuality,
    val preferences: PlaybackPrewarmPreferences
)

internal data class PlaybackPrewarmCandidate(
    val track: PlaybackTrack,
    val targetType: PlaybackPrewarmTargetType,
    val startOffsetBytes: Long? = null,
    val reason: String? = null
) {
    val signature: String
        get() = listOf(
            track.id,
            targetType.wireValue,
            startOffsetBytes ?: -1L,
            track.songId.orEmpty(),
            track.sourceContextSignature()
        ).joinToString("|")
}

internal class PlaybackPrewarmCoordinator(
    private val scope: CoroutineScope,
    private val planner: OnlinePlaybackPreparationPlanner,
    private val cacheLookup: (String) -> Result<CacheLookupSnapshot?> = CacheCore::lookup,
    private val openSession: (OpenSessionParams) -> Result<CacheSession> = CacheCore::openSession,
    private val providerFactory: (OnlinePlaybackPlan) -> RangeDataProvider = { plan ->
        HttpRangeDataProvider(
            url = plan.playbackUrl.orEmpty(),
            requestHeaders = plan.requestHeaders
        )
    },
    private val onSnapshot: (PlaybackPrewarmSnapshot) -> Unit,
    private val onFinished: () -> Unit = {},
    private val debugLogger: (String) -> Unit = ::safeLogD,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var activeJob: Job? = null
    private var activeSignature: String? = null
    private var lastFinishedSignature: String? = null
    private var activeSnapshot: PlaybackPrewarmSnapshot? = null
    private var activeResourceKey: String? = null

    fun schedule(context: PlaybackPrewarmContext) {
        val preferences = context.preferences.sanitized()
        val skipped = resolvePrewarmSkipSnapshot(context.copy(preferences = preferences))
        if (skipped != null) {
            cancelActive(reason = skipped.reason ?: "预热已跳过", publishCanceled = false)
            publish(skipped)
            return
        }

        val candidate = resolvePlaybackPrewarmCandidate(context.copy(preferences = preferences))
            ?: run {
                cancelActive(reason = "没有可预热目标", publishCanceled = false)
                publish(
                    PlaybackPrewarmSnapshot(
                        targetId = null,
                        targetType = PlaybackPrewarmTargetType.NEXT_TRACK,
                        state = PlaybackPrewarmState.SKIPPED,
                        reason = "没有可预热目标"
                    )
                )
                return
            }
        val signature = listOf(
            candidate.signature,
            preferences.enabled,
            preferences.budgetDurationMs,
            preferences.budgetBytes,
            preferences.readyThresholdDurationMs,
            preferences.readyThresholdBytes,
            context.preferredAudioQuality.wireValue,
            context.currentItemCacheCompleteOrNoWrite,
            if (candidate.targetType == PlaybackPrewarmTargetType.CURRENT_AHEAD) {
                context.currentPositionMs / CURRENT_AHEAD_SIGNATURE_BUCKET_MS
            } else {
                -1L
            }
        ).joinToString("|")
        if (activeSignature == signature && activeJob?.isActive == true) {
            return
        }
        if (lastFinishedSignature == signature) {
            return
        }
        cancelActive(reason = "预热上下文变化", publishCanceled = true)
        activeSignature = signature
        activeResourceKey = null
        val resolving = PlaybackPrewarmSnapshot(
            targetId = candidate.track.id,
            targetType = candidate.targetType,
            state = PlaybackPrewarmState.RESOLVING,
            reason = candidate.reason
        )
        val job = scope.launch(start = CoroutineStart.LAZY) {
            runCandidate(
                candidate = candidate,
                context = context.copy(preferences = preferences),
                signature = signature
            )
        }
        activeJob = job
        publish(resolving, signature)
        job.start()
    }

    fun cancel(reason: String) {
        cancelActive(reason = reason, publishCanceled = true)
    }

    private suspend fun runCandidate(
        candidate: PlaybackPrewarmCandidate,
        context: PlaybackPrewarmContext,
        signature: String
    ) {
        var provider: RangeDataProvider? = null
        var session: CacheSession? = null
        var finished = false
        var resolvedResourceKey: String? = null
        try {
            val plan = planner.buildPlan(
                track = candidate.track,
                preferredAudioQuality = context.preferredAudioQuality
            ).getOrElse { error ->
                publishIfActive(
                    signature,
                    candidate.snapshot(
                        state = PlaybackPrewarmState.FAILED,
                        reason = error.message ?: "预热资源解析失败"
                    )
                )
                finished = true
                return
            }
            resolvedResourceKey = plan.resourceKey
            markResourceKeyIfActive(signature, plan.resourceKey)
            if (plan.useCacheOnlyProvider || isCompleteCachedContent(cacheLookup(plan.resourceKey).getOrNull())) {
                publishIfActive(
                    signature,
                    candidate.snapshot(
                        state = PlaybackPrewarmState.SKIPPED,
                        cachedBytes = plan.contentLengthHintBytes ?: 0L,
                        targetBytes = plan.contentLengthHintBytes,
                        cachedDurationMs = plan.durationHintMs.takeIf { it > 0L },
                        targetDurationMs = plan.durationHintMs.takeIf { it > 0L },
                        isReady = true,
                        isCompleted = true,
                        reason = "目标已完整缓存"
                    ),
                    resourceKey = resolvedResourceKey
                )
                finished = true
                return
            }
            if (plan.playbackUrl.isNullOrBlank()) {
                publishIfActive(
                    signature,
                    candidate.snapshot(
                        state = PlaybackPrewarmState.SKIPPED,
                        reason = "目标缺少可预热 URL"
                    ),
                    resourceKey = resolvedResourceKey
                )
                finished = true
                return
            }

            val activeProvider = providerFactory(plan)
            provider = activeProvider
            val contentLength = plan.contentLengthHintBytes
                ?: withContext(ioDispatcher) { activeProvider.queryContentLength() }
            val startOffset = resolvePrewarmStartOffset(
                candidate = candidate,
                contentLength = contentLength,
                currentPositionMs = context.currentPositionMs,
                currentDurationMs = context.currentDurationMs
            )
            val targetBytes = resolvePrewarmTargetBytes(
                preferences = context.preferences,
                contentLength = contentLength,
                durationMs = plan.durationHintMs,
                startOffset = startOffset
            )
            if (targetBytes <= 0L) {
                publishIfActive(
                    signature,
                    candidate.snapshot(
                        state = PlaybackPrewarmState.SKIPPED,
                        targetBytes = targetBytes,
                        reason = "预热预算为空"
                    ),
                    resourceKey = resolvedResourceKey
                )
                finished = true
                return
            }
            val readyBytes = resolvePrewarmReadyBytes(
                preferences = context.preferences,
                contentLength = contentLength,
                durationMs = plan.durationHintMs
            ).coerceAtMost(targetBytes)

            val activeSession = openSession(
                OpenSessionParams(
                    resourceKey = plan.resourceKey,
                    provider = activeProvider,
                    config = SessionCacheConfig(),
                    contentLengthHint = contentLength,
                    durationMsHint = plan.durationHintMs.takeIf { it > 0L },
                    extra = plan.cacheExtraMetadata
                )
            ).getOrElse { error ->
                publishIfActive(
                    signature,
                    candidate.snapshot(
                        state = PlaybackPrewarmState.FAILED,
                        targetBytes = targetBytes,
                        targetDurationMs = context.preferences.budgetDurationMs,
                        reason = "打开缓存会话失败: ${error.message ?: "unknown"}"
                    ),
                    resourceKey = resolvedResourceKey
                )
                finished = true
                return
            }
            session = activeSession
            persistOnlineCacheMetadata(plan.resourceKey, plan.cacheExtraMetadata)

            var readyPublished = false
            var cachedBytes = contiguousCachedBytes(
                snapshot = cacheLookup(plan.resourceKey).getOrNull(),
                startOffset = startOffset,
                maxBytes = targetBytes
            )
            publishProgress(
                signature = signature,
                candidate = candidate,
                state = PlaybackPrewarmState.RUNNING,
                cachedBytes = cachedBytes,
                targetBytes = targetBytes,
                contentLength = contentLength,
                durationMs = plan.durationHintMs,
                preferences = context.preferences,
                reason = "预热中",
                resourceKey = resolvedResourceKey
            )
            while (cachedBytes < targetBytes) {
                coroutineContext.ensureActive()
                val nextOffset = startOffset + cachedBytes
                val requestBytes = (targetBytes - cachedBytes)
                    .coerceAtMost(PREWARM_READ_CHUNK_BYTES)
                    .toInt()
                val bytes = withContext(ioDispatcher) {
                    activeSession.readAt(nextOffset, requestBytes).getOrThrow()
                }
                if (bytes.isEmpty()) {
                    break
                }
                persistOnlineCacheMetadata(plan.resourceKey, plan.cacheExtraMetadata)
                cachedBytes = contiguousCachedBytes(
                    snapshot = cacheLookup(plan.resourceKey).getOrNull(),
                    startOffset = startOffset,
                    maxBytes = targetBytes
                ).coerceAtLeast(cachedBytes + bytes.size)
                    .coerceAtMost(targetBytes)
                if (!readyPublished && cachedBytes >= readyBytes) {
                    readyPublished = true
                    publishProgress(
                        signature = signature,
                        candidate = candidate,
                        state = PlaybackPrewarmState.READY,
                        cachedBytes = cachedBytes,
                        targetBytes = targetBytes,
                        contentLength = contentLength,
                        durationMs = plan.durationHintMs,
                        preferences = context.preferences,
                        isReady = true,
                        reason = "达到快速起播阈值",
                        resourceKey = resolvedResourceKey
                    )
                } else {
                    publishProgress(
                        signature = signature,
                        candidate = candidate,
                        state = PlaybackPrewarmState.RUNNING,
                        cachedBytes = cachedBytes,
                        targetBytes = targetBytes,
                        contentLength = contentLength,
                        durationMs = plan.durationHintMs,
                        preferences = context.preferences,
                        isReady = readyPublished,
                        reason = "预热中",
                        resourceKey = resolvedResourceKey
                    )
                }
            }
            val snapshot = cacheLookup(plan.resourceKey).getOrNull()
            val complete = cachedBytes >= targetBytes || isCompleteCachedContent(snapshot)
            publishProgress(
                signature = signature,
                candidate = candidate,
                state = if (complete) PlaybackPrewarmState.COMPLETED else PlaybackPrewarmState.FAILED,
                cachedBytes = cachedBytes,
                targetBytes = targetBytes,
                contentLength = contentLength,
                durationMs = plan.durationHintMs,
                preferences = context.preferences,
                isReady = readyPublished || cachedBytes >= readyBytes || complete,
                isCompleted = complete,
                reason = if (complete) "预热预算已完成" else "预热读取未达到预算",
                resourceKey = resolvedResourceKey
            )
            finished = true
        } catch (_: CancellationException) {
            throw CancellationException("预热已取消")
        } catch (error: Throwable) {
            publishIfActive(
                signature,
                candidate.snapshot(
                    state = PlaybackPrewarmState.FAILED,
                    reason = error.message ?: "预热失败"
                ),
                resourceKey = resolvedResourceKey
            )
            finished = true
        } finally {
            runCatching { session?.close() }
            runCatching { provider?.close() }
            if (activeSignature == signature && activeJob === coroutineContext[Job]) {
                if (finished) {
                    lastFinishedSignature = signature
                }
                activeJob = null
                activeSignature = null
                activeSnapshot = null
                activeResourceKey = null
                onFinished()
            }
        }
    }

    private suspend fun publishProgress(
        signature: String,
        candidate: PlaybackPrewarmCandidate,
        state: PlaybackPrewarmState,
        cachedBytes: Long,
        targetBytes: Long,
        contentLength: Long?,
        durationMs: Long,
        preferences: PlaybackPrewarmPreferences,
        isReady: Boolean = false,
        isCompleted: Boolean = false,
        reason: String,
        resourceKey: String? = activeResourceKey
    ) {
        publishIfActive(
            signature,
            candidate.snapshot(
                state = state,
                cachedBytes = cachedBytes,
                targetBytes = targetBytes,
                cachedDurationMs = estimateDurationMs(
                    bytes = cachedBytes,
                    contentLength = contentLength,
                    durationMs = durationMs
                ),
                targetDurationMs = preferences.budgetDurationMs,
                isReady = isReady,
                isCompleted = isCompleted,
                reason = reason
            ),
            resourceKey = resourceKey
        )
    }

    private suspend fun publishIfActive(
        signature: String,
        snapshot: PlaybackPrewarmSnapshot,
        resourceKey: String? = activeResourceKey
    ) {
        if (activeSignature != signature || activeJob !== coroutineContext[Job]) {
            debugLogger(
                "prewarm snapshot dropped: signature=$signature active=${activeSignature.orEmpty()}, key=${resourceKey ?: "<resolving>"}, target=${snapshot.targetId}, type=${snapshot.targetType.wireValue}, state=${snapshot.state.wireValue}, reason=${snapshot.reason.orEmpty()}"
            )
            return
        }
        publish(snapshot, signature, resourceKey)
    }

    private suspend fun markResourceKeyIfActive(
        signature: String,
        resourceKey: String
    ) {
        if (activeSignature == signature && activeJob === coroutineContext[Job]) {
            activeResourceKey = resourceKey
        }
    }

    private fun publish(
        snapshot: PlaybackPrewarmSnapshot,
        signature: String? = null,
        resourceKey: String? = activeResourceKey
    ) {
        if (snapshot == activeSnapshot) {
            return
        }
        activeSnapshot = snapshot
        debugLogger(
            "prewarm snapshot: signature=${signature.orEmpty()}, key=${resourceKey ?: "<resolving>"}, target=${snapshot.targetId}, type=${snapshot.targetType.wireValue}, state=${snapshot.state.wireValue}, cached=${snapshot.cachedBytes}, target=${snapshot.targetBytes ?: -1L}, ready=${snapshot.isReady}, completed=${snapshot.isCompleted}, reason=${snapshot.reason.orEmpty()}"
        )
        onSnapshot(snapshot)
    }

    private fun cancelActive(reason: String, publishCanceled: Boolean) {
        val previous = activeSnapshot
        val previousSignature = activeSignature
        val previousResourceKey = activeResourceKey
        activeJob?.cancel(CancellationException(reason))
        activeJob = null
        activeSignature = null
        activeSnapshot = null
        activeResourceKey = null
        if (publishCanceled && previous != null) {
            publish(
                previous.copy(
                    state = PlaybackPrewarmState.CANCELED,
                    isCompleted = false,
                    reason = reason
                ),
                previousSignature,
                previousResourceKey
            )
        }
    }

    private fun persistOnlineCacheMetadata(
        resourceKey: String,
        extraMetadata: Map<String, String>
    ) {
        if (extraMetadata.isEmpty()) {
            return
        }
        cacheLookup(resourceKey)
            .getOrNull()
            ?.let { snapshot ->
                OnlineCacheMetadata.persist(snapshot.extraFilePath, extraMetadata)
            }
    }

    private fun PlaybackPrewarmCandidate.snapshot(
        state: PlaybackPrewarmState,
        cachedBytes: Long = 0L,
        targetBytes: Long? = null,
        cachedDurationMs: Long? = null,
        targetDurationMs: Long? = null,
        isReady: Boolean = false,
        isCompleted: Boolean = false,
        reason: String? = null
    ): PlaybackPrewarmSnapshot {
        return PlaybackPrewarmSnapshot(
            targetId = track.id,
            targetType = targetType,
            state = state,
            cachedBytes = cachedBytes.coerceAtLeast(0L),
            targetBytes = targetBytes,
            cachedDurationMs = cachedDurationMs,
            targetDurationMs = targetDurationMs,
            isReady = isReady,
            isCompleted = isCompleted,
            reason = reason
        )
    }

    private companion object {
        private const val TAG = "PlaybackPrewarm"
        private const val PREWARM_READ_CHUNK_BYTES = 256L * 1024L
        private const val CURRENT_AHEAD_SIGNATURE_BUCKET_MS = 5_000L

        private fun safeLogD(message: String) {
            runCatching { Log.d(TAG, message) }
        }
    }
}

internal fun resolvePrewarmSkipSnapshot(
    context: PlaybackPrewarmContext
): PlaybackPrewarmSnapshot? {
    val preferences = context.preferences.sanitized()
    if (!preferences.enabled) {
        return PlaybackPrewarmSnapshot(
            targetId = null,
            targetType = PlaybackPrewarmTargetType.NEXT_TRACK,
            state = PlaybackPrewarmState.SKIPPED,
            reason = "预热已关闭"
        )
    }
    return null
}

internal fun resolvePlaybackPrewarmCandidate(
    context: PlaybackPrewarmContext
): PlaybackPrewarmCandidate? {
    val currentReadyForNextTrack = context.currentItemCacheCompleteOrNoWrite ||
        hasStableCurrentBufferForNextTrackPrewarm(
            currentPositionMs = context.currentPositionMs,
            currentDurationMs = context.currentDurationMs,
            cacheProgress = context.currentCacheProgress,
            preferences = context.preferences
        )
    if (!currentReadyForNextTrack) {
        val current = context.tracks.getOrNull(context.activeIndex) ?: return null
        if (!current.isOnlineTrack()) {
            return null
        }
        if (context.currentPositionMs <= 0L) {
            return null
        }
        return PlaybackPrewarmCandidate(
            track = current,
            targetType = PlaybackPrewarmTargetType.CURRENT_AHEAD,
            startOffsetBytes = null,
            reason = "当前曲前方窗口"
        )
    }
    val next = resolveNextPrewarmTrack(
        tracks = context.tracks,
        activeIndex = context.activeIndex,
        playbackMode = context.playbackMode
    ) ?: return null
    if (!next.isOnlineTrack()) {
        return null
    }
    return PlaybackPrewarmCandidate(
        track = next,
        targetType = PlaybackPrewarmTargetType.NEXT_TRACK,
        startOffsetBytes = 0L,
        reason = "下一首首段"
    )
}

internal fun hasStableCurrentBufferForNextTrackPrewarm(
    currentPositionMs: Long,
    currentDurationMs: Long,
    cacheProgress: PlaybackCacheProgressSnapshot?,
    preferences: PlaybackPrewarmPreferences
): Boolean {
    val progress = cacheProgress ?: return false
    if (progress.isFullyCached) {
        return true
    }
    if (currentDurationMs <= 0L) {
        return false
    }
    val bufferedEndMs = (progress.normalizedDisplayRatio * currentDurationMs.toDouble())
        .toLong()
        .coerceIn(0L, currentDurationMs)
    val requiredAheadMs = maxOf(
        MIN_CURRENT_BUFFER_AHEAD_FOR_NEXT_TRACK_MS,
        preferences.sanitized().readyThresholdDurationMs * 2L
    )
    return bufferedEndMs - currentPositionMs.coerceAtLeast(0L) >= requiredAheadMs
}

internal fun resolveNextPrewarmTrack(
    tracks: List<PlaybackTrack>,
    activeIndex: Int,
    playbackMode: PlaybackMode
): PlaybackTrack? {
    if (activeIndex !in tracks.indices || tracks.size <= 1) {
        return null
    }
    return when (playbackMode) {
        PlaybackMode.SINGLE_LOOP -> null
        PlaybackMode.SHUFFLE -> null
        PlaybackMode.LIST_LOOP -> {
            val nextIndex = if (activeIndex == tracks.lastIndex) 0 else activeIndex + 1
            tracks.getOrNull(nextIndex)
        }
    }
}

internal fun isCompleteCachedContent(snapshot: CacheLookupSnapshot?): Boolean {
    snapshot ?: return false
    if (snapshot.contentLength <= 0L) {
        return false
    }
    return contiguousCachedBytes(
        snapshot = snapshot,
        startOffset = 0L,
        maxBytes = snapshot.contentLength
    ) >= snapshot.contentLength
}

internal fun contiguousCachedBytes(
    snapshot: CacheLookupSnapshot?,
    startOffset: Long,
    maxBytes: Long
): Long {
    if (snapshot == null || maxBytes <= 0L) {
        return 0L
    }
    val normalizedStart = startOffset.coerceAtLeast(0L)
    val merged = mergeCompletedRanges(snapshot.completedRanges)
    val range = merged.firstOrNull {
        normalizedStart >= it.start && normalizedStart < it.endExclusive
    } ?: return 0L
    return (range.endExclusive - normalizedStart)
        .coerceAtLeast(0L)
        .coerceAtMost(maxBytes)
}

private fun mergeCompletedRanges(ranges: List<CacheCompletedRange>): List<CacheCompletedRange> {
    return ranges
        .filter { it.endExclusive > it.start }
        .sortedBy { it.start }
        .fold(mutableListOf<CacheCompletedRange>()) { merged, range ->
            val last = merged.lastOrNull()
            if (last == null || range.start > last.endExclusive) {
                merged += range
            } else if (range.endExclusive > last.endExclusive) {
                merged[merged.lastIndex] = last.copy(endExclusive = range.endExclusive)
            }
            merged
        }
}

private const val MIN_CURRENT_BUFFER_AHEAD_FOR_NEXT_TRACK_MS = 15_000L

private fun resolvePrewarmStartOffset(
    candidate: PlaybackPrewarmCandidate,
    contentLength: Long?,
    currentPositionMs: Long,
    currentDurationMs: Long
): Long {
    if (candidate.targetType == PlaybackPrewarmTargetType.NEXT_TRACK) {
        return 0L
    }
    val explicitOffset = candidate.startOffsetBytes
    if (explicitOffset != null) {
        return explicitOffset.coerceAtLeast(0L)
    }
    val totalBytes = contentLength?.takeIf { it > 0L } ?: return 0L
    if (currentDurationMs <= 0L || currentPositionMs <= 0L) {
        return 0L
    }
    return (totalBytes.toDouble() * (currentPositionMs.toDouble() / currentDurationMs.toDouble()))
        .toLong()
        .coerceIn(0L, totalBytes - 1L)
}

private fun resolvePrewarmTargetBytes(
    preferences: PlaybackPrewarmPreferences,
    contentLength: Long?,
    durationMs: Long,
    startOffset: Long
): Long {
    val budgetBytes = preferences.budgetBytes.coerceAtLeast(1L)
    val durationBudgetBytes = bytesForDurationBudget(
        requestedDurationMs = preferences.budgetDurationMs,
        contentLength = contentLength,
        durationMs = durationMs
    )
    val target = minOf(budgetBytes, durationBudgetBytes ?: budgetBytes)
    val remaining = contentLength
        ?.takeIf { it > 0L }
        ?.let { (it - startOffset).coerceAtLeast(0L) }
    return if (remaining != null) {
        target.coerceAtMost(remaining)
    } else {
        target
    }
}

private fun resolvePrewarmReadyBytes(
    preferences: PlaybackPrewarmPreferences,
    contentLength: Long?,
    durationMs: Long
): Long {
    val readyBytes = preferences.readyThresholdBytes.coerceAtLeast(1L)
    val durationReadyBytes = bytesForDurationBudget(
        requestedDurationMs = preferences.readyThresholdDurationMs,
        contentLength = contentLength,
        durationMs = durationMs
    )
    return minOf(readyBytes, durationReadyBytes ?: readyBytes).coerceAtLeast(1L)
}

private fun bytesForDurationBudget(
    requestedDurationMs: Long,
    contentLength: Long?,
    durationMs: Long
): Long? {
    val totalBytes = contentLength?.takeIf { it > 0L } ?: return null
    if (durationMs <= 0L || requestedDurationMs <= 0L) {
        return null
    }
    return (totalBytes.toDouble() * (requestedDurationMs.toDouble() / durationMs.toDouble()))
        .toLong()
        .coerceAtLeast(1L)
}

private fun estimateDurationMs(
    bytes: Long,
    contentLength: Long?,
    durationMs: Long
): Long? {
    val totalBytes = contentLength?.takeIf { it > 0L } ?: return null
    if (durationMs <= 0L || bytes <= 0L) {
        return null
    }
    return (durationMs.toDouble() * (bytes.toDouble() / totalBytes.toDouble()))
        .toLong()
        .coerceAtLeast(0L)
}

private fun PlaybackTrack.isOnlineTrack(): Boolean {
    return !songId.isNullOrBlank() ||
        uri.startsWith("http://", ignoreCase = true) ||
        uri.startsWith("https://", ignoreCase = true)
}

private fun PlaybackTrack.sourceContextSignature(): String {
    return playable.sourceContext?.let { context ->
        listOf(
            context.useDefaultSource,
            context.sourceConfigJson.orEmpty()
        ).joinToString(":")
    }.orEmpty()
}
