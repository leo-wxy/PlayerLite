package com.wxy.playerlite.playback.process.source

import android.util.Log
import com.wxy.playerlite.cache.core.CacheCompletedRange
import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.CacheProgressChunk
import com.wxy.playerlite.cache.core.session.CacheProgressChunkEmitter
import com.wxy.playerlite.cache.core.session.CacheSession
import com.wxy.playerlite.cache.core.session.OpenSessionParams
import com.wxy.playerlite.cache.core.session.SessionCacheConfig
import com.wxy.playerlite.player.source.IDirectReadableSource
import com.wxy.playerlite.player.source.IPlaysource
import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot
import com.wxy.playerlite.playback.process.OnlineCacheMetadata
import com.wxy.playerlite.playback.process.PlaybackCacheProgressEmitter
import java.nio.ByteBuffer

internal class CachedNetworkSource(
    private val resourceKey: String,
    private val provider: RangeDataProvider,
    private val sessionConfig: SessionCacheConfig,
    private val contentLengthHint: Long? = null,
    private val durationMsHint: Long? = null,
    private val extraMetadata: Map<String, String> = emptyMap(),
    private val onCacheFailure: (String) -> Unit = {}
) : IPlaysource, IDirectReadableSource, PlaybackCacheProgressEmitter {
    private var sourceMode: IPlaysource.SourceMode = IPlaysource.SourceMode.NORMAL
    private var opened = false
    private var aborted = false
    private var closed = false
    private var position = 0L
    private var seekSerial = 0L
    private var seekCallbackCount = 0L
    private var seekFailureCount = 0L
    private var lastLoggedCacheProgressBucket = -1
    private var contentLength: Long = contentLengthHint?.takeIf { it > 0L } ?: -1L
    private var session: CacheSession? = null
    private val cacheProgressTracker = ObservedCacheProgressTracker()

    @Volatile
    private var cacheProgressListener: ((PlaybackCacheProgressSnapshot?) -> Unit)? = null

    @Volatile
    private var lastEmittedCacheProgress: PlaybackCacheProgressSnapshot? = null

    override val sourceId: String
        get() = resourceKey

    override fun setSourceMode(mode: IPlaysource.SourceMode) {
        sourceMode = mode
    }

    override fun setCacheProgressListener(listener: ((PlaybackCacheProgressSnapshot?) -> Unit)?) {
        cacheProgressListener = listener
        if (listener == null) {
            lastEmittedCacheProgress = null
            lastLoggedCacheProgressBucket = -1
            return
        }
        emitCacheProgress(force = true)
    }

    override fun open(): IPlaysource.AudioSourceCode {
        synchronized(this) {
            if (aborted || closed) {
                return IPlaysource.AudioSourceCode.ASC_ABORT
            }
            if (opened && session != null) {
                return IPlaysource.AudioSourceCode.ASC_SUCCESS
            }
            val cachedLengthHint = contentLength.takeIf { it > 0L }
            fun openSession() = CacheCore.openSession(
                OpenSessionParams(
                    resourceKey = resourceKey,
                    provider = provider,
                    config = sessionConfig,
                    contentLengthHint = cachedLengthHint,
                    durationMsHint = durationMsHint?.takeIf { it > 0L }
                )
            )
            var openResult = openSession()
            if (openResult.isFailure) {
                val existingSnapshot = CacheCore.lookup(resourceKey).getOrNull()
                if (existingSnapshot != null) {
                    safeLogE(
                        "openSession failed once; purging stale snapshot: key=$resourceKey, error=${openResult.exceptionOrNull()?.message}"
                    )
                    OnlineCacheMetadata.purgeSnapshot(existingSnapshot)
                    openResult = openSession()
                }
            }
            if (openResult.isFailure) {
                val errorMessage = openResult.exceptionOrNull()?.message ?: "unknown"
                safeLogE("openSession failed: key=$resourceKey, error=$errorMessage")
                onCacheFailure("打开缓存会话失败: $errorMessage")
                return IPlaysource.AudioSourceCode.ASC_IO_EXCEPTION
            }

            val openedSession = openResult.getOrNull()
            session = openedSession
            opened = true

            val snapshot = CacheCore.lookup(resourceKey).getOrNull()
            if (snapshot != null) {
                cacheProgressTracker.seedCompletedRanges(snapshot.completedRanges)
                if (extraMetadata.isNotEmpty()) {
                    OnlineCacheMetadata.persist(snapshot.extraFilePath, extraMetadata)
                }
            }
            (openedSession as? CacheProgressChunkEmitter)?.setCacheProgressChunkListener(::onCacheProgressChunk)
            safeLogI("open success: key=$resourceKey, cachedLengthHint=$cachedLengthHint")
            emitCacheProgress(force = true)
            return IPlaysource.AudioSourceCode.ASC_SUCCESS
        }
    }

    override fun stop() {
        synchronized(this) {
            if (!aborted) {
                session?.cancelPendingRead()
            }
        }
    }

    override fun abort() {
        synchronized(this) {
            if (aborted || closed) {
                return
            }
            aborted = true
            safeLogI("abort: key=$resourceKey")
            close()
        }
    }

    override fun close() {
        synchronized(this) {
            if (closed) {
                return
            }
            closed = true
            safeLogI(
                "close: key=$resourceKey, seekCallbacks=$seekCallbackCount, seekFailures=$seekFailureCount"
            )
            opened = false
            (session as? CacheProgressChunkEmitter)?.setCacheProgressChunkListener(null)
            session?.close()
            session = null
            provider.close()
        }
    }

    override fun size(): Long {
        return refreshContentLength().coerceAtLeast(0L)
    }

    override fun cacheSize(): Long = 0L

    override fun supportFastSeek(): Boolean = sourceMode == IPlaysource.SourceMode.NORMAL

    override fun read(buffer: ByteArray, size: Int): Int {
        while (true) {
            val currentSession: CacheSession
            val readOffset: Long
            val maxRead: Int
            val readSeekSerial: Long
            synchronized(this) {
                if (!opened) {
                    val openCode = open()
                    if (openCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
                        return -1
                    }
                }
                if (aborted || size <= 0 || buffer.isEmpty()) {
                    return 0
                }
                maxRead = size.coerceAtMost(buffer.size)
                currentSession = session ?: return -1
                readOffset = position
                readSeekSerial = seekSerial
                if (contentLength > 0L && readOffset >= contentLength) {
                    refreshContentLength()
                }
            }
            val result = currentSession.readAt(readOffset, maxRead)
            if (result.isFailure) {
                val retry = synchronized(this) {
                    !aborted && session === currentSession && seekSerial != readSeekSerial
                }
                if (retry) {
                    continue
                }
                safeLogE(
                    "readAt failed: key=$resourceKey, pos=$readOffset, size=$maxRead, error=${result.exceptionOrNull()?.message}"
                )
                onCacheFailure(
                    "读取缓存数据失败: ${result.exceptionOrNull()?.message ?: "unknown"}"
                )
                return -1
            }

            val bytes = result.getOrThrow()
            val copySize = bytes.size.coerceAtMost(maxRead)
            if (copySize <= 0) {
                val retry = synchronized(this) {
                    !aborted && session === currentSession && seekSerial != readSeekSerial
                }
                if (retry) {
                    continue
                }
                val resolvedLength = refreshContentLength()
                val beforeKnownEof = resolvedLength <= 0L || readOffset < resolvedLength
                if (beforeKnownEof) {
                    safeLogE(
                        "read empty before eof: key=$resourceKey, offset=$readOffset, request=$maxRead, contentLength=$resolvedLength"
                    )
                    onCacheFailure("读取缓存数据为空: offset=$readOffset")
                    return -1
                }
                safeLogI("read empty: key=$resourceKey, offset=$readOffset, request=$maxRead")
                return 0
            }

            refreshContentLength()

            var nextOffset = readOffset
            val accepted = synchronized(this) {
                if (aborted || session !== currentSession) {
                    false
                } else if (seekSerial != readSeekSerial || position != readOffset) {
                    false
                } else {
                    bytes.copyInto(buffer, destinationOffset = 0, startIndex = 0, endIndex = copySize)
                    position += copySize
                    nextOffset = position
                    true
                }
            }
            if (!accepted) {
                continue
            }

            emitCacheProgress()
            return copySize
        }
    }

    override fun readDirect(buffer: ByteBuffer, size: Int): Int {
        if (size <= 0 || !buffer.hasRemaining()) {
            return 0
        }
        while (true) {
            val currentSession: CacheSession
            val readOffset: Long
            val maxRead: Int
            val readSeekSerial: Long
            synchronized(this) {
                if (!opened) {
                    val openCode = open()
                    if (openCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
                        return -1
                    }
                }
                if (aborted) {
                    return 0
                }
                maxRead = size.coerceAtMost(buffer.remaining())
                currentSession = session ?: return -1
                readOffset = position
                readSeekSerial = seekSerial
                if (contentLength > 0L && readOffset >= contentLength) {
                    refreshContentLength()
                }
            }

            val target = buffer.slice()
            target.limit(maxRead)
            val result = currentSession.readAtDirect(readOffset, target, maxRead)
            if (result.isFailure) {
                val retry = synchronized(this) {
                    !aborted && session === currentSession && seekSerial != readSeekSerial
                }
                if (retry) {
                    continue
                }
                onCacheFailure(
                    "读取缓存数据失败: ${result.exceptionOrNull()?.message ?: "unknown"}"
                )
                return -1
            }

            val read = result.getOrThrow().coerceAtMost(maxRead)
            if (read <= 0) {
                val retry = synchronized(this) {
                    !aborted && session === currentSession && seekSerial != readSeekSerial
                }
                if (retry) {
                    continue
                }
                val resolvedLength = refreshContentLength()
                if (resolvedLength <= 0L || readOffset < resolvedLength) {
                    onCacheFailure("读取缓存数据为空: offset=$readOffset")
                    return -1
                }
                return 0
            }

            refreshContentLength()
            val accepted = synchronized(this) {
                if (aborted || session !== currentSession ||
                    seekSerial != readSeekSerial || position != readOffset
                ) {
                    false
                } else {
                    position += read
                    buffer.position(buffer.position() + read)
                    true
                }
            }
            if (!accepted) {
                continue
            }
            emitCacheProgress()
            return read
        }
    }

    override fun seek(offset: Long, whence: Int): Long {
        synchronized(this) {
            if (aborted) {
                return -1L
            }
            seekCallbackCount += 1
            if ((whence and IPlaysource.SEEK_SIZE) != 0) {
                return size()
            }
            val base = when (whence and 0x3) {
                IPlaysource.SEEK_SET -> 0L
                IPlaysource.SEEK_CUR -> position
                IPlaysource.SEEK_END -> size()
                else -> {
                    seekFailureCount += 1
                    return -1L
                }
            }
            val target = (base + offset).coerceAtLeast(0L)
            val bounded = if (size() > 0L) target.coerceAtMost(size()) else target
            val currentSession = session ?: run {
                seekFailureCount += 1
                return -1L
            }
            seekSerial += 1
            val seekResult = currentSession.seek(bounded, IPlaysource.SEEK_SET)
            if (seekResult.isFailure) {
                seekFailureCount += 1
                safeLogE(
                    "seek failed: key=$resourceKey, target=$bounded, error=${seekResult.exceptionOrNull()?.message}"
                )
                return -1L
            }
            val newOffset = seekResult.getOrThrow()
            position = newOffset
            return newOffset
        }
    }

    override fun onPlaybackSeekPositionChanged(positionMs: Long, durationMs: Long) {
        if (positionMs < 0L || durationMs <= 0L) {
            return
        }
        val totalBytes = synchronized(this) {
            contentLength.takeIf { it > 0L } ?: contentLengthHint?.takeIf { it > 0L }
        } ?: return
        if (totalBytes <= 0L) {
            return
        }
        val anchorOffset = if (totalBytes == 1L) {
            0L
        } else {
            ((totalBytes.toDouble() * (positionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0))
                .toLong()
                .coerceIn(0L, totalBytes - 1L))
        }
        synchronized(this) {
            cacheProgressTracker.onPlaybackSeekAccepted(anchorOffset)
        }
        emitCacheProgress(force = true)
    }

    private fun safeLogI(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun safeLogE(message: String) {
        runCatching { Log.e(TAG, message) }
    }

    private fun refreshContentLength(): Long {
        val resolved = provider.queryContentLength()
        if (resolved != null && resolved > 0L && (contentLength <= 0L || resolved > contentLength)) {
            contentLength = resolved
        }
        return contentLength
    }

    private fun onCacheProgressChunk(chunk: CacheProgressChunk) {
        synchronized(this) {
            cacheProgressTracker.onCacheProgressChunk(chunk)
        }
        emitCacheProgress()
    }

    private fun emitCacheProgress(force: Boolean = false) {
        val listener = cacheProgressListener ?: return
        val emission = synchronized(this) {
            val totalBytes = contentLength.takeIf { it > 0L } ?: contentLengthHint?.takeIf { it > 0L }
            val resolved = cacheProgressTracker.snapshot(totalBytes) ?: return
            val previous = lastEmittedCacheProgress
            if (!force && previous != null && !resolved.isFullyCached) {
                val minimumByteDelta = totalBytes
                    ?.let { total -> (total / 100L).coerceIn(1L, MAX_PROGRESS_EMIT_DELTA_BYTES) }
                    ?: MAX_PROGRESS_EMIT_DELTA_BYTES
                val cachedByteDelta = kotlin.math.abs(resolved.cachedBytes - previous.cachedBytes)
                if (cachedByteDelta < minimumByteDelta) {
                    return
                }
            }
            if (resolved == previous) {
                return
            }
            lastEmittedCacheProgress = resolved
            val progressBucket = (resolved.normalizedDisplayRatio * PROGRESS_LOG_BUCKET_COUNT)
                .toInt()
                .coerceIn(0, PROGRESS_LOG_BUCKET_COUNT)
            val shouldLog = resolved.isFullyCached || progressBucket > lastLoggedCacheProgressBucket
            if (shouldLog) {
                lastLoggedCacheProgressBucket = progressBucket
            }
            Triple(cacheProgressTracker.observedRanges(), resolved, shouldLog)
        }
        val (observedRanges, resolved, shouldLog) = emission
        if (shouldLog) {
            safeLogI(
                "cacheProgress: key=$resourceKey, progress=${describeCacheProgress(resolved)}, observed=${describeObservedRanges(observedRanges)}"
            )
        }
        listener(resolved)
    }

    private companion object {
        private const val TAG = "CachedNetworkSource"
        private const val MAX_PROGRESS_EMIT_DELTA_BYTES = 256L * 1024L
        private const val PROGRESS_LOG_BUCKET_COUNT = 10

        private fun describeCacheProgress(snapshot: PlaybackCacheProgressSnapshot?): String {
            if (snapshot == null) {
                return "<null>"
            }
            return "cached=${snapshot.cachedBytes},total=${snapshot.totalBytes ?: -1L},ratio=${snapshot.normalizedDisplayRatio},full=${snapshot.isFullyCached},estimated=${snapshot.isEstimated}"
        }

        private fun describeObservedRanges(ranges: List<LongRange>): String {
            if (ranges.isEmpty()) {
                return "[]"
            }
            return ranges.joinToString(prefix = "[", postfix = "]") { "${it.first}-${it.last + 1L}" }
        }
    }
}

internal class ObservedCacheProgressTracker {
    private val observedCompletedRanges = mutableListOf<LongRange>()
    private var displayAnchorOffset: Long? = null

    fun seedCompletedRanges(ranges: List<CacheCompletedRange>) {
        ranges.forEach { range ->
            recordObservedCompletedRange(range.start, range.endExclusive)
        }
    }

    fun onCacheProgressChunk(chunk: CacheProgressChunk) {
        if (chunk.offset < 0L || chunk.length <= 0) {
            return
        }
        recordObservedCompletedRange(chunk.offset, chunk.offset + chunk.length.toLong())
    }

    fun onPlaybackSeekAccepted(offset: Long) {
        displayAnchorOffset = offset.coerceAtLeast(0L)
    }

    fun snapshot(totalBytesHint: Long?): PlaybackCacheProgressSnapshot? {
        val completedRanges = observedCompletedRanges.mapNotNull { range ->
            val endExclusive = range.last + 1L
            if (endExclusive <= range.first) {
                null
            } else {
                CacheCompletedRange(start = range.first, endExclusive = endExclusive)
            }
        }
        val totalBytes = totalBytesHint?.takeIf { it > 0L }
        val headCachedBytes = coveredBytesFromStart(completedRanges)
        val totalCachedBytes = coveredBytesTotal(completedRanges)
        val displayCoverage = coverageAtOffset(
            ranges = completedRanges,
            offset = displayAnchorOffset,
            totalBytes = totalBytes
        )
        val displayEndBytes = maxOf(
            headCachedBytes,
            displayCoverage?.displayEndBytes ?: 0L
        ).let { endBytes ->
            if (totalBytes != null) endBytes.coerceAtMost(totalBytes) else endBytes
        }
        val displayStartBytes = if (
            displayCoverage != null &&
            displayCoverage.displayEndBytes >= headCachedBytes &&
            displayCoverage.displayEndBytes > 0L
        ) {
            displayCoverage.displayStartBytes
        } else {
            0L
        }.let { startBytes ->
            if (totalBytes != null) startBytes.coerceIn(0L, totalBytes) else startBytes.coerceAtLeast(0L)
        }
        val cachedBytes = maxOf(
            totalCachedBytes,
            displayCoverage?.cachedBytes ?: 0L
        ).let { bytes ->
            if (totalBytes != null) bytes.coerceAtMost(totalBytes) else bytes
        }
        val isFullyCached = totalBytes != null && headCachedBytes >= totalBytes
        if (cachedBytes <= 0L && displayEndBytes <= 0L && !isFullyCached) {
            return null
        }
        val displayRatio = when {
            isFullyCached -> 1f
            totalBytes != null && totalBytes > 0L && displayEndBytes > 0L -> {
                (displayEndBytes.toDouble() / totalBytes.toDouble()).toFloat()
            }
            totalBytes != null && totalBytes > 0L && cachedBytes > 0L -> {
                (cachedBytes.toDouble() / totalBytes.toDouble()).toFloat()
            }
            cachedBytes > 0L -> OBSERVED_UNKNOWN_TOTAL_MIN_DISPLAY_RATIO
            else -> 0f
        }.coerceIn(0f, 1f)
        val displayStartRatio = when {
            isFullyCached -> 0f
            totalBytes != null && totalBytes > 0L && displayEndBytes > 0L -> {
                (displayStartBytes.toDouble() / totalBytes.toDouble()).toFloat()
            }
            else -> 0f
        }.coerceIn(0f, displayRatio)
        return PlaybackCacheProgressSnapshot(
            cachedBytes = cachedBytes.coerceAtLeast(0L),
            totalBytes = totalBytes,
            displayStartRatio = displayStartRatio,
            displayRatio = displayRatio,
            isFullyCached = isFullyCached,
            isEstimated = true
        )
    }

    fun observedRanges(): List<LongRange> = observedCompletedRanges.toList()

    private fun recordObservedCompletedRange(start: Long, endExclusive: Long) {
        if (endExclusive <= start || start < 0L) {
            return
        }
        observedCompletedRanges += start until endExclusive
        if (observedCompletedRanges.size <= 1) {
            return
        }
        val merged = observedCompletedRanges
            .filter { it.last >= it.first }
            .sortedBy { it.first }
            .fold(mutableListOf<LongRange>()) { ranges, range ->
                val last = ranges.lastOrNull()
                if (last == null || range.first > last.last + 1L) {
                    ranges += range
                } else if (range.last > last.last) {
                    ranges[ranges.lastIndex] = last.first..range.last
                }
                ranges
            }
        observedCompletedRanges.clear()
        observedCompletedRanges += merged
    }

    private fun coveredBytesFromStart(ranges: List<CacheCompletedRange>): Long {
        if (ranges.isEmpty()) {
            return 0L
        }
        val sortedRanges = ranges
            .filter { it.endExclusive > it.start }
            .sortedBy { it.start }
        if (sortedRanges.isEmpty() || sortedRanges.first().start > 0L) {
            return 0L
        }
        var coveredEnd = sortedRanges.first().endExclusive
        for (index in 1 until sortedRanges.size) {
            val range = sortedRanges[index]
            if (range.start > coveredEnd) {
                break
            }
            if (range.endExclusive > coveredEnd) {
                coveredEnd = range.endExclusive
            }
        }
        return coveredEnd.coerceAtLeast(0L)
    }

    private fun coveredBytesTotal(ranges: List<CacheCompletedRange>): Long {
        return ranges
            .filter { it.endExclusive > it.start }
            .sumOf { (it.endExclusive - it.start).coerceAtLeast(0L) }
    }

    private fun coverageAtOffset(
        ranges: List<CacheCompletedRange>,
        offset: Long?,
        totalBytes: Long?
    ): PlaybackBufferedCoverage? {
        val normalizedOffset = offset ?: return null
        val normalizedRanges = ranges
            .filter { it.endExclusive > it.start }
            .sortedBy { it.start }
        if (normalizedRanges.isEmpty()) {
            return null
        }
        var mergedStart = normalizedRanges.first().start
        var mergedEnd = normalizedRanges.first().endExclusive
        for (index in 1..normalizedRanges.size) {
            val range = normalizedRanges.getOrNull(index)
            if (range != null && range.start <= mergedEnd) {
                if (range.endExclusive > mergedEnd) {
                    mergedEnd = range.endExclusive
                }
                continue
            }
            if (normalizedOffset >= mergedStart && normalizedOffset < mergedEnd) {
                return coverageFromRange(
                    start = mergedStart,
                    endExclusive = mergedEnd,
                    totalBytes = totalBytes
                )
            }
            if (range != null) {
                mergedStart = range.start
                mergedEnd = range.endExclusive
            }
        }
        return null
    }

    private fun coverageFromRange(
        start: Long,
        endExclusive: Long,
        totalBytes: Long?
    ): PlaybackBufferedCoverage {
        val clippedStart = start.coerceAtLeast(0L)
        val clippedEnd = totalBytes
            ?.takeIf { it > 0L }
            ?.let { endExclusive.coerceIn(0L, it) }
            ?: endExclusive.coerceAtLeast(0L)
        return PlaybackBufferedCoverage(
            cachedBytes = (clippedEnd - clippedStart).coerceAtLeast(0L),
            displayStartBytes = clippedStart,
            displayEndBytes = clippedEnd
        )
    }

    private data class PlaybackBufferedCoverage(
        val cachedBytes: Long,
        val displayStartBytes: Long,
        val displayEndBytes: Long
    )

    private companion object {
        private const val OBSERVED_UNKNOWN_TOTAL_MIN_DISPLAY_RATIO = 0.04f
    }
}
