package com.wxy.playerlite.playback.process

import android.net.Uri
import com.wxy.playerlite.cache.core.session.SessionCacheConfig
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.source.IPlaysource
import com.wxy.playerlite.player.source.LocalFileSource
import com.wxy.playerlite.playback.process.source.CachedNetworkSource
import com.wxy.playerlite.playback.process.source.HttpRangeDataProvider
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

        val sourceFile = withContext(ioDispatcher) {
            sourceRepository.copyUriToCacheFile(sourceUri)
        } ?: return PreparationResult.Invalid("Failed to read audio file")

        val source = LocalFileSource(sourceFile)
        source.setSourceMode(IPlaysource.SourceMode.NORMAL)
        val sourceOpenCode = source.open()
        if (sourceOpenCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
            source.close()
            return PreparationResult.Invalid("Source open failed(${sourceOpenCode.code})")
        }

        return try {
            val mediaMeta = playbackCoordinator.loadAudioMetaDisplayFromSource(source)
            source.seek(0L, IPlaysource.SEEK_SET)
            PreparationResult.Ready(source = source, mediaMeta = mediaMeta)
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

    private fun prepareNetworkSource(item: PlaybackTrack): PreparationResult {
        val provider = HttpRangeDataProvider(item.uri)
        val source = CachedNetworkSource(
            resourceKey = item.id.ifBlank { item.uri },
            provider = provider,
            sessionConfig = SessionCacheConfig()
        )
        source.setSourceMode(IPlaysource.SourceMode.NORMAL)
        val openCode = source.open()
        if (openCode != IPlaysource.AudioSourceCode.ASC_SUCCESS) {
            source.close()
            return PreparationResult.Invalid("Source open failed(${openCode.code})")
        }
        if (source.seek(0L, IPlaysource.SEEK_SET) < 0L) {
            source.close()
            return PreparationResult.Invalid("Source rewind failed")
        }
        return PreparationResult.Ready(
            source = source,
            mediaMeta = AudioMetaDisplay(
                codec = "-",
                sampleRate = "-",
                channels = "-",
                bitRate = "-",
                durationMs = 0L
            )
        )
    }
}

internal sealed interface PreparationResult {
    data class Ready(
        val source: IPlaysource,
        val mediaMeta: AudioMetaDisplay
    ) : PreparationResult

    data class Invalid(val message: String) : PreparationResult
}
