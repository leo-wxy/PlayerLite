package com.wxy.playerlite.feature.song

import android.content.Context
import android.content.Intent
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.album.AlbumDetailActivity
import com.wxy.playerlite.feature.artist.ArtistDetailActivity

internal fun PlaylistItem.toSongRefOrNull(): SongRef? {
    songId?.takeIf { it.isNotBlank() }?.let { onlineSongId ->
        return SongRef.Online(songId = onlineSongId)
    }
    val playbackUri = uri.takeIf { it.isNotBlank() } ?: return null
    return SongRef.Local(
        playbackUri = playbackUri,
        title = effectiveTitle,
        artistText = artistText.orEmpty(),
        albumTitle = albumTitle,
        durationMs = durationMs,
        coverUrl = coverUrl
    )
}

internal fun PlaylistItem.createSongDetailIntent(context: Context): Intent? {
    val ref = toSongRefOrNull() ?: return null
    return SongDetailActivity.createIntent(context = context, ref = ref)
}

internal fun PlaylistItem.createArtistDetailIntent(context: Context): Intent? {
    val artistId = primaryArtistId?.takeIf { it.isNotBlank() } ?: return null
    return ArtistDetailActivity.createIntent(context = context, artistId = artistId)
}

internal fun PlaylistItem.createAlbumDetailIntent(context: Context): Intent? {
    val targetAlbumId = albumId?.takeIf { it.isNotBlank() } ?: return null
    return AlbumDetailActivity.createIntent(context = context, albumId = targetAlbumId)
}
