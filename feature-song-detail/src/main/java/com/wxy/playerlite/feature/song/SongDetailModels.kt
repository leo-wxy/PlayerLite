package com.wxy.playerlite.feature.song

import com.wxy.playerlite.core.playlist.PlaylistItem

sealed interface SongRef {
    data class Online(
        val songId: String
    ) : SongRef

    data class Local(
        val playbackUri: String,
        val title: String,
        val artistText: String,
        val albumTitle: String?,
        val durationMs: Long,
        val coverUrl: String? = null
    ) : SongRef
}

enum class SongDetailSource {
    ONLINE,
    LOCAL
}

data class SongDetailWikiSectionUi(
    val title: String,
    val values: List<String>
)

data class SongDetailRelatedSongUi(
    val songId: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String?
)

data class SongDetailRelatedPlaylistUi(
    val playlistId: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String?
)

data class SongDetailWikiUi(
    val title: String,
    val contributionText: String?,
    val sections: List<SongDetailWikiSectionUi>,
    val similarSongs: List<SongDetailRelatedSongUi> = emptyList(),
    val relatedPlaylists: List<SongDetailRelatedPlaylistUi> = emptyList()
)

data class SongDetailContent(
    val ref: SongRef,
    val source: SongDetailSource,
    val recentRecordKey: String? = null,
    val title: String,
    val artistText: String,
    val primaryArtistId: String? = null,
    val albumTitle: String? = null,
    val albumId: String? = null,
    val coverUrl: String? = null,
    val durationMs: Long = 0L,
    val playlistItem: PlaylistItem,
    val wiki: SongDetailWikiUi? = null,
    val canFavorite: Boolean = false
) {
    val canRemoveFromRecent: Boolean
        get() = !recentRecordKey.isNullOrBlank()
}

sealed interface SongDetailContentState {
    data object Loading : SongDetailContentState

    data class Content(
        val content: SongDetailContent
    ) : SongDetailContentState

    data class Error(
        val message: String
    ) : SongDetailContentState
}

data class SongDetailUiState(
    val contentState: SongDetailContentState = SongDetailContentState.Loading,
    val isFavoriting: Boolean = false
)

sealed interface SongDetailEvent {
    data object OpenPlayer : SongDetailEvent

    data object OpenLandscapePlayer : SongDetailEvent

    data class OpenArtist(
        val artistId: String
    ) : SongDetailEvent

    data class OpenAlbum(
        val albumId: String
    ) : SongDetailEvent

    data class OpenSong(
        val songId: String
    ) : SongDetailEvent

    data class OpenPlaylist(
        val playlistId: String
    ) : SongDetailEvent

    data class Share(
        val text: String
    ) : SongDetailEvent

    data class ShowMessage(
        val message: String
    ) : SongDetailEvent
}
