package com.wxy.playerlite.playback.process

import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.CacheLookupSnapshot
import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackPreviewClip
import com.wxy.playerlite.playback.model.SongAudioQualityCatalog
import com.wxy.playerlite.playback.model.SongAudioQualityCatalogJsonMapper
import com.wxy.playerlite.playback.model.SongAudioQualityOption
import java.io.IOException
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
    val sourceIdentity: String = "",
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

internal enum class OnlinePlaybackFailureKind {
    URL_EXPIRED,
    RESOURCE_UNAVAILABLE,
    UNAUTHORIZED,
    RETRYABLE_NETWORK,
    UNSUPPORTED,
    UNKNOWN
}

internal data class OnlinePlaybackFailure(
    val kind: OnlinePlaybackFailureKind,
    val message: String,
    val cause: Throwable? = null
)

internal class OnlinePlaybackResolutionException(
    val failure: OnlinePlaybackFailure
) : IllegalStateException(failure.message, failure.cause)

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

    @Synchronized
    fun clear() {
        entries.clear()
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

internal interface SongAudioQualityCatalogRemoteDataSource {
    suspend fun fetchSongAudioQualityCatalog(
        songId: String,
        requestHeaders: Map<String, String>
    ): JsonObject
}

internal class NeteaseOnlinePlaybackRemoteDataSource(
    private val baseUrlProvider: () -> String,
    private val httpGet: suspend (
        baseUrl: String,
        path: String,
        queryParams: Map<String, String>,
        headers: Map<String, String>
    ) -> JsonObject = { baseUrl, path, queryParams, headers ->
        JsonHttpClient(baseUrl = baseUrl).get(
            path = path,
            queryParams = queryParams,
            headers = headers
        )
    }
) : OnlinePlaybackRemoteDataSource {
    override suspend fun fetchSongUrlV1(
        songIds: String,
        level: String,
        requestHeaders: Map<String, String>,
        unblock: Boolean
    ): JsonObject {
        return httpGet(
            baseUrlProvider(),
            "/song/url/v1",
            mapOf(
                "id" to songIds,
                "level" to level,
                "unblock" to unblock.toString()
            ),
            requestHeaders
        )
    }

    override suspend fun fetchSongUrl(
        songIds: String,
        bitrate: Int,
        requestHeaders: Map<String, String>
    ): JsonObject {
        return httpGet(
            baseUrlProvider(),
            "/song/url",
            mapOf(
                "id" to songIds,
                "br" to bitrate.toString()
            ),
            requestHeaders
        )
    }

    override suspend fun checkMusic(
        songId: String,
        bitrate: Int,
        requestHeaders: Map<String, String>
    ): JsonObject {
        return httpGet(
            baseUrlProvider(),
            "/check/music",
            mapOf(
                "id" to songId,
                "br" to bitrate.toString()
            ),
            requestHeaders
        )
    }
}

internal class NeteaseSongAudioQualityCatalogRemoteDataSource(
    private val baseUrlProvider: () -> String,
    private val httpGet: suspend (
        baseUrl: String,
        path: String,
        queryParams: Map<String, String>,
        headers: Map<String, String>
    ) -> JsonObject = { baseUrl, path, queryParams, headers ->
        JsonHttpClient(baseUrl = baseUrl).get(
            path = path,
            queryParams = queryParams,
            headers = headers
        )
    }
) : SongAudioQualityCatalogRemoteDataSource {
    override suspend fun fetchSongAudioQualityCatalog(
        songId: String,
        requestHeaders: Map<String, String>
    ): JsonObject {
        return httpGet(
            baseUrlProvider(),
            "/song/music/detail",
            mapOf("id" to songId),
            buildPlaybackRequestHeaders(requestHeaders)
        )
    }
}

internal class CachedSongAudioQualityCatalogProvider(
    private val remoteDataSource: SongAudioQualityCatalogRemoteDataSource,
    private val sourceIdentityProvider: () -> String = { "" },
    maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    private val entries = object : LinkedHashMap<String, SongAudioQualityCatalog>(
        maxEntries,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, SongAudioQualityCatalog>?
        ): Boolean {
            return size > maxEntries
        }
    }

    suspend fun getCatalog(
        songId: String,
        requestHeaders: Map<String, String>
    ): SongAudioQualityCatalog? {
        val normalizedSongId = songId.trim().takeIf { it.isNotEmpty() } ?: return null
        val cacheKey = buildSongAudioQualityCatalogCacheKey(
            songId = normalizedSongId,
            requestHeaders = requestHeaders,
            sourceIdentity = sourceIdentityProvider()
        )
        synchronized(entries) {
            entries[cacheKey]
        }?.let { return it }

        val payload = runCatching {
            remoteDataSource.fetchSongAudioQualityCatalog(
                songId = normalizedSongId,
                requestHeaders = requestHeaders
            )
        }.getOrNull() ?: return null
        val catalog = runCatching {
            SongAudioQualityCatalogJsonMapper.parseCatalog(
                payload = payload,
                songId = normalizedSongId
            )
        }.getOrNull() ?: return null

        synchronized(entries) {
            entries[cacheKey] = catalog
        }
        return catalog
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
        }
    }

    private companion object {
        private const val DEFAULT_MAX_ENTRIES = 24
    }
}

internal interface OnlinePlaybackResolver {
    suspend fun resolve(
        songId: String,
        requestHeaders: Map<String, String>,
        requestedLevel: String,
        fallbackBitrate: Int? = null,
        preferredClipMode: OnlineClipMode = OnlineClipMode.FULL,
        expectedDurationMs: Long = 0L
    ): Result<ResolvedOnlineStream>
}

internal class OnlinePlaybackUrlResolver(
    private val remoteDataSource: OnlinePlaybackRemoteDataSource,
    private val memoryCache: ResolvedOnlineUrlMemoryCache = ResolvedOnlineUrlMemoryCache(),
    private val sourceIdentityProvider: () -> String = { "" },
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : OnlinePlaybackResolver {
    override suspend fun resolve(
        songId: String,
        requestHeaders: Map<String, String>,
        requestedLevel: String,
        fallbackBitrate: Int?,
        preferredClipMode: OnlineClipMode,
        expectedDurationMs: Long
    ): Result<ResolvedOnlineStream> {
        val preparedHeaders = buildPlaybackRequestHeaders(requestHeaders)
        val legacyFallbackBitrate = fallbackBitrate?.takeIf { it > 0 } ?: DEFAULT_FALLBACK_BITRATE
        val sourceIdentity = sourceIdentityProvider()
        if (preferredClipMode != OnlineClipMode.PREVIEW) {
            val primaryKey = OnlinePlaybackCacheKey(
                sourceIdentity = sourceIdentity,
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
        }.fold(
            onSuccess = { payload ->
                runCatching {
                    parseSongUrlAttempt(payload = payload, requestHeaders = preparedHeaders, nowMs = nowMs())
                }.getOrElse { error ->
                    SongUrlAttempt(
                        resolved = null,
                        message = error.message,
                        cause = error
                    )
                }
            },
            onFailure = { error ->
                SongUrlAttempt(
                    resolved = null,
                    message = error.message,
                    cause = error
                )
            }
        )

        v1Attempt?.resolved?.let { resolved ->
            val normalized = resolved.normalizeForExpectedDuration(expectedDurationMs)
            cacheResolved(songId = songId, level = requestedLevel, resolved = normalized)
            return Result.success(normalized)
        }

        val legacyAttempt = runCatching {
            remoteDataSource.fetchSongUrl(
                songIds = songId,
                bitrate = legacyFallbackBitrate,
                requestHeaders = preparedHeaders
            )
        }.fold(
            onSuccess = { payload ->
                runCatching {
                    parseSongUrlAttempt(payload = payload, requestHeaders = preparedHeaders, nowMs = nowMs())
                }.getOrElse { error ->
                    SongUrlAttempt(
                        resolved = null,
                        message = error.message,
                        cause = error
                    )
                }
            },
            onFailure = { error ->
                SongUrlAttempt(
                    resolved = null,
                    message = error.message,
                    cause = error
                )
            }
        )

        legacyAttempt?.resolved?.let { resolved ->
            val normalized = resolved.normalizeForExpectedDuration(expectedDurationMs)
            cacheResolved(songId = songId, level = requestedLevel, resolved = normalized)
            return Result.success(normalized)
        }

        val checkMusicAttempt = runCatching {
            remoteDataSource.checkMusic(
                songId = songId,
                bitrate = legacyFallbackBitrate,
                requestHeaders = preparedHeaders
            )
        }.fold(
            onSuccess = { payload ->
                ResolverFailureContext(message = payload.stringValue("message"))
            },
            onFailure = { error ->
                ResolverFailureContext(
                    message = error.message,
                    cause = error
                )
            }
        )

        val failure = classifyOnlinePlaybackFailure(
            v1Attempt = v1Attempt,
            legacyAttempt = legacyAttempt,
            checkMusicAttempt = checkMusicAttempt
        )
        return Result.failure(OnlinePlaybackResolutionException(failure))
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
            OnlinePlaybackCacheKey(
                sourceIdentity = sourceIdentityProvider(),
                songId = songId,
                level = level,
                clipMode = OnlineClipMode.FULL
            ),
            resolved
        )
    }

    fun clear() {
        memoryCache.clear()
    }

    private companion object {
        private const val DEFAULT_FALLBACK_BITRATE = 320_000
    }
}

internal data class OnlinePlaybackPlan(
    val resourceKey: String,
    val playbackUrl: String?,
    val requestHeaders: Map<String, String>,
    val preferredAudioQuality: PlaybackAudioQuality,
    val appliedAudioQuality: PlaybackAudioQuality,
    val durationHintMs: Long,
    val contentLengthHintBytes: Long?,
    val previewClip: PlaybackPreviewClip?,
    val useCacheOnlyProvider: Boolean,
    val cacheExtraMetadata: Map<String, String> = emptyMap()
)

internal class OnlinePlaybackPreparationPlanner(
    private val cacheLookup: (String) -> Result<CacheLookupSnapshot?> = CacheCore::lookup,
    private val resolver: OnlinePlaybackResolver? = null,
    private val audioQualityCatalogProvider: suspend (String, Map<String, String>) -> SongAudioQualityCatalog? = { _, _ -> null },
    private val sourceAdapterProvider: (() -> SourceAdapter)? = null,
    private val defaultLevel: String = DEFAULT_PLAYBACK_LEVEL
) {
    suspend fun buildPlan(
        track: PlaybackTrack,
        preferredAudioQuality: PlaybackAudioQuality = PlaybackAudioQuality.EXHIGH
    ): Result<OnlinePlaybackPlan> {
        val songId = track.songId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalArgumentException("Missing songId for online track"))
        val sourceAdapter = currentSourceAdapter()
            ?: return Result.failure(IllegalStateException("Online playback source adapter unavailable"))
        val actionContext = track.toSourceActionContext(preferredAudioQuality)
        val preview = runCatching {
            sourceAdapter.previewResolveMusicUrl(actionContext).getOrNull()
        }.getOrNull()
        val requestedMode = preview?.preferredClipMode ?: if (track.previewClip != null) {
            OnlineClipMode.PREVIEW
        } else {
            OnlineClipMode.FULL
        }
        val appliedLevel = (preview?.appliedAudioQuality ?: preferredAudioQuality).wireValue
        val initialKey = buildOnlineResourceKey(songId, appliedLevel, requestedMode)
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
                    preferredAudioQuality = preferredAudioQuality,
                    appliedAudioQuality = preview?.appliedAudioQuality ?: preferredAudioQuality,
                    durationHintMs = firstPositive(track.durationHintMs, initialSnapshot?.durationMs),
                    contentLengthHintBytes = initialSnapshot?.contentLength?.takeIf { it > 0L },
                    previewClip = track.previewClip,
                    useCacheOnlyProvider = true,
                    cacheExtraMetadata = OnlineCacheMetadata.buildExtraMetadata(requestedMode)
                )
            )
        }

        val resolvedMusicUrl = sourceAdapter.handle(
            action = SourceAction.ResolveMusicUrl,
            context = actionContext
        ).getOrElse { return Result.failure(it) } as? SourceActionResult.MusicUrl
            ?: return Result.failure(
                IllegalStateException("Source adapter returned unsupported music url result")
            )

        val actualPreviewClip = resolvedMusicUrl.previewClip ?: track.previewClip
        val actualMode = if (actualPreviewClip != null) {
            OnlineClipMode.PREVIEW
        } else {
            OnlineClipMode.FULL
        }
        val finalKey = buildOnlineResourceKey(
            songId = songId,
            level = resolvedMusicUrl.appliedAudioQuality.wireValue,
            clipMode = actualMode
        )
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
                playbackUrl = resolvedMusicUrl.playbackUrl,
                requestHeaders = resolvedMusicUrl.requestHeaders,
                preferredAudioQuality = preferredAudioQuality,
                appliedAudioQuality = resolvedMusicUrl.appliedAudioQuality,
                durationHintMs = firstPositive(
                    track.durationHintMs,
                    resolvedMusicUrl.durationMs,
                    completeSnapshot?.durationMs
                ),
                contentLengthHintBytes = completeSnapshot?.contentLength?.takeIf { it > 0L }
                    ?: resolvedMusicUrl.contentLengthBytes,
                previewClip = actualPreviewClip,
                useCacheOnlyProvider = completeSnapshot != null,
                cacheExtraMetadata = OnlineCacheMetadata.buildExtraMetadata(actualMode)
            )
        )
    }

    private fun currentSourceAdapter(): SourceAdapter? {
        return sourceAdapterProvider?.invoke()
            ?: resolver?.let { resolver ->
                ResolverBackedSourceAdapter(
                    resolver = resolver,
                    audioQualityCatalogProvider = audioQualityCatalogProvider
                )
            }
    }

    private companion object {
        private const val DEFAULT_PLAYBACK_LEVEL = "exhigh"
    }
}

private fun PlaybackTrack.toSourceActionContext(
    preferredAudioQuality: PlaybackAudioQuality
): SourceActionContext {
    return SourceActionContext(
        songId = songId,
        title = displayName,
        artistText = artistText,
        albumTitle = playable.albumTitle,
        durationMs = durationHintMs,
        preferredAudioQuality = preferredAudioQuality,
        requestHeaders = requestHeaders,
        previewClip = previewClip
    )
}

private data class SelectedOnlineAudioQuality(
    val appliedAudioQuality: PlaybackAudioQuality,
    val legacyFallbackBitrate: Int?
)

private fun selectAppliedAudioQuality(
    preferredAudioQuality: PlaybackAudioQuality,
    catalog: SongAudioQualityCatalog?
): SelectedOnlineAudioQuality {
    val options = catalog?.options.orEmpty()
    if (options.isEmpty()) {
        return SelectedOnlineAudioQuality(
            appliedAudioQuality = preferredAudioQuality,
            legacyFallbackBitrate = null
        )
    }

    val uniqueOptions = options
        .sortedWith(
            compareByDescending<SongAudioQualityOption> { it.quality.sortOrder }
                .thenBy { it.rawKey }
        )
        .distinctBy { it.quality }
    val optionsByQuality = uniqueOptions.associateBy { it.quality }
    val exactMatch = optionsByQuality[preferredAudioQuality]
    val appliedOption = exactMatch
        ?: resolveNearestLowerOption(preferredAudioQuality, optionsByQuality)
        ?: uniqueOptions.first()

    return SelectedOnlineAudioQuality(
        appliedAudioQuality = appliedOption.quality,
        legacyFallbackBitrate = resolveLegacyFallbackBitrate(
            appliedOption = appliedOption,
            uniqueOptions = uniqueOptions
        )
    )
}

private fun resolveNearestLowerOption(
    preferredAudioQuality: PlaybackAudioQuality,
    optionsByQuality: Map<PlaybackAudioQuality, SongAudioQualityOption>
): SongAudioQualityOption? {
    val preferenceOrder = PlaybackAudioQuality.descendingPreference
    val preferredIndex = preferenceOrder.indexOf(preferredAudioQuality)
    if (preferredIndex < 0) {
        return null
    }
    return preferenceOrder
        .drop(preferredIndex + 1)
        .firstNotNullOfOrNull { optionsByQuality[it] }
}

private fun resolveLegacyFallbackBitrate(
    appliedOption: SongAudioQualityOption,
    uniqueOptions: List<SongAudioQualityOption>
): Int? {
    if (appliedOption.bitRate <= 0) {
        return null
    }
    if (appliedOption.quality.sortOrder <= PlaybackAudioQuality.LOSSLESS.sortOrder) {
        return appliedOption.bitRate
    }
    return uniqueOptions
        .firstOrNull { option ->
            option.bitRate > 0 &&
                option.quality.sortOrder <= PlaybackAudioQuality.LOSSLESS.sortOrder
        }
        ?.bitRate
        ?: appliedOption.bitRate
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
    val message: String?,
    val cause: Throwable? = null
)

private data class ResolverFailureContext(
    val message: String?,
    val cause: Throwable? = null
)

private fun classifyOnlinePlaybackFailure(
    v1Attempt: SongUrlAttempt,
    legacyAttempt: SongUrlAttempt,
    checkMusicAttempt: ResolverFailureContext
): OnlinePlaybackFailure {
    val messages = listOfNotNull(
        v1Attempt.message?.takeIfMeaningfulFailure(),
        legacyAttempt.message?.takeIfMeaningfulFailure(),
        checkMusicAttempt.message?.takeIfMeaningfulFailure()
    )

    messages.firstOrNull(::looksLikeExpiredPlaybackMessage)?.let { message ->
        return OnlinePlaybackFailure(
            kind = OnlinePlaybackFailureKind.URL_EXPIRED,
            message = message
        )
    }

    messages.firstOrNull(::looksLikeUnauthorizedPlaybackMessage)?.let { message ->
        return OnlinePlaybackFailure(
            kind = OnlinePlaybackFailureKind.UNAUTHORIZED,
            message = message
        )
    }

    messages.firstOrNull(::looksLikeResourceUnavailableMessage)?.let { message ->
        return OnlinePlaybackFailure(
            kind = OnlinePlaybackFailureKind.RESOURCE_UNAVAILABLE,
            message = message
        )
    }

    val networkCause = listOfNotNull(
        v1Attempt.cause,
        legacyAttempt.cause,
        checkMusicAttempt.cause
    ).firstOrNull(::isRetryableNetworkFailure)
    if (networkCause != null) {
        return OnlinePlaybackFailure(
            kind = OnlinePlaybackFailureKind.RETRYABLE_NETWORK,
            message = messages.firstOrNull(::looksLikeRetryableNetworkMessage)
                ?: networkCause.message
                ?: "Temporary network failure",
            cause = networkCause
        )
    }

    messages.firstOrNull(::looksLikeRetryableNetworkMessage)?.let { message ->
        return OnlinePlaybackFailure(
            kind = OnlinePlaybackFailureKind.RETRYABLE_NETWORK,
            message = message
        )
    }

    return OnlinePlaybackFailure(
        kind = OnlinePlaybackFailureKind.UNKNOWN,
        message = messages.firstOrNull() ?: "Failed to resolve online stream"
    )
}

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

private fun String.takeIfMeaningfulFailure(): String? {
    val normalized = trim()
    if (normalized.isEmpty()) {
        return null
    }
    return if (
        normalized.equals("ok", ignoreCase = true) ||
        normalized.equals("success", ignoreCase = true)
    ) {
        null
    } else {
        normalized
    }
}

private fun looksLikeExpiredPlaybackMessage(message: String): Boolean {
    val normalized = message.lowercase()
    return "expired" in normalized ||
        "expire" in normalized ||
        "过期" in message ||
        "失效" in message
}

private fun looksLikeResourceUnavailableMessage(message: String): Boolean {
    val normalized = message.lowercase()
    return "暂无版权" in message ||
        "无版权" in message ||
        "下架" in message ||
        "不可用" in message ||
        "无可播" in message ||
        "resource unavailable" in normalized ||
        "not available" in normalized
}

private fun looksLikeUnauthorizedPlaybackMessage(message: String): Boolean {
    val normalized = message.lowercase()
    return "未登录" in message ||
        "登录" in message ||
        "unauthorized" in normalized ||
        "login" in normalized ||
        "401" in normalized ||
        "403" in normalized
}

private fun looksLikeRetryableNetworkMessage(message: String): Boolean {
    val normalized = message.lowercase()
    return "network" in normalized ||
        "timeout" in normalized ||
        "timed out" in normalized ||
        "connection" in normalized ||
        "reset" in normalized ||
        "temporarily" in normalized ||
        "unreachable" in normalized
}

private fun isRetryableNetworkFailure(error: Throwable): Boolean {
    return generateSequence(error) { it.cause }.any { cause ->
        cause is IOException ||
            cause.message?.let(::looksLikeRetryableNetworkMessage) == true
    }
}

internal fun Throwable.asOnlinePlaybackFailure(): OnlinePlaybackFailure? {
    return (this as? OnlinePlaybackResolutionException)?.failure
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

private fun buildSongAudioQualityCatalogCacheKey(
    songId: String,
    requestHeaders: Map<String, String>,
    sourceIdentity: String = ""
): String {
    val normalizedSourceIdentity = sourceIdentity.trim().trimEnd('/')
    if (requestHeaders.isEmpty()) {
        return if (normalizedSourceIdentity.isBlank()) {
            songId
        } else {
            "$normalizedSourceIdentity::$songId"
        }
    }
    val identity = requestHeaders.entries
        .mapNotNull { (key, value) ->
            val normalizedKey = key.trim().lowercase()
            val normalizedValue = value.trim()
            if (normalizedKey.isEmpty() || normalizedValue.isEmpty()) {
                null
            } else {
                "$normalizedKey=$normalizedValue"
            }
        }
        .sorted()
        .joinToString(separator = "&")
    return buildString {
        if (normalizedSourceIdentity.isNotBlank()) {
            append(normalizedSourceIdentity)
            append("::")
        }
        append(songId)
        if (identity.isNotBlank()) {
            append("::")
            append(identity)
        }
    }
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())
