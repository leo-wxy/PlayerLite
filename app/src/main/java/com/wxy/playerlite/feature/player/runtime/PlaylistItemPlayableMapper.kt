package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.playback.model.LocalMusicInfo
import com.wxy.playerlite.playback.model.MusicInfo
import com.wxy.playerlite.playback.model.PlayableItem
import com.wxy.playerlite.playback.model.PlaybackContext

internal fun PlaylistItem.toQueuePlayableItem(): PlayableItem? {
    return if (isOnline) {
        val resolvedSongId = songId?.takeIf { it.isNotBlank() } ?: return null
        MusicInfo(
            id = id,
            songId = resolvedSongId,
            title = effectiveTitle,
            artistNames = artistText
                ?.split("/")
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                .orEmpty(),
            albumTitle = albumTitle,
            coverUrl = coverUrl,
            durationMs = durationMs,
            playbackUri = uri,
            playbackContext = contextType
                ?.takeIf { it.isNotBlank() }
                ?.let { sourceType ->
                    PlaybackContext(
                        sourceType = sourceType,
                        sourceId = contextId,
                        sourceTitle = contextTitle
                    )
                }
        )
    } else {
        uri.takeIf { it.isNotBlank() }?.let { playbackUri ->
            LocalMusicInfo(
                id = id,
                songId = songId,
                title = effectiveTitle,
                artistText = artistText,
                albumTitle = albumTitle,
                coverUrl = coverUrl,
                durationMs = durationMs,
                playbackUri = playbackUri
            )
        }
    }
}
