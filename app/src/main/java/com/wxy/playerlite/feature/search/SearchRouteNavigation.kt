package com.wxy.playerlite.feature.search

import android.content.Context
import android.content.Intent
import com.wxy.playerlite.feature.album.AlbumDetailActivity
import com.wxy.playerlite.feature.artist.ArtistDetailActivity
import com.wxy.playerlite.feature.playlist.PlaylistDetailActivity
import com.wxy.playerlite.feature.song.SongDetailActivity

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

        is SearchRouteTarget.Song -> SongDetailActivity.createOnlineIntent(
            context = context,
            songId = target.songId
        )

        else -> null
    }
}
