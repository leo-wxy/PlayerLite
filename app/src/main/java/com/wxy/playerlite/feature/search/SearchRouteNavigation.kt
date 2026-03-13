package com.wxy.playerlite.feature.search

import android.content.Context
import android.content.Intent
import com.wxy.playerlite.feature.album.AlbumDetailActivity
import com.wxy.playerlite.feature.artist.ArtistDetailActivity
import com.wxy.playerlite.feature.playlist.PlaylistDetailActivity

internal fun searchRouteIntent(
    context: Context,
    target: SearchRouteTarget
): Intent? {
    return when (target) {
        is SearchRouteTarget.Artist -> ArtistDetailActivity.createIntent(
            context = context,
            artistId = target.artistId
        )

        is SearchRouteTarget.Playlist -> PlaylistDetailActivity.createIntent(
            context = context,
            playlistId = target.playlistId
        )

        is SearchRouteTarget.Album -> AlbumDetailActivity.createIntent(
            context = context,
            albumId = target.albumId
        )

        else -> null
    }
}
