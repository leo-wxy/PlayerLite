package com.wxy.playerlite.playback.process

import android.net.Uri
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.source.IPlaysource
import com.wxy.playerlite.player.source.LocalFileSource
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
}

internal sealed interface PreparationResult {
    data class Ready(
        val source: IPlaysource,
        val mediaMeta: AudioMetaDisplay
    ) : PreparationResult

    data class Invalid(val message: String) : PreparationResult
}
