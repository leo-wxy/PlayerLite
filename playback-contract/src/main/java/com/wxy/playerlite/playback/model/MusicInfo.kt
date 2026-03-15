package com.wxy.playerlite.playback.model

import androidx.media3.common.MediaItem

data class MusicInfo(
    override val id: String,
    override val songId: String? = null,
    override val title: String,
    val artistNames: List<String> = emptyList(),
    val artistIds: List<String> = emptyList(),
    override val albumTitle: String? = null,
    override val coverUrl: String? = null,
    override val durationMs: Long = 0L,
    override val playbackUri: String,
    override val playbackContext: PlaybackContext? = null,
    override val previewClip: PlaybackPreviewClip? = null,
    override val requestHeaders: Map<String, String> = emptyMap(),
    override val requiresAuthorization: Boolean = false
) : PlayableItem {
    override val artistText: String?
        get() = artistNames
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(separator = " / ")
            .takeIf { it.isNotBlank() }

    override fun toPlayableItem(): PlayableItemSnapshot {
        return PlayableItemSnapshot(
            id = id,
            songId = songId,
            title = title,
            artistText = artistText,
            albumTitle = albumTitle,
            coverUrl = coverUrl,
            durationMs = durationMs,
            playbackUri = playbackUri,
            playbackContext = playbackContext,
            previewClip = previewClip,
            requestHeaders = requestHeaders,
            requiresAuthorization = requiresAuthorization
        )
    }

    override fun toMediaItem(statusText: String?): MediaItem {
        return toPlayableItem().toMediaItem(statusText = statusText)
    }

    companion object {
        fun fromMediaItem(mediaItem: MediaItem): MusicInfo? {
            val playable = PlayableItemSnapshot.fromMediaItem(mediaItem) ?: return null
            return MusicInfo(
                id = playable.id,
                songId = playable.songId,
                title = playable.title,
                artistNames = playable.artistText
                    ?.split("/")
                    ?.map(String::trim)
                    ?.filter(String::isNotEmpty)
                    .orEmpty(),
                albumTitle = playable.albumTitle,
                coverUrl = playable.coverUrl,
                durationMs = playable.durationMs,
                playbackUri = playable.playbackUri,
                playbackContext = playable.playbackContext,
                previewClip = playable.previewClip,
                requestHeaders = playable.requestHeaders,
                requiresAuthorization = playable.requiresAuthorization
            )
        }
    }
}
