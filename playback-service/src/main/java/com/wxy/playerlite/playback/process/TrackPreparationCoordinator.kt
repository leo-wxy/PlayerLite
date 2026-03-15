package com.wxy.playerlite.playback.process

import android.net.Uri
import android.util.Log
import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.SessionCacheConfig
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.source.IPlaysource
import com.wxy.playerlite.playback.process.source.CachedNetworkSource
import com.wxy.playerlite.playback.process.source.HttpRangeDataProvider
import com.wxy.playerlite.playback.process.source.ProviderBackedNetworkProbeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TrackPreparationCoordinator(
    private val sourceRepository: MediaSourceRepository,
    private val playbackCoordinator: PlaybackCoordinator,
    private val onlinePreparationPlanner: OnlinePlaybackPreparationPlanner? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun prepare(item: PlaybackTrack): PreparationResult {
        val sourceUri = try {
            Uri.parse(item.uri)
        } catch (_: IllegalArgumentException) {
            return PreparationResult.Invalid("Invalid media uri")
        }

        val scheme = sourceUri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https" || !item.songId.isNullOrBlank()) {
            return prepareNetworkSource(item)
        }

        if (sourceUri.scheme == "content" && !sourceRepository.hasPersistedReadPermission(sourceUri)) {
            return PreparationResult.Invalid("Missing persisted read permission for content URI")
        }

        val source = withContext(ioDispatcher) {
            sourceRepository.createPlayableSource(sourceUri)
        } ?: return PreparationResult.Invalid("Failed to open media source")

        source.setSourceMode(IPlaysource.SourceMode.NORMAL)
        val sourceOpenCode = source.open()
        if (sourceOpenCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
            source.close()
            return PreparationResult.Invalid("Source open failed(${sourceOpenCode.code})")
        }

        val seekSupported = source.supportFastSeek()
        return try {
            val mediaMeta = if (seekSupported) {
                playbackCoordinator.loadAudioMetaDisplayFromSource(source)
            } else {
                placeholderAudioMetaDisplay()
            }
            if (seekSupported) {
                source.seek(0L, IPlaysource.SEEK_SET)
            }
            PreparationResult.Ready(
                source = source,
                mediaMeta = mediaMeta,
                isSeekSupported = seekSupported
            )
        } catch (cancel: CancellationException) {
            source.abort()
            source.close()
            throw cancel
        } catch (_: IllegalArgumentException) {
            source.abort()
            source.close()
            PreparationResult.Invalid("Invalid media uri")
        } catch (_: Exception) {
            source.abort()
            source.close()
            PreparationResult.Invalid("Failed to parse audio file")
        }
    }

    private suspend fun prepareNetworkSource(item: PlaybackTrack): PreparationResult {
        val planner = if (!item.songId.isNullOrBlank()) {
            onlinePreparationPlanner
                ?: return PreparationResult.Invalid("Online playback planner unavailable")
        } else {
            null
        }
        var validationAttempt = 0
        while (true) {
            val plan = if (planner != null) {
                planner.buildPlan(item).getOrElse { error ->
                    return PreparationResult.Invalid(error.message ?: "Failed to resolve online stream")
                }
            } else {
                OnlinePlaybackPlan(
                    resourceKey = item.id.ifBlank { item.uri },
                    playbackUrl = item.uri,
                    requestHeaders = item.requestHeaders,
                    durationHintMs = item.durationHintMs,
                    contentLengthHintBytes = null,
                    previewClip = item.previewClip,
                    useCacheOnlyProvider = false
                )
            }
            if (!plan.useCacheOnlyProvider && plan.playbackUrl.isNullOrBlank()) {
                return PreparationResult.Invalid("Failed to resolve online stream")
            }
            val shouldValidateActualDuration =
                !item.songId.isNullOrBlank() &&
                    item.durationHintMs > 0L &&
                    plan.previewClip == null
            val result = prepareNetworkSourceInternal(
                item = item,
                durationHintMs = plan.durationHintMs,
                preferActualMetadataWhenHintPresent = shouldValidateActualDuration,
                createSource = {
                    val provider = if (plan.useCacheOnlyProvider) {
                        CacheOnlyRangeDataProvider(contentLengthHint = plan.contentLengthHintBytes ?: 0L)
                    } else {
                        HttpRangeDataProvider(
                            url = plan.playbackUrl.orEmpty(),
                            requestHeaders = plan.requestHeaders
                        )
                    }
                    CachedNetworkSource(
                        resourceKey = plan.resourceKey,
                        provider = provider,
                        sessionConfig = SessionCacheConfig(),
                        contentLengthHint = plan.contentLengthHintBytes,
                        durationMsHint = plan.durationHintMs,
                        extraMetadata = plan.cacheExtraMetadata
                    )
                },
                createMetadataProbeSource = if (
                    (!shouldValidateActualDuration && plan.durationHintMs > 0L) || plan.useCacheOnlyProvider
                ) {
                    null
                } else {
                    {
                        ProviderBackedNetworkProbeSource(
                            id = plan.resourceKey,
                            provider = HttpRangeDataProvider(
                                url = plan.playbackUrl.orEmpty(),
                                requestHeaders = plan.requestHeaders
                            )
                        )
                    }
                },
                loadAudioMeta = { source ->
                    playbackCoordinator.loadAudioMetaDisplayFromSource(source)
                },
                logInfo = ::safeLogI,
                logError = ::safeLogE
            )
            val ready = result as? PreparationResult.Ready ?: return result
            if (
                shouldValidateActualDuration &&
                looksLikeShortRestrictedClip(item.durationHintMs, ready.mediaMeta.durationMs)
            ) {
                ready.source.abort()
                ready.source.close()
                invalidateOnlineCache(plan.resourceKey)
                if (validationAttempt == 0) {
                    validationAttempt += 1
                    continue
                }
                return PreparationResult.Invalid("Resolved online stream is shorter than expected")
            }
            return ready
        }
    }

    private companion object {
        private const val TAG = "TrackPrep"
    }

    private fun safeLogI(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun safeLogE(message: String) {
        runCatching { Log.e(TAG, message) }
    }

    private fun invalidateOnlineCache(resourceKey: String) {
        CacheCore.lookup(resourceKey)
            .getOrNull()
            ?.let(OnlineCacheMetadata::purgeSnapshot)
    }
}

internal suspend fun prepareNetworkSourceInternal(
    item: PlaybackTrack,
    durationHintMs: Long = 0L,
    preferActualMetadataWhenHintPresent: Boolean = false,
    createSource: () -> IPlaysource,
    createMetadataProbeSource: (() -> IPlaysource)? = null,
    loadAudioMeta: suspend (IPlaysource) -> AudioMetaDisplay,
    logInfo: (String) -> Unit = {},
    logError: (String) -> Unit = {}
): PreparationResult {
    if (item.requiresAuthorization && item.requestHeaders.isEmpty()) {
        return PreparationResult.Invalid("Missing authorization context for protected online source")
    }
    repeat(2) { attempt ->
        logInfo("prepareNetworkSource: key=${item.id}, uri=${item.uri}, attempt=${attempt + 1}")
        val source = createSource()
        source.setSourceMode(IPlaysource.SourceMode.NORMAL)
        val openCode = source.open()
        if (openCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
            logError("prepareNetworkSource open failed: code=${openCode.code}, key=${item.id}")
            source.close()
            return PreparationResult.Invalid("Source open failed(${openCode.code})")
        }

        var playbackSourceConsumedForMetadata = false
        val mediaMeta = try {
            if (durationHintMs > 0L && !preferActualMetadataWhenHintPresent) {
                placeholderAudioMetaDisplay(durationHintMs)
            } else {
                val metadataProbe = createMetadataProbeSource?.invoke()
                try {
                    if (metadataProbe != null) {
                        metadataProbe.setSourceMode(IPlaysource.SourceMode.NORMAL)
                        val probeOpenCode = metadataProbe.open()
                        if (probeOpenCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
                            logError("prepareNetworkSource metadata probe open failed: code=${probeOpenCode.code}, key=${item.id}")
                            placeholderAudioMetaDisplay()
                        } else {
                            runCatching { loadAudioMeta(metadataProbe) }
                                .onFailure {
                                    logError("prepareNetworkSource metadata load failed: key=${item.id}")
                                }
                                .getOrElse { placeholderAudioMetaDisplay() }
                        }
                    } else {
                        playbackSourceConsumedForMetadata = true
                        loadAudioMeta(source)
                    }
                } finally {
                    metadataProbe?.close()
                }
            }
        } catch (cancel: CancellationException) {
            source.abort()
            source.close()
            throw cancel
        } catch (_: Exception) {
            logError("prepareNetworkSource metadata load failed: key=${item.id}")
            placeholderAudioMetaDisplay()
        }

        if (playbackSourceConsumedForMetadata && source.seek(0L, IPlaysource.SEEK_SET) < 0L) {
            logError("prepareNetworkSource seek rewind failed: key=${item.id}, attempt=${attempt + 1}")
            source.close()
            val freshSource = createSource()
            freshSource.setSourceMode(IPlaysource.SourceMode.NORMAL)
            val freshOpenCode = freshSource.open()
            if (freshOpenCode == IPlaysource.AudioSourceCode.ASC_SUCCESS) {
                logInfo("prepareNetworkSource reopened fresh playback source after rewind failure: key=${item.id}")
                return PreparationResult.Ready(
                    source = freshSource,
                    isSeekSupported = freshSource.supportFastSeek(),
                    mediaMeta = mediaMeta
                )
            }
            logError("prepareNetworkSource reopen failed after rewind failure: code=${freshOpenCode.code}, key=${item.id}")
            freshSource.close()
            if (attempt == 0) {
                return@repeat
            }
            return PreparationResult.Invalid("Source rewind failed")
        }

        logInfo("prepareNetworkSource ready: key=${item.id}, durationMs=${mediaMeta.durationMs}")
        return PreparationResult.Ready(
            source = source,
            isSeekSupported = source.supportFastSeek(),
            mediaMeta = mediaMeta
        )
    }

    return PreparationResult.Invalid("Source rewind failed")
}

private fun placeholderAudioMetaDisplay(durationMs: Long = 0L): AudioMetaDisplay {
    return AudioMetaDisplay(
        codec = "-",
        sampleRate = "-",
        channels = "-",
        bitRate = "-",
        durationMs = durationMs.coerceAtLeast(0L)
    )
}

private class CacheOnlyRangeDataProvider(
    private val contentLengthHint: Long
) : RangeDataProvider {
    override fun readAt(offset: Long, size: Int, callback: RangeDataProvider.ReadCallback) {
        callback.onDataBegin(offset, size)
        callback.onDataEnd(false)
    }

    override fun cancelInFlightRead() = Unit

    override fun queryContentLength(): Long? {
        return contentLengthHint.takeIf { it > 0L }
    }
}

internal sealed interface PreparationResult {
    data class Ready(
        val source: IPlaysource,
        val mediaMeta: AudioMetaDisplay,
        val isSeekSupported: Boolean
    ) : PreparationResult

    data class Invalid(val message: String) : PreparationResult
}
