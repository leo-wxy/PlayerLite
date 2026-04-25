package com.wxy.playerlite.playback.client

import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot
import com.wxy.playerlite.playback.model.PlaybackMode

data class RemotePlaybackSnapshot(
    val playbackState: Int,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val isSeekSupported: Boolean,
    val currentPositionMs: Long,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long,
    val playbackSpeed: Float,
    val playbackMode: PlaybackMode,
    val audioEffectPreset: AudioEffectPreset? = null,
    val preferredAudioQuality: PlaybackAudioQuality? = null,
    val appliedAudioQuality: PlaybackAudioQuality? = null,
    val statusText: String?,
    val currentPlayable: PlayableItemSnapshot?,
    val currentMediaId: String?,
    val playbackOutputInfo: PlaybackOutputInfo?,
    val audioMeta: AudioMetaDisplay?,
    val cacheProgress: PlaybackCacheProgressSnapshot? = null
)
