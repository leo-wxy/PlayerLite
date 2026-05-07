package com.wxy.playerlite.playback.client

import android.os.Bundle
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.PlaybackSpeed

internal object RemotePlaybackSnapshotMapper {
    fun readPreferredCacheProgress(
        currentMetadataExtras: Bundle?,
        sessionExtras: Bundle?,
        rootMetadataExtras: Bundle?
    ) = PlaybackMetadataExtras.readCacheProgress(sessionExtras)
        ?: PlaybackMetadataExtras.readCacheProgress(currentMetadataExtras)
        ?: PlaybackMetadataExtras.readCacheProgress(rootMetadataExtras)

    fun map(
        playbackState: Int,
        playWhenReady: Boolean,
        isPlaying: Boolean,
        isSeekSupported: Boolean,
        currentPositionMs: Long,
        bufferedPositionMs: Long = currentPositionMs,
        durationMs: Long,
        playbackParametersSpeed: Float,
        currentMetadataExtras: Bundle?,
        sessionExtras: Bundle?,
        rootMetadataExtras: Bundle?,
        currentPlayable: PlayableItemSnapshot?,
        currentMediaId: String?,
        statusText: String?
    ): RemotePlaybackSnapshot {
        val playbackSpeed = playbackParametersSpeed
            .takeIf { it > 0f }
            ?: PlaybackMetadataExtras.readPlaybackSpeed(currentMetadataExtras)
            ?: PlaybackMetadataExtras.readPlaybackSpeed(sessionExtras)
            ?: PlaybackMetadataExtras.readPlaybackSpeed(rootMetadataExtras)
            ?: PlaybackSpeed.DEFAULT.value
        val playbackMode = PlaybackMetadataExtras.readPlaybackMode(currentMetadataExtras)
            ?: PlaybackMetadataExtras.readPlaybackMode(sessionExtras)
            ?: PlaybackMetadataExtras.readPlaybackMode(rootMetadataExtras)
            ?: PlaybackMode.LIST_LOOP
        val audioEffectPreset = PlaybackMetadataExtras.readAudioEffectPreset(currentMetadataExtras)
            ?: PlaybackMetadataExtras.readAudioEffectPreset(sessionExtras)
            ?: PlaybackMetadataExtras.readAudioEffectPreset(rootMetadataExtras)
        val preferredAudioQuality = PlaybackMetadataExtras.readPreferredAudioQuality(currentMetadataExtras)
            ?: PlaybackMetadataExtras.readPreferredAudioQuality(sessionExtras)
            ?: PlaybackMetadataExtras.readPreferredAudioQuality(rootMetadataExtras)
        val appliedAudioQuality = PlaybackMetadataExtras.readAppliedAudioQuality(currentMetadataExtras)
            ?: PlaybackMetadataExtras.readAppliedAudioQuality(sessionExtras)
            ?: PlaybackMetadataExtras.readAppliedAudioQuality(rootMetadataExtras)
        return RemotePlaybackSnapshot(
            playbackState = playbackState,
            playWhenReady = playWhenReady,
            isPlaying = isPlaying,
            isSeekSupported = isSeekSupported,
            currentPositionMs = currentPositionMs,
            bufferedPositionMs = bufferedPositionMs,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            playbackMode = playbackMode,
            audioEffectPreset = audioEffectPreset,
            preferredAudioQuality = preferredAudioQuality,
            appliedAudioQuality = appliedAudioQuality,
            statusText = statusText,
            currentPlayable = currentPlayable,
            currentMediaId = currentMediaId,
            playbackOutputInfo = PlaybackMetadataExtras.readPlaybackOutputInfo(currentMetadataExtras)
                ?: PlaybackMetadataExtras.readPlaybackOutputInfo(sessionExtras)
                ?: PlaybackMetadataExtras.readPlaybackOutputInfo(rootMetadataExtras),
            audioMeta = PlaybackMetadataExtras.readAudioMeta(currentMetadataExtras)
                ?: PlaybackMetadataExtras.readAudioMeta(sessionExtras)
                ?: PlaybackMetadataExtras.readAudioMeta(rootMetadataExtras),
            cacheProgress = readPreferredCacheProgress(
                currentMetadataExtras = currentMetadataExtras,
                sessionExtras = sessionExtras,
                rootMetadataExtras = rootMetadataExtras
            ),
            prewarmSnapshot = PlaybackMetadataExtras.readPlaybackPrewarmSnapshot(sessionExtras)
                ?: PlaybackMetadataExtras.readPlaybackPrewarmSnapshot(rootMetadataExtras)
        )
    }
}
