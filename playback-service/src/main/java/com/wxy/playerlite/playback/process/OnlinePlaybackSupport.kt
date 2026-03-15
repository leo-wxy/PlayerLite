package com.wxy.playerlite.playback.process

import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.CacheLookupSnapshot
import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.playback.model.PlaybackPreviewClip
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal enum class OnlineClipMode(
    val wireValue: String
) {
    FULL("full"),
    PREVIEW("preview")
}

internal data class OnlinePlaybackCacheKey(
    val songId: String,
    val level: String,
    val clipMode: OnlineClipMode
)

internal data class ResolvedOnlineStream(
    val playbackUrl: String,
    val requestHeaders: Map<String, String>,
    val contentLengthBytes: Long? = null,
    val durationMs: Long? = null,
    val expiresAtMs: Long? = null,
    val previewClip: PlaybackPreviewClip? = null,
    val cacheIdentity: String = playbackUrl
)

internal class ResolvedOnlineUrlMemoryCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    private val entries = object : LinkedHashMap<OnlinePlaybackCacheKey, ResolvedOnlineStream>(
        maxEntries,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<OnlinePlaybackCacheKey, ResolvedOnlineStream>?
        ): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun getIfFresh(
        key: OnlinePlaybackCacheKey,
        nowMs: Long
    ): ResolvedOnlineStream? {
        val value = entries[key] ?: return null
        val expiresAtMs = value.expiresAtMs
        if (expiresAtMs != null && nowMs >= expiresAtMs) {
            entries.remove(key)
            return null
        }
        return value
    }

    @Synchronized
    fun put(
        key: OnlinePlaybackCacheKey,
        value: ResolvedOnlineStream
    ) {
        if (value.previewClip != null) {
            return
        }
        entries[key] = value
    }

    @Synchronized
    fun remove(key: OnlinePlaybackCacheKey) {
        entries.remove(key)
    }

    private companion object {
        private const val DEFAULT_MAX_ENTRIES = 10
    }
}

internal interface OnlinePlaybackRemoteDataSource {
    suspend fun fetchSongUrlV1(
        songIds: String,
        level: String,
        requestHeaders: Map<String, String>,
        unblock: Boolean = false
    ): JsonObject

    suspend fun fetchSongUrl(
        songIds: String,
        bitrate: Int,
        requestHeaders: Map<String, String>
    ): JsonObject

    suspend fun checkMusic(
        songId: String,
        bitrate: Int,
        requestHeaders: Map<String, String>
    ): JsonObject
}

internal class NeteaseOnlinePlaybackRemoteDataSource(
    private val httpClient: JsonHttpClient
) : OnlinePlaybackRemoteDataSource {
    override suspend fun fetchSongUrlV1(
        songIds: String,
        level: String,
        requestHeaders: Map<String, String>,
        unblock: Boolean
    ): JsonObject {
        return httpClient.get(
            path = "/song/url/v1",
            queryParams = mapOf(
                "id" to songIds,
                "level" to level,
                "unblock" to unblock.toString()
            ),
            headers = requestHeaders
        )
    }

    override suspend fun fetchSongUrl(
        songIds: String,
        bitrate: Int,
        requestHeaders: Map<String, String>
    ): JsonObject {
        return httpClient.get(
            path = "/song/url",
            queryParams = mapOf(
                "id" to songIds,
                "br" to bitrate.toString()
            ),
            headers = requestHeaders
        )
    }

    override suspend fun checkMusic(
        songId: String,
        bitrate: Int,
        requestHeaders: Map<String, String>
    ): JsonObject {
        return httpClient.get(
            path = "/check/music",
            queryParams = mapOf(
                "id" to songId,
                "br" to bitrate.toString()
            ),
            headers = requestHeaders
        )
    }
}

internal interface OnlinePlaybackResolver {
    suspend fun resolve(
        songId: String,
        requestHeaders: Map<String, String>,
        requestedLevel: String,
        preferredClipMode: OnlineClipMode = OnlineClipMode.FULL,
        expectedDurationMs: Long = 0L
    ): Result<ResolvedOnlineStream>
}

internal class OnlinePlaybackUrlResolver(
    private val remoteDataSource: OnlinePlaybackRemoteDataSource,
    private val memoryCache: ResolvedOnlineUrlMemoryCache = ResolvedOnlineUrlMemoryCache(),
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : OnlinePlaybackResolver {
    override suspend fun resolve(
        songId: String,
        requestHeaders: Map<String, String>,
        requestedLevel: String,
        preferredClipMode: OnlineClipMode,
        expectedDurationMs: Long
    ): Result<ResolvedOnlineStream> {
        val preparedHeaders = buildPlaybackRequestHeaders(requestHeaders)
        if (preferredClipMode != OnlineClipMode.PREVIEW) {
            val primaryKey = OnlinePlaybackCacheKey(
                songId = songId,
                level = requestedLevel,
                clipMode = preferredClipMode
            )
            memoryCache.getIfFresh(primaryKey, nowMs())
                ?.takeIf { it.previewClip == null }
                ?.let { cached ->
                    val normalizedCached = cached.normalizeForExpectedDuration(expectedDurationMs)
                    if (normalizedCached.previewClip == null) {
                        return Result.success(normalizedCached)
                    }
                    memoryCache.remove(primaryKey)
                }
        }

        val v1Attempt = runCatching {
            remoteDataSource.fetchSongUrlV1(
                songIds = songId,
                level = requestedLevel,
                requestHeaders = preparedHeaders,
                unblock = false
            )
        }.mapCatching { payload ->
            parseSongUrlAttempt(payload = payload, requestHeaders = preparedHeaders, nowMs = nowMs())
        }.getOrNull()

        v1Attempt?.resolved?.let { resolved ->
            val normalized = resolved.normalizeForExpectedDuration(expectedDurationMs)
            cacheResolved(songId = songId, level = requestedLevel, resolved = normalized)
            return Result.success(normalized)
        }

        val legacyAttempt = runCatching {
            remoteDataSource.fetchSongUrl(
                songIds = songId,
                bitrate = DEFAULT_FALLBACK_BITRATE,
                requestHeaders = preparedHeaders
            )
        }.mapCatching { payload ->
            parseSongUrlAttempt(payload = payload, requestHeaders = preparedHeaders, nowMs = nowMs())
        }.getOrNull()

        legacyAttempt?.resolved?.let { resolved ->
            val normalized = resolved.normalizeForExpectedDuration(expectedDurationMs)
            cacheResolved(songId = songId, level = requestedLevel, resolved = normalized)
            return Result.success(normalized)
        }

        val checkMusicMessage = runCatching {
            remoteDataSource.checkMusic(
                songId = songId,
                bitrate = DEFAULT_FALLBACK_BITRATE,
                requestHeaders = preparedHeaders
            )
        }.getOrNull()?.stringValue("message")

        val failureMessage = checkMusicMessage
            ?: legacyAttempt?.message
            ?: v1Attempt?.message
            ?: "Failed to resolve online stream"
        return Result.failure(IllegalStateException(failureMessage))
    }

    private fun cacheResolved(
        songId: String,
        level: String,
        resolved: ResolvedOnlineStream
    ) {
        if (resolved.previewClip != null) {
            return
        }
        memoryCache.put(
            OnlinePlaybackCacheKey(songId = songId, level = level, clipMode = OnlineClipMode.FULL),
            resolved
        )
    }

    private companion object {
        private const val DEFAULT_FALLBACK_BITRATE = 320_000
    }
}

internal data class OnlinePlaybackPlan(
    val resourceKey: String,
    val playbackUrl: String?,
    val requestHeaders: Map<String, String>,
    val durationHintMs: Long,
    val contentLengthHintBytes: Long?,
    val previewClip: PlaybackPreviewClip?,
    val useCacheOnlyProvider: Boolean,
    val cacheExtraMetadata: Map<String, String> = emptyMap()
)

internal class OnlinePlaybackPreparationPlanner(
    private val cacheLookup: (String) -> Result<CacheLookupSnapshot?> = CacheCore::lookup,
    private val resolver: OnlinePlaybackResolver,
    private val defaultLevel: String = DEFAULT_PLAYBACK_LEVEL
) {
    suspend fun buildPlan(track: PlaybackTrack): Result<OnlinePlaybackPlan> {
        val songId = track.songId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalArgumentException("Missing songId for online track"))
        val requestedMode = if (track.previewClip != null) {
            OnlineClipMode.PREVIEW
        } else {
            OnlineClipMode.FULL
        }
        val initialKey = buildOnlineResourceKey(songId, defaultLevel, requestedMode)
        val initialSnapshot = cacheLookup(initialKey)
            .getOrNull()
            ?.sanitizeForReuse(
                expectedClipMode = requestedMode,
                expectedDurationMs = track.durationHintMs
            )
        if (initialSnapshot.isCompleteCachedContent()) {
            return Result.success(
                OnlinePlaybackPlan(
                    resourceKey = initialKey,
                    playbackUrl = track.uri.takeIf { it.isNotBlank() },
                    requestHeaders = track.requestHeaders,
                    durationHintMs = firstPositive(track.durationHintMs, initialSnapshot?.durationMs),
                    contentLengthHintBytes = initialSnapshot?.contentLength?.takeIf { it > 0L },
                    previewClip = track.previewClip,
                    useCacheOnlyProvider = true,
                    cacheExtraMetadata = OnlineCacheMetadata.buildExtraMetadata(requestedMode)
                )
            )
        }

        val resolved = resolver.resolve(
            songId = songId,
            requestHeaders = track.requestHeaders,
            requestedLevel = defaultLevel,
            preferredClipMode = requestedMode,
            expectedDurationMs = track.durationHintMs
        ).getOrElse { return Result.failure(it) }

        val actualMode = if (resolved.previewClip != null) {
            OnlineClipMode.PREVIEW
        } else {
            OnlineClipMode.FULL
        }
        val finalKey = buildOnlineResourceKey(songId, defaultLevel, actualMode)
        val finalSnapshot = if (finalKey != initialKey) {
            cacheLookup(finalKey).getOrNull()
        } else {
            initialSnapshot
        }?.sanitizeForReuse(
            expectedClipMode = actualMode,
            expectedDurationMs = track.durationHintMs
        )
        val completeSnapshot = finalSnapshot.takeIf { it.isCompleteCachedContent() }

        return Result.success(
            OnlinePlaybackPlan(
                resourceKey = finalKey,
                playbackUrl = resolved.playbackUrl,
                requestHeaders = resolved.requestHeaders,
                durationHintMs = firstPositive(track.durationHintMs, resolved.durationMs, completeSnapshot?.durationMs),
                contentLengthHintBytes = completeSnapshot?.contentLength?.takeIf { it > 0L }
                    ?: resolved.contentLengthBytes,
                previewClip = resolved.previewClip ?: track.previewClip,
                useCacheOnlyProvider = completeSnapshot != null,
                cacheExtraMetadata = OnlineCacheMetadata.buildExtraMetadata(actualMode)
            )
        )
    }

    private companion object {
        private const val DEFAULT_PLAYBACK_LEVEL = "exhigh"
    }
}

internal fun buildOnlineResourceKey(
    songId: String,
    level: String,
    clipMode: OnlineClipMode
): String {
    return "song_${songId}_${level}_${clipMode.wireValue}"
}

private data class SongUrlAttempt(
    val resolved: ResolvedOnlineStream?,
    val message: String?
)

private fun parseSongUrlAttempt(
    payload: JsonObject,
    requestHeaders: Map<String, String>,
    nowMs: Long
): SongUrlAttempt {
    val item = payload.arrayValue("data").firstOrNull() as? JsonObject
    val url = item?.stringValue("url")
    val previewClip = item?.objectValue("freeTrialInfo")
        ?.takeIf { it.isNotEmpty() }
        ?.let { freeTrial ->
            val startMs = freeTrial.longValue("start")
            val endMs = freeTrial.longValue("end")
            if (endMs > startMs && endMs > 0L) {
                PlaybackPreviewClip(startMs = startMs, endMs = endMs)
            } else {
                null
            }
        }
    val message = item?.stringValue("message")
        ?: payload.stringValue("message")
    if (url.isNullOrBlank()) {
        return SongUrlAttempt(
            resolved = null,
            message = message
        )
    }

    val expiSeconds = item.intValue("expi")
    return SongUrlAttempt(
        resolved = ResolvedOnlineStream(
            playbackUrl = url,
            requestHeaders = requestHeaders,
            contentLengthBytes = item.longValue("size").takeIf { it > 0L },
            durationMs = item.longValue("time").takeIf { it > 0L },
            expiresAtMs = expiSeconds.takeIf { it > 0 }?.let { nowMs + it * 1000L },
            previewClip = previewClip
        ),
        message = message
    )
}

private fun CacheLookupSnapshot?.isCompleteCachedContent(): Boolean {
    if (this == null) {
        return false
    }
    if (contentLength <= 0L) {
        return false
    }
    val mergedRanges = completedRanges
        .filter { it.endExclusive > it.start }
        .sortedBy { it.start }
    if (mergedRanges.isEmpty()) {
        return false
    }
    if (mergedRanges.first().start > 0L) {
        return false
    }
    var coveredEnd = mergedRanges.first().endExclusive
    for (index in 1 until mergedRanges.size) {
        val range = mergedRanges[index]
        if (range.start > coveredEnd) {
            return false
        }
        if (range.endExclusive > coveredEnd) {
            coveredEnd = range.endExclusive
        }
        if (coveredEnd >= contentLength) {
            return true
        }
    }
    return coveredEnd >= contentLength
}

private fun CacheLookupSnapshot.sanitizeForReuse(
    expectedClipMode: OnlineClipMode,
    expectedDurationMs: Long
): CacheLookupSnapshot? {
    if (!isCompleteCachedContent()) {
        return this
    }
    if (OnlineCacheMetadata.isTrustedForReuse(this, expectedClipMode)) {
        return this
    }
    if (
        expectedClipMode == OnlineClipMode.FULL &&
        expectedDurationMs > 0L &&
        durationMs > 0L &&
        !looksLikeShortRestrictedClip(expectedDurationMs, durationMs)
    ) {
        return this
    }
    OnlineCacheMetadata.purgeSnapshot(this)
    return null
}

private fun firstPositive(vararg values: Long?): Long {
    return values.firstOrNull { it != null && it > 0L } ?: 0L
}

private fun ResolvedOnlineStream.normalizeForExpectedDuration(
    expectedDurationMs: Long
): ResolvedOnlineStream {
    if (previewClip != null) {
        return this
    }
    val actualDurationMs = durationMs ?: 0L
    if (!looksLikeShortRestrictedClip(expectedDurationMs, actualDurationMs)) {
        return this
    }
    return copy(
        previewClip = PlaybackPreviewClip(
            startMs = 0L,
            endMs = actualDurationMs
        )
    )
}

internal fun looksLikeShortRestrictedClip(
    expectedDurationMs: Long,
    actualDurationMs: Long
): Boolean {
    if (expectedDurationMs <= 0L || actualDurationMs <= 0L) {
        return false
    }
    val durationGapMs = expectedDurationMs - actualDurationMs
    if (durationGapMs < 30_000L) {
        return false
    }
    return actualDurationMs * 100L < expectedDurationMs * 80L
}

private fun buildPlaybackRequestHeaders(headers: Map<String, String>): Map<String, String> {
    if (headers.isEmpty()) {
        return emptyMap()
    }
    return buildMap {
        headers.forEach { (key, value) ->
            if (value.isBlank()) {
                return@forEach
            }
            if (key.equals("Cookie", ignoreCase = true)) {
                put("Cookie", ensureCookiePair(value, "os", "pc"))
            } else {
                put(key, value)
            }
        }
    }
}

private fun ensureCookiePair(
    rawCookie: String,
    key: String,
    value: String
): String {
    val pairs = rawCookie.split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toMutableList()
    val exists = pairs.any { segment ->
        segment.substringBefore('=', "").trim().equals(key, ignoreCase = true)
    }
    if (!exists) {
        pairs += "$key=$value"
    }
    return pairs.joinToString(separator = "; ")
}

private fun JsonObject.objectValue(key: String): JsonObject {
    return (this[key] as? JsonObject) ?: emptyJsonObject
}

private fun JsonObject.arrayValue(key: String): JsonArray {
    return this[key]?.jsonArray ?: emptyJsonArray
}

private fun JsonObject.stringValue(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.longValue(key: String): Long {
    return stringValue(key)?.toLongOrNull() ?: 0L
}

private fun JsonObject.intValue(key: String): Int {
    return stringValue(key)?.toIntOrNull() ?: 0
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())
