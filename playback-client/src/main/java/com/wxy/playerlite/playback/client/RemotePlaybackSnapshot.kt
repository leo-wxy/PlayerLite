package com.wxy.playerlite.playback.client

import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.playback.model.PlaybackMode

data class RemotePlaybackSnapshot(
    val playbackState: Int,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val isSeekSupported: Boolean,
    val currentPositionMs: Long,
    val durationMs: Long,
    val playbackSpeed: Float,
    val playbackMode: PlaybackMode,
    val statusText: String?,
    val currentMediaId: String?,
    val playbackOutputInfo: PlaybackOutputInfo?,
    val audioMeta: AudioMetaDisplay?
)
