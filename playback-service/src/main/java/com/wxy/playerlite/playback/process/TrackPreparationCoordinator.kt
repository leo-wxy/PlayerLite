package com.wxy.playerlite.playback.process

import android.net.Uri
import android.util.Log
import com.wxy.playerlite.cache.core.session.SessionCacheConfig
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.source.IPlaysource
import com.wxy.playerlite.playback.process.source.CachedNetworkSource
import com.wxy.playerlite.playback.process.source.OkHttpRangeDataProvider
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
                AudioMetaDisplay(
                    codec = "-",
                    sampleRate = "-",
                    channels = "-",
                    bitRate = "-",
                    durationMs = 0L
                )
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
        safeLogI("prepareNetworkSource: key=${item.id}, uri=${item.uri}")
        val provider = OkHttpRangeDataProvider(item.uri)
        val source = CachedNetworkSource(
            resourceKey = item.id.ifBlank { item.uri },
            provider = provider,
            sessionConfig = SessionCacheConfig()
        )
        source.setSourceMode(IPlaysource.SourceMode.NORMAL)
        val openCode = source.open()
        if (openCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
            safeLogE("prepareNetworkSource open failed: code=${openCode.code}, key=${item.id}")
            source.close()
            return PreparationResult.Invalid("Source open failed(${openCode.code})")
        }
        if (source.seek(0L, IPlaysource.SEEK_SET) < 0L) {
            safeLogE("prepareNetworkSource seek rewind failed: key=${item.id}")
            source.close()
            return PreparationResult.Invalid("Source rewind failed")
        }

        safeLogI("prepareNetworkSource ready: key=${item.id}")
        return PreparationResult.Ready(
            source = source,
            isSeekSupported = source.supportFastSeek(),
            mediaMeta = AudioMetaDisplay(
                codec = "-",
                sampleRate = "-",
                channels = "-",
                bitRate = "-",
                durationMs = 0L
            )
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

internal sealed interface PreparationResult {
    data class Ready(
        val source: IPlaysource,
        val mediaMeta: AudioMetaDisplay,
        val isSeekSupported: Boolean
    ) : PreparationResult

    data class Invalid(val message: String) : PreparationResult
}
