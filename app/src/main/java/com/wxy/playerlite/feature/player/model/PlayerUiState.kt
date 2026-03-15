package com.wxy.playerlite.feature.player.model

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.PlaybackSpeed
import com.wxy.playerlite.player.PlaybackOutputInfo

internal sealed interface PlayerSongWikiUiState {
    data object Placeholder : PlayerSongWikiUiState
    data object Loading : PlayerSongWikiUiState

    data class Content(
        val summary: SongWikiSummary
    ) : PlayerSongWikiUiState

    data class Empty(
        val message: String
    ) : PlayerSongWikiUiState

    data class Error(
        val message: String
    ) : PlayerSongWikiUiState
}

internal data class PlayerUiState(
    val selectedFileName: String = "No audio selected",
    val currentTrackTitle: String = "No audio selected",
    val currentTrackArtist: String? = null,
    val currentArtistId: String? = null,
    val currentCoverUrl: String? = null,
    val currentSongIdOverride: String? = null,
    val statusText: String = "Pick a local audio file, then tap Play",
    val audioMeta: AudioMetaDisplay = emptyAudioMeta(),
    val playbackOutputInfo: PlaybackOutputInfo? = null,
    val hasSelection: Boolean = false,
    val playlistItems: List<PlaylistItem> = emptyList(),
    val activePlaylistIndex: Int = -1,
    val showPlaylistSheet: Boolean = false,
    val showSongWikiSheet: Boolean = false,
    val songWikiUiState: PlayerSongWikiUiState = PlayerSongWikiUiState.Placeholder,
    val isPreparing: Boolean = false,
    val playbackState: Int = AUDIO_TRACK_PLAYSTATE_UNAVAILABLE,
    val isSeekSupported: Boolean = false,
    val playbackSpeed: Float = PlaybackSpeed.DEFAULT.value,
    val playbackMode: PlaybackMode = PlaybackMode.LIST_LOOP,
    val showOriginalOrderInShuffle: Boolean = false,
    val canReorderPlaylist: Boolean = true,
    val durationMs: Long = 0L,
    val seekPositionMs: Long = 0L,
    val seekDragPositionMs: Long = 0L,
    val isSeekDragging: Boolean = false
) {
    val displayedSeekMs: Long
        get() = if (isSeekDragging) seekDragPositionMs else seekPositionMs

    val currentSongId: String?
        get() = currentSongIdOverride?.takeIf { it.isNotBlank() }
            ?: playlistItems
                .getOrNull(activePlaylistIndex)
                ?.songId
                ?.takeIf { it.isNotBlank() }
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
