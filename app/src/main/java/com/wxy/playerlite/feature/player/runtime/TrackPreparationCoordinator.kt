package com.wxy.playerlite.feature.player.runtime

import android.net.Uri
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.source.IPlaysource
import com.wxy.playerlite.core.playlist.PlaylistItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TrackPreparationCoordinator(
    private val sourceRepository: MediaSourceRepository,
    private val playbackCoordinator: PlaybackCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun prepare(item: PlaylistItem): PreparationResult {
        val sourceUri = try {
            Uri.parse(item.uri)
        } catch (_: IllegalArgumentException) {
            return PreparationResult.Invalid("Invalid media uri")
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
}

internal sealed interface PreparationResult {
    data class Ready(
        val source: IPlaysource,
        val mediaMeta: AudioMetaDisplay,
        val isSeekSupported: Boolean
    ) : PreparationResult

    data class Invalid(val message: String) : PreparationResult
}
