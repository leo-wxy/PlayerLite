package com.wxy.playerlite.feature.main

import com.wxy.playerlite.feature.home.HomeContentTarget
import com.wxy.playerlite.feature.search.SearchRouteTarget

internal fun HomeContentTarget.toContentEntryAction(): ContentEntryAction {
    return when (this) {
        HomeContentTarget.DailyRecommendedSongs -> ContentEntryAction.OpenDailyRecommendedSongs
        is HomeContentTarget.Song -> ContentEntryAction.OpenDetail(
            SearchRouteTarget.Song(songId = songId)
        )

        is HomeContentTarget.Artist -> ContentEntryAction.OpenDetail(
            SearchRouteTarget.Artist(artistId = artistId)
        )

        is HomeContentTarget.Playlist -> ContentEntryAction.OpenDetail(
            SearchRouteTarget.Playlist(playlistId = playlistId)
        )

        is HomeContentTarget.Album -> ContentEntryAction.OpenDetail(
            SearchRouteTarget.Album(albumId = albumId)
        )

        is HomeContentTarget.ExternalUri -> ContentEntryAction.OpenUri(
            uri = uri,
            fallbackMessage = fallbackMessage
        )

        is HomeContentTarget.Unsupported -> ContentEntryAction.Unsupported(message = message)
    }
}
