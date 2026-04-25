package com.wxy.playerlite.playback.process

import android.util.Log
import com.wxy.playerlite.cache.core.CacheLookupSnapshot
import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.CacheCompletedRange
import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot
import java.io.File

internal fun resolveInitialPlaybackCacheProgressSnapshot(
    resourceKey: String?,
    totalBytesHint: Long?,
    durationMs: Long
): PlaybackCacheProgressSnapshot? {
    val normalizedResourceKey = resourceKey?.takeIf { it.isNotBlank() } ?: return null
    return resolvePlaybackCacheProgressSnapshot(
        snapshot = CacheCore.lookup(normalizedResourceKey).getOrNull(),
        totalBytesHint = totalBytesHint,
        playbackPositionMs = 0L,
        durationMs = durationMs
    )
}

internal fun stabilizePlaybackCacheProgressSnapshot(
    previous: PlaybackCacheProgressSnapshot?,
    resolved: PlaybackCacheProgressSnapshot?,
    resourceKey: String?
): PlaybackCacheProgressSnapshot? {
    if (resolved != null || previous == null) {
        return resolved
    }
    return previous.takeIf { !resourceKey.isNullOrBlank() }
}

internal fun resolvePlaybackCacheProgressSnapshot(
    snapshot: CacheLookupSnapshot?,
    totalBytesHint: Long?,
    playbackPositionMs: Long,
    durationMs: Long,
    resourceKey: String? = null,
    cacheRootDirPath: String? = null
): PlaybackCacheProgressSnapshot? {
    val effectiveSnapshot = (snapshot
        ?: readCacheSidecarSnapshot(
            resourceKey = resourceKey,
            cacheRootDirPath = cacheRootDirPath
        ))?.withConfigSidecarMetadata()
    if (effectiveSnapshot == null && (totalBytesHint == null || totalBytesHint <= 0L)) {
        logCacheProgressResolver(
            "resolver result: null reason=no_snapshot_and_no_hint pos=$playbackPositionMs dur=$durationMs hint=${totalBytesHint ?: -1L}"
        )
        return null
    }
    val resolvedTotalBytes = effectiveSnapshot?.contentLength
        ?.takeIf { it > 0L }
        ?: totalBytesHint?.takeIf { it > 0L }
    val completedRangeBytes = coveredBytesFromStart(effectiveSnapshot?.completedRanges.orEmpty())
    val contiguousBlockBytes = coveredBytesFromStart(
        blocks = effectiveSnapshot?.cachedBlocks.orEmpty(),
        blockSizeBytes = effectiveSnapshot?.blockSizeBytes ?: 0,
        totalBytes = resolvedTotalBytes
    )
    val playbackByteOffset = playbackByteOffset(
        playbackPositionMs = playbackPositionMs,
        durationMs = durationMs,
        totalBytes = resolvedTotalBytes
    )
    val rangeCoverage = coverageAtPlaybackPosition(
        ranges = effectiveSnapshot?.completedRanges.orEmpty(),
        playbackByteOffset = playbackByteOffset,
        totalBytes = resolvedTotalBytes
    )
    val blockCoverage = coverageAtPlaybackPosition(
        blocks = effectiveSnapshot?.cachedBlocks.orEmpty(),
        blockSizeBytes = effectiveSnapshot?.blockSizeBytes ?: 0,
        playbackByteOffset = playbackByteOffset,
        totalBytes = resolvedTotalBytes
    )
    val fileSizeBytes = effectiveSnapshot?.dataFileSizeBytes?.coerceAtLeast(0L) ?: 0L
    val cachedBytes = maxOf(
        completedRangeBytes,
        contiguousBlockBytes,
        rangeCoverage?.cachedBytes ?: 0L,
        blockCoverage?.cachedBytes ?: 0L
    ).let { coveredBytes ->
        if (resolvedTotalBytes != null) {
            coveredBytes.coerceAtMost(resolvedTotalBytes)
        } else {
            coveredBytes
        }
    }
    val bufferedEndBytes = maxOf(
        completedRangeBytes,
        contiguousBlockBytes,
        rangeCoverage?.displayEndBytes ?: 0L,
        blockCoverage?.displayEndBytes ?: 0L
    ).let { endBytes ->
        if (resolvedTotalBytes != null) {
            endBytes.coerceAtMost(resolvedTotalBytes)
        } else {
            endBytes
        }
    }
    val bufferedStartBytes = when (bufferedEndBytes) {
        rangeCoverage?.displayEndBytes -> rangeCoverage.displayStartBytes
        blockCoverage?.displayEndBytes -> blockCoverage.displayStartBytes
        else -> 0L
    }.let { startBytes ->
        if (resolvedTotalBytes != null) {
            startBytes.coerceIn(0L, resolvedTotalBytes)
        } else {
            startBytes.coerceAtLeast(0L)
        }
    }
    val playedRatio = if (durationMs > 0L && playbackPositionMs > 0L) {
        (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val isFullyCached = resolvedTotalBytes != null &&
        maxOf(completedRangeBytes, contiguousBlockBytes) >= resolvedTotalBytes
    val displayRatio = when {
        isFullyCached -> 1f
        resolvedTotalBytes != null && resolvedTotalBytes > 0L && bufferedEndBytes > 0L -> {
            (bufferedEndBytes.toDouble() / resolvedTotalBytes.toDouble()).toFloat()
        }

        resolvedTotalBytes != null && resolvedTotalBytes > 0L -> {
            (cachedBytes.toDouble() / resolvedTotalBytes.toDouble()).toFloat()
        }

        cachedBytes > 0L -> UNKNOWN_TOTAL_MIN_DISPLAY_RATIO
        else -> 0f
    }.coerceIn(0f, 1f)
    val displayStartRatio = when {
        isFullyCached -> 0f
        resolvedTotalBytes != null && resolvedTotalBytes > 0L && bufferedEndBytes > 0L -> {
            (bufferedStartBytes.toDouble() / resolvedTotalBytes.toDouble()).toFloat()
        }

        else -> 0f
    }.coerceIn(0f, displayRatio)
    if (cachedBytes <= 0L && bufferedEndBytes <= 0L && !isFullyCached) {
        logCacheProgressResolver(
            "resolver result: null reason=no_cached_bytes key=${effectiveSnapshot?.resourceKey ?: "<none>"} total=${resolvedTotalBytes ?: -1L} fileBytes=$fileSizeBytes blockBytes=$contiguousBlockBytes playbackByte=${playbackByteOffset ?: -1L} blocks=${describeBlocks(effectiveSnapshot)} ranges=${describeRanges(effectiveSnapshot)}"
        )
        return null
    }
    val resolved = PlaybackCacheProgressSnapshot(
        cachedBytes = cachedBytes.coerceAtLeast(0L),
        totalBytes = resolvedTotalBytes,
        displayStartRatio = displayStartRatio,
        displayRatio = displayRatio,
        isFullyCached = isFullyCached,
        isEstimated = snapshot?.contentLength?.takeIf { it > 0L } == null
    )
    logCacheProgressResolver(
        buildString {
            append("resolver result: key=")
            append(effectiveSnapshot?.resourceKey ?: "<none>")
            append(", total=")
            append(resolvedTotalBytes ?: -1L)
            append(", fileBytes=")
            append(fileSizeBytes)
            append(", completedBytes=")
            append(completedRangeBytes)
            append(", blockBytes=")
            append(contiguousBlockBytes)
            append(", cachedBytes=")
            append(cachedBytes)
            append(", bufferedEndBytes=")
            append(bufferedEndBytes)
            append(", bufferedStartBytes=")
            append(bufferedStartBytes)
            append(", playbackByte=")
            append(playbackByteOffset ?: -1L)
            append(", blocks=")
            append(describeBlocks(effectiveSnapshot))
            append(", ranges=")
            append(describeRanges(effectiveSnapshot))
            append(", playedRatio=")
            append(playedRatio)
            append(", displayRatio=")
            append(resolved.normalizedDisplayRatio)
            append(", full=")
            append(isFullyCached)
            append(", estimated=")
            append(resolved.isEstimated)
        }
    )
    return resolved
}

private const val UNKNOWN_TOTAL_MIN_DISPLAY_RATIO = 0.04f
private const val TAG = "CacheProgressResolver"

private data class PlaybackBufferedCoverage(
    val cachedBytes: Long,
    val displayStartBytes: Long,
    val displayEndBytes: Long
)

private fun logCacheProgressResolver(message: String) {
    runCatching { Log.d(TAG, message) }
}

private fun describeRanges(snapshot: CacheLookupSnapshot?): String {
    return snapshot?.completedRanges
        ?.joinToString(prefix = "[", postfix = "]") { "${it.start}-${it.endExclusive}" }
        ?: "[]"
}

private fun coveredBytesFromStart(
    ranges: List<com.wxy.playerlite.cache.core.CacheCompletedRange>
): Long {
    if (ranges.isEmpty()) {
        return 0L
    }
    val mergedRanges = ranges
        .filter { it.endExclusive > it.start }
        .sortedBy { it.start }
    if (mergedRanges.isEmpty() || mergedRanges.first().start > 0L) {
        return 0L
    }
    var coveredEnd = mergedRanges.first().endExclusive
    for (index in 1 until mergedRanges.size) {
        val range = mergedRanges[index]
        if (range.start > coveredEnd) {
            break
        }
        if (range.endExclusive > coveredEnd) {
            coveredEnd = range.endExclusive
        }
    }
    return coveredEnd.coerceAtLeast(0L)
}

private fun coveredBytesFromStart(
    blocks: Set<Long>,
    blockSizeBytes: Int,
    totalBytes: Long?
): Long {
    if (blocks.isEmpty() || blockSizeBytes <= 0) {
        return 0L
    }
    val sortedBlocks = blocks
        .filter { it >= 0L }
        .sorted()
    if (sortedBlocks.isEmpty() || sortedBlocks.first() != 0L) {
        return 0L
    }
    var nextExpectedBlock = 0L
    for (block in sortedBlocks) {
        if (block != nextExpectedBlock) {
            break
        }
        nextExpectedBlock++
    }
    val coveredBytes = nextExpectedBlock * blockSizeBytes.toLong()
    return totalBytes
        ?.takeIf { it > 0L }
        ?.let { coveredBytes.coerceAtMost(it) }
        ?: coveredBytes
}

private fun playbackByteOffset(
    playbackPositionMs: Long,
    durationMs: Long,
    totalBytes: Long?
): Long? {
    val normalizedTotalBytes = totalBytes?.takeIf { it > 0L } ?: return null
    if (durationMs <= 0L || playbackPositionMs < 0L) {
        return null
    }
    if (normalizedTotalBytes == 1L) {
        return 0L
    }
    val ratio = (playbackPositionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0)
    return (normalizedTotalBytes.toDouble() * ratio)
        .toLong()
        .coerceIn(0L, normalizedTotalBytes - 1L)
}

private fun coverageAtPlaybackPosition(
    ranges: List<CacheCompletedRange>,
    playbackByteOffset: Long?,
    totalBytes: Long?
): PlaybackBufferedCoverage? {
    val offset = playbackByteOffset ?: return null
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
        if (offset >= mergedStart && offset < mergedEnd) {
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

private fun coverageAtPlaybackPosition(
    blocks: Set<Long>,
    blockSizeBytes: Int,
    playbackByteOffset: Long?,
    totalBytes: Long?
): PlaybackBufferedCoverage? {
    val offset = playbackByteOffset ?: return null
    if (blocks.isEmpty() || blockSizeBytes <= 0) {
        return null
    }
    val sortedBlocks = blocks
        .filter { it >= 0L }
        .sorted()
    if (sortedBlocks.isEmpty()) {
        return null
    }
    var groupStartBlock = sortedBlocks.first()
    var previousBlock = sortedBlocks.first()
    for (index in 1..sortedBlocks.size) {
        val block = sortedBlocks.getOrNull(index)
        if (block != null && block == previousBlock + 1L) {
            previousBlock = block
            continue
        }
        val start = groupStartBlock * blockSizeBytes.toLong()
        val endExclusive = (previousBlock + 1L) * blockSizeBytes.toLong()
        if (offset >= start && offset < endExclusive) {
            return coverageFromRange(
                start = start,
                endExclusive = endExclusive,
                totalBytes = totalBytes
            )
        }
        if (block != null) {
            groupStartBlock = block
            previousBlock = block
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

private fun describeBlocks(snapshot: CacheLookupSnapshot?): String {
    val blocks = snapshot?.cachedBlocks.orEmpty()
    if (blocks.isEmpty()) {
        return "[]"
    }
    val sorted = blocks.sorted()
    val first = sorted.take(8).joinToString()
    return if (sorted.size <= 8) {
        "[$first]"
    } else {
        "[$first, ... size=${sorted.size}]"
    }
}

private fun CacheLookupSnapshot.withConfigSidecarMetadata(): CacheLookupSnapshot {
    if (contentLength > 0L || cachedBlocks.isNotEmpty() || completedRanges.isNotEmpty()) {
        return this
    }
    val rawConfigPath = configFilePath.takeIf { it.isNotBlank() } ?: run {
        logCacheProgressResolver(
            "resolver sidecar skipped: key=$resourceKey reason=blank_config_path"
        )
        return this
    }
    val configFile = File(rawConfigPath)
    if (!configFile.exists() || !configFile.isFile) {
        logCacheProgressResolver(
            "resolver sidecar skipped: key=$resourceKey reason=config_missing path=$rawConfigPath"
        )
        return this
    }
    val parsed = runCatching {
        val payload = configFile.readText()
        val parsedContentLength = payload.parseLongField("contentLength") ?: -1L
        val parsedDurationMs = payload.parseLongField("durationMs") ?: -1L
        val parsedBlockSizeBytes = payload.parseLongField("blockSizeBytes")?.toInt() ?: blockSizeBytes
        val parsedBlocks = payload.parseLongArrayField("blocks")
        val parsedRanges = payload.parseRangesField("completedRanges")
        val parsedRangesDescription = parsedRanges.joinToString(prefix = "[", postfix = "]") {
            "${it.start}-${it.endExclusive}"
        }
        val enriched = copy(
            blockSizeBytes = parsedBlockSizeBytes.takeIf { it > 0 } ?: blockSizeBytes,
            contentLength = parsedContentLength.takeIf { it > 0L } ?: contentLength,
            durationMs = parsedDurationMs.takeIf { it > 0L } ?: durationMs,
            cachedBlocks = if (parsedBlocks.isNotEmpty()) parsedBlocks else cachedBlocks,
            completedRanges = if (parsedRanges.isNotEmpty()) parsedRanges else completedRanges
        )
        logCacheProgressResolver(
            "resolver sidecar parsed: key=$resourceKey path=$rawConfigPath parsedContentLength=$parsedContentLength parsedDuration=$parsedDurationMs parsedBlockSize=$parsedBlockSizeBytes parsedBlocks=${parsedBlocks.sorted()} parsedRanges=$parsedRangesDescription"
        )
        enriched
    }.getOrElse { error ->
        logCacheProgressResolver(
            "resolver sidecar failed: key=$resourceKey path=$rawConfigPath error=${error.message ?: error::class.java.simpleName}"
        )
        this
    }
    if (parsed === this) {
        logCacheProgressResolver(
            "resolver sidecar no-op: key=$resourceKey path=$rawConfigPath contentLength=$contentLength blocks=${describeBlocks(this)} ranges=${describeRanges(this)}"
        )
    } else {
        logCacheProgressResolver(
            "resolver sidecar applied: key=$resourceKey path=$rawConfigPath contentLength=${parsed.contentLength} blocks=${describeBlocks(parsed)} ranges=${describeRanges(parsed)}"
        )
    }
    return parsed
}

private fun readCacheSidecarSnapshot(
    resourceKey: String?,
    cacheRootDirPath: String?
): CacheLookupSnapshot? {
    val normalizedResourceKey = resourceKey?.takeIf { it.isNotBlank() } ?: return null
    val rootPath = cacheRootDirPath?.takeIf { it.isNotBlank() } ?: return null
    val root = File(rootPath)
    val configFile = File(root, "${normalizedResourceKey}_config.json")
    val dataFile = File(root, "$normalizedResourceKey.data")
    val extraFile = File(root, "${normalizedResourceKey}_extra.json")
    if (!configFile.isFile || !dataFile.isFile) {
        logCacheProgressResolver(
            "resolver sidecar lookup skipped: key=$normalizedResourceKey reason=missing_file config=${configFile.exists()} data=${dataFile.exists()}"
        )
        return null
    }
    return runCatching {
        CacheLookupSnapshot(
            resourceKey = normalizedResourceKey,
            dataFilePath = dataFile.absolutePath,
            configFilePath = configFile.absolutePath,
            extraFilePath = extraFile.absolutePath,
            dataFileSizeBytes = dataFile.length(),
            blockSizeBytes = 0,
            contentLength = -1L,
            durationMs = -1L,
            cachedBlocks = emptySet(),
            lastAccessEpochMs = configFile.lastModified(),
            completedRanges = emptyList()
        ).withConfigSidecarMetadata()
    }.onSuccess { sidecarSnapshot ->
        logCacheProgressResolver(
            "resolver sidecar lookup loaded: key=$normalizedResourceKey contentLength=${sidecarSnapshot.contentLength} fileBytes=${sidecarSnapshot.dataFileSizeBytes} ranges=${describeRanges(sidecarSnapshot)}"
        )
    }.onFailure { error ->
        logCacheProgressResolver(
            "resolver sidecar lookup failed: key=$normalizedResourceKey error=${error.message ?: error::class.java.simpleName}"
        )
    }.getOrNull()
}

private fun String.parseLongField(field: String): Long? {
    val regex = Regex("\"$field\"\\s*:\\s*(-?\\d+)")
    return regex.find(this)?.groupValues?.get(1)?.toLongOrNull()
}

private fun String.parseLongArrayField(field: String): Set<Long> {
    val bodyPattern = Regex("\"$field\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
    val body = bodyPattern.find(this)?.groupValues?.get(1) ?: return emptySet()
    return body.split(',')
        .mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() }?.toLongOrNull() }
        .filter { it >= 0L }
        .toSet()
}

private fun String.parseRangesField(field: String): List<CacheCompletedRange> {
    val bodyPattern = Regex("\"$field\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
    val body = bodyPattern.find(this)?.groupValues?.get(1) ?: return emptyList()
    val entryPattern = Regex("\\{\\s*\"start\"\\s*:\\s*(-?\\d+)\\s*,\\s*\"end\"\\s*:\\s*(-?\\d+)\\s*\\}")
    return entryPattern.findAll(body)
        .mapNotNull { match ->
            val start = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val end = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            if (start >= 0L && end > start) {
                CacheCompletedRange(start = start, endExclusive = end)
            } else {
                null
            }
        }
        .toList()
}
