package com.wxy.playerlite.playback.process

import android.net.Uri
import android.util.Log
import com.wxy.playerlite.cache.core.session.SessionCacheConfig
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.source.IPlaysource
import com.wxy.playerlite.playback.process.source.CachedNetworkSource
import com.wxy.playerlite.playback.process.source.OkHttpRangeDataProvider
import com.wxy.playerlite.playback.process.source.ProviderBackedNetworkProbeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TrackPreparationCoordinator(
    private val sourceRepository: MediaSourceRepository,
    private val playbackCoordinator: PlaybackCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun prepare(item: PlaybackTrack): PreparationResult {
        val sourceUri = try {
            Uri.parse(item.uri)
        } catch (_: IllegalArgumentException) {
            return PreparationResult.Invalid("Invalid media uri")
        }

        val scheme = sourceUri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
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
        return prepareNetworkSourceInternal(
            item = item,
            createSource = {
                val provider = OkHttpRangeDataProvider(item.uri)
                CachedNetworkSource(
                    resourceKey = item.id.ifBlank { item.uri },
                    provider = provider,
                    sessionConfig = SessionCacheConfig()
                )
            },
            createMetadataProbeSource = {
                ProviderBackedNetworkProbeSource(
                    id = item.id.ifBlank { item.uri },
                    provider = OkHttpRangeDataProvider(item.uri)
                )
            },
            loadAudioMeta = { source ->
                playbackCoordinator.loadAudioMetaDisplayFromSource(source)
            },
            logInfo = ::safeLogI,
            logError = ::safeLogE
        )
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
}

internal suspend fun prepareNetworkSourceInternal(
    item: PlaybackTrack,
    createSource: () -> IPlaysource,
    createMetadataProbeSource: (() -> IPlaysource)? = null,
    loadAudioMeta: suspend (IPlaysource) -> AudioMetaDisplay,
    logInfo: (String) -> Unit = {},
    logError: (String) -> Unit = {}
): PreparationResult {
    logInfo("prepareNetworkSource: key=${item.id}, uri=${item.uri}")
    val source = createSource()
    source.setSourceMode(IPlaysource.SourceMode.NORMAL)
    val openCode = source.open()
    if (openCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
        logError("prepareNetworkSource open failed: code=${openCode.code}, key=${item.id}")
        source.close()
        return PreparationResult.Invalid("Source open failed(${openCode.code})")
    }

    val metadataProbe = createMetadataProbeSource?.invoke()
    val mediaMeta = try {
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
            loadAudioMeta(source)
        }
    } catch (cancel: CancellationException) {
        metadataProbe?.abort()
        metadataProbe?.close()
        source.abort()
        source.close()
        throw cancel
    } catch (_: Exception) {
        logError("prepareNetworkSource metadata load failed: key=${item.id}")
        placeholderAudioMetaDisplay()
    } finally {
        if (metadataProbe != null) {
            metadataProbe.close()
        }
    }

    if (source.seek(0L, IPlaysource.SEEK_SET) < 0L) {
        logError("prepareNetworkSource seek rewind failed: key=${item.id}")
        source.close()
        return PreparationResult.Invalid("Source rewind failed")
    }

    logInfo("prepareNetworkSource ready: key=${item.id}, durationMs=${mediaMeta.durationMs}")
    return PreparationResult.Ready(
        source = source,
        isSeekSupported = source.supportFastSeek(),
        mediaMeta = mediaMeta
    )
}

private fun placeholderAudioMetaDisplay(): AudioMetaDisplay {
    return AudioMetaDisplay(
        codec = "-",
        sampleRate = "-",
        channels = "-",
        bitRate = "-",
        durationMs = 0L
    )
}

internal sealed interface PreparationResult {
    data class Ready(
        val source: IPlaysource,
        val mediaMeta: AudioMetaDisplay,
        val isSeekSupported: Boolean
    ) : PreparationResult

    data class Invalid(val message: String) : PreparationResult
}
