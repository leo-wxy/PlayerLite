package com.wxy.playerlite.feature.local

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType

internal data class LocalSongEntry(
    val id: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long
) {
    fun toPlaylistItem(): PlaylistItem {
        return PlaylistItem(
            id = id,
            uri = contentUri,
            displayName = title,
            title = title,
            artistText = artist,
            albumTitle = album,
            durationMs = durationMs,
            itemType = PlaylistItemType.LOCAL
        )
    }
}

internal data class LocalSongsUiState(
    val songs: List<LocalSongEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val requiresPermission: Boolean = false,
    val errorMessage: String? = null,
    val hasCachedSongs: Boolean = false
)

internal sealed interface LocalSongsUiEvent {
    data object OpenPlayer : LocalSongsUiEvent

    data class ShowMessage(
        val message: String
    ) : LocalSongsUiEvent
}
