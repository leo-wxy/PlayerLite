package com.wxy.playerlite.feature.webplaylistimport

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.feature.search.SearchResultUiModel

data class ImportedPlaylistSnapshot(
    val source: ImportedPlaylistSource,
    val playlistId: String,
    val sourceUrl: String,
    val title: String,
    val creatorName: String,
    val description: String,
    val coverUrl: String?,
    val tracks: List<ImportedPlaylistTrack>
) {
    val summary: ImportedPlaylistSummary
        get() = ImportedPlaylistSummary.fromTracks(tracks)
}

data class ImportedPlaylistTrack(
    val sourceTrackId: String?,
    val title: String,
    val artistNames: List<String> = emptyList(),
    val artistText: String = artistNames.joinToString(separator = " / ").ifBlank { "" },
    val albumTitle: String = "",
    val coverUrl: String? = null,
    val durationMs: Long = 0L,
    val resolution: ImportedTrackResolution = ImportedTrackResolution.Unmatched
) {
    val resolvedSong: ResolvedImportedSong?
        get() = when (val current = resolution) {
            is ImportedTrackResolution.Direct -> current.song
            is ImportedTrackResolution.Matched -> current.song
            is ImportedTrackResolution.Ambiguous -> null
            ImportedTrackResolution.Unmatched -> null
        }

    val resolvedSongId: String?
        get() = resolvedSong?.songId
}

data class ImportedPlaylistSummary(
    val totalCount: Int,
    val directCount: Int,
    val matchedCount: Int,
    val ambiguousCount: Int,
    val unmatchedCount: Int
) {
    companion object {
        fun fromTracks(tracks: List<ImportedPlaylistTrack>): ImportedPlaylistSummary {
            var directCount = 0
            var matchedCount = 0
            var ambiguousCount = 0
            var unmatchedCount = 0
            tracks.forEach { track ->
                when (track.resolution) {
                    is ImportedTrackResolution.Direct -> directCount += 1
                    is ImportedTrackResolution.Matched -> matchedCount += 1
                    is ImportedTrackResolution.Ambiguous -> ambiguousCount += 1
                    ImportedTrackResolution.Unmatched -> unmatchedCount += 1
                }
            }
            return ImportedPlaylistSummary(
                totalCount = tracks.size,
                directCount = directCount,
                matchedCount = matchedCount,
                ambiguousCount = ambiguousCount,
                unmatchedCount = unmatchedCount
            )
        }
    }
}

data class ResolvedImportedSong(
    val songId: String,
    val title: String,
    val artistText: String,
    val primaryArtistId: String? = null,
    val albumTitle: String = "",
    val coverUrl: String? = null,
    val durationMs: Long = 0L
)

sealed interface ImportedTrackResolution {
    data class Direct(
        val song: ResolvedImportedSong
    ) : ImportedTrackResolution

    data class Matched(
        val song: ResolvedImportedSong
    ) : ImportedTrackResolution

    data class Ambiguous(
        val candidates: List<ResolvedImportedSong>
    ) : ImportedTrackResolution

    data object Unmatched : ImportedTrackResolution
}

fun SearchResultUiModel.Song.toResolvedImportedSong(): ResolvedImportedSong {
    return ResolvedImportedSong(
        songId = id,
        title = title,
        artistText = artistText,
        albumTitle = albumTitle,
        coverUrl = coverUrl
    )
}

fun ImportedPlaylistTrack.toPlaylistItem(
    source: ImportedPlaylistSource,
    playlistId: String,
    playlistTitle: String,
    index: Int
): PlaylistItem? {
    val resolved = resolvedSong ?: return null
    val contextId = "import:${source.wireValue}:$playlistId"
    return PlaylistItem(
        id = "import:${source.wireValue}:$playlistId:$index:${resolved.songId}",
        displayName = resolved.title,
        songId = resolved.songId,
        title = resolved.title,
        artistText = resolved.artistText,
        primaryArtistId = resolved.primaryArtistId,
        albumTitle = resolved.albumTitle,
        coverUrl = resolved.coverUrl,
        durationMs = resolved.durationMs,
        itemType = PlaylistItemType.ONLINE,
        contextType = "imported_playlist",
        contextId = contextId,
        contextTitle = playlistTitle
    )
}
