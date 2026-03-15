package com.wxy.playerlite.playback.model

import androidx.media3.common.MediaItem

interface PlayableItem {
    val id: String
    val songId: String?
    val title: String
    val artistText: String?
    val albumTitle: String?
    val coverUrl: String?
    val durationMs: Long
    val playbackUri: String
    val playbackContext: PlaybackContext?
    val previewClip: PlaybackPreviewClip?
    val requestHeaders: Map<String, String>
    val requiresAuthorization: Boolean

    fun toPlayableItem(): PlayableItemSnapshot

    fun toMediaItem(statusText: String? = null): MediaItem {
        return toPlayableItem().toMediaItem(statusText = statusText)
    }
}
