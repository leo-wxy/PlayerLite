package com.wxy.playerlite.feature.player.model

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.PlaybackSpeed
import com.wxy.playerlite.player.PlaybackOutputInfo

internal data class PlayerUiState(
    val selectedFileName: String = "No audio selected",
    val statusText: String = "Pick a local audio file, then tap Play",
    val audioMeta: AudioMetaDisplay = emptyAudioMeta(),
    val playbackOutputInfo: PlaybackOutputInfo? = null,
    val hasSelection: Boolean = false,
    val playlistItems: List<PlaylistItem> = emptyList(),
    val activePlaylistIndex: Int = -1,
    val showPlaylistSheet: Boolean = false,
    val isPreparing: Boolean = false,
    val playbackState: Int = AUDIO_TRACK_PLAYSTATE_UNAVAILABLE,
    val isSeekSupported: Boolean = false,
    val playbackSpeed: Float = PlaybackSpeed.DEFAULT.value,
    val durationMs: Long = 0L,
    val seekPositionMs: Long = 0L,
    val seekDragPositionMs: Long = 0L,
    val isSeekDragging: Boolean = false
) {
    val displayedSeekMs: Long
        get() = if (isSeekDragging) seekDragPositionMs else seekPositionMs
}

internal fun emptyAudioMeta(): AudioMetaDisplay {
    return AudioMetaDisplay(
        codec = "-",
        sampleRate = "-",
        channels = "-",
        bitRate = "-",
        durationMs = 0L
    )
}
