package com.wxy.playerlite.feature.search

import android.content.Context
import com.wxy.playerlite.core.playback.AppPlaybackGraph
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.feature.player.PlayerActivity
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackRequest

internal fun buildSearchPlaybackRequest(
    songs: List<SearchResultUiModel.Song>,
    activeSongId: String
): DetailPlaybackRequest? {
    if (songs.isEmpty()) {
        return null
    }
    val activeIndex = songs.indexOfFirst { it.id == activeSongId }
    if (activeIndex < 0) {
        return null
    }
    return DetailPlaybackRequest(
        items = songs.mapIndexed { index, song ->
            song.toSearchPlaylistItem(queueIndex = index)
        },
        activeIndex = activeIndex
    )
}

internal fun playSearchSongs(
    context: Context,
    songs: List<SearchResultUiModel.Song>,
    activeSongId: String
): Boolean {
    val request = buildSearchPlaybackRequest(
        songs = songs,
        activeSongId = activeSongId
    ) ?: return false
    val played = AppPlaybackGraph.detailPlaybackGateway(context).play(request)
    if (played) {
        context.startActivity(
            PlayerActivity.createIntent(
                context = context,
                startPlayback = true
            )
        )
    }
    return played
}

internal fun SearchResultUiModel.Song.toSearchPlaylistItem(queueIndex: Int): PlaylistItem {
    return PlaylistItem(
        id = "search:$id:$queueIndex",
        displayName = title,
        songId = id,
        title = title,
        artistText = artistText,
        primaryArtistId = primaryArtistId,
        albumId = albumId,
        albumTitle = albumTitle,
        coverUrl = coverUrl,
        durationMs = durationMs,
        itemType = PlaylistItemType.ONLINE,
        contextType = "search_song_result",
        contextId = id,
        contextTitle = title
    )
}
