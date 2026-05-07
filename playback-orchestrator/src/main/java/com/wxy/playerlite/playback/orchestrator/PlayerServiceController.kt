package com.wxy.playerlite.playback.orchestrator

import androidx.media3.common.C
import com.wxy.playerlite.playback.client.RemotePlaybackSnapshot
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlayableItem
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.model.PlaybackPrewarmPreferences
import com.wxy.playerlite.player.AudioEffectPreset

interface PlayerServiceController {
    fun prewarmConnection()
    fun ensurePlaybackServiceStartedForPlayback()
    fun connectIfNeeded()

    fun syncQueue(
        queue: List<PlayableItem>,
        activeIndex: Int,
        playWhenReady: Boolean,
        startPositionMs: Long = C.TIME_UNSET
    ): Boolean

    fun play(): Boolean
    fun pause(): Boolean
    fun seekTo(positionMs: Long): Boolean
    fun seekToNextMediaItem(): Boolean
    fun seekToPreviousMediaItem(): Boolean
    fun stop(): Boolean
    fun clearCache(): Boolean
    fun setPlaybackCacheLimitBytes(maxBytes: Long, onResult: ((Boolean) -> Unit)? = null): Boolean
    fun setPlaybackSpeed(speed: Float, onResult: ((Boolean) -> Unit)? = null): Boolean
    fun setAudioEffectPreset(
        audioEffectPreset: AudioEffectPreset,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean
    fun setPreferredAudioQuality(
        audioQuality: PlaybackAudioQuality,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean
    fun setWeakNetworkAutoRetryEnabled(
        enabled: Boolean,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean
    fun setCachePolicyPreferences(
        showCacheFailureNotifications: Boolean,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean
    fun setPlaybackPrewarmPreferences(
        preferences: PlaybackPrewarmPreferences,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean
    fun setActiveAudioSourceConfigJson(
        configJson: String?,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean
    fun setPlaybackMode(playbackMode: PlaybackMode, onResult: ((Boolean) -> Unit)? = null): Boolean
    fun setDisplayMetadata(title: String?, subtitle: String?): Boolean
    fun currentSnapshot(): RemotePlaybackSnapshot?
    fun setSnapshotListener(listener: ((RemotePlaybackSnapshot?) -> Unit)?) = Unit
    fun release()
}
