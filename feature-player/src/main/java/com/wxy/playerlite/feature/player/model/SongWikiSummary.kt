package com.wxy.playerlite.feature.player.model

data class SongWikiSummary(
    val title: String,
    val coverUrl: String?,
    val contributionText: String?,
    val sections: List<SongWikiSummarySection>,
    val similarSongs: List<SongWikiSummarySong> = emptyList(),
    val relatedPlaylists: List<SongWikiSummaryPlaylist> = emptyList()
)

data class SongWikiSummarySection(
    val title: String,
    val values: List<String>
)

data class SongWikiSummarySong(
    val songId: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String?
)

data class SongWikiSummaryPlaylist(
    val playlistId: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String?
)
