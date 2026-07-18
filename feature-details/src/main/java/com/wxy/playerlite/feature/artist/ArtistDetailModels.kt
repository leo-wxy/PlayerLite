package com.wxy.playerlite.feature.artist

data class ArtistDetailContent(
    val artistId: String,
    val name: String,
    val aliases: List<String>,
    val identities: List<String>,
    val avatarUrl: String?,
    val coverUrl: String?,
    val briefDesc: String,
    val encyclopediaSummary: String = "",
    val encyclopediaSections: List<ArtistEncyclopediaSection> = emptyList(),
    val musicCount: Int,
    val albumCount: Int,
    val isFollowed: Boolean? = null,
    val videoCount: Int = 0,
    val fansCount: Long = 0L
)

data class ArtistEncyclopediaContent(
    val summary: String,
    val sections: List<ArtistEncyclopediaSection>
)

data class ArtistEncyclopediaSection(
    val title: String,
    val body: String
)

data class ArtistHotSongRow(
    val trackId: String,
    val title: String,
    val artistText: String,
    val albumTitle: String,
    val coverUrl: String?,
    val durationMs: Long
)

data class ArtistAlbumPage(
    val items: List<ArtistAlbumRow>,
    val hasMore: Boolean
) {
    fun append(nextPage: ArtistAlbumPage): ArtistAlbumPage {
        return copy(
            items = items + nextPage.items,
            hasMore = nextPage.hasMore
        )
    }
}

data class ArtistAlbumRow(
    val albumId: String,
    val title: String,
    val artistText: String,
    val coverUrl: String?,
    val trackCount: Int,
    val type: String,
    val publishTimeText: String,
    val showYearOnly: Boolean = false
)

const val DEFAULT_ARTIST_ALBUM_PAGE_SIZE = 30
