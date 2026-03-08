package com.wxy.playerlite.playback.client

import android.os.Bundle
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.player.PlaybackSpeed

internal object RemotePlaybackSnapshotMapper {
    fun map(
        playbackState: Int,
        playWhenReady: Boolean,
        isPlaying: Boolean,
        isSeekSupported: Boolean,
        currentPositionMs: Long,
        durationMs: Long,
        playbackParametersSpeed: Float,
        currentMetadataExtras: Bundle?,
        sessionExtras: Bundle?,
        rootMetadataExtras: Bundle?,
        currentMediaId: String?,
        statusText: String?
    ): RemotePlaybackSnapshot {
        val playbackSpeed = playbackParametersSpeed
            .takeIf { it > 0f }
            ?: PlaybackMetadataExtras.readPlaybackSpeed(currentMetadataExtras)
            ?: PlaybackMetadataExtras.readPlaybackSpeed(sessionExtras)
            ?: PlaybackMetadataExtras.readPlaybackSpeed(rootMetadataExtras)
            ?: PlaybackSpeed.DEFAULT.value
        return RemotePlaybackSnapshot(
            playbackState = playbackState,
            playWhenReady = playWhenReady,
            isPlaying = isPlaying,
            isSeekSupported = isSeekSupported,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            statusText = statusText,
            currentMediaId = currentMediaId,
            playbackOutputInfo = PlaybackMetadataExtras.readPlaybackOutputInfo(currentMetadataExtras)
                ?: PlaybackMetadataExtras.readPlaybackOutputInfo(sessionExtras)
                ?: PlaybackMetadataExtras.readPlaybackOutputInfo(rootMetadataExtras),
            audioMeta = PlaybackMetadataExtras.readAudioMeta(currentMetadataExtras)
                ?: PlaybackMetadataExtras.readAudioMeta(sessionExtras)
                ?: PlaybackMetadataExtras.readAudioMeta(rootMetadataExtras)
        )
    }
}
