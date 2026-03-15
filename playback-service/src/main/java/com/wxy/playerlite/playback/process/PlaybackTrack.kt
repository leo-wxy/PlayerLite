package com.wxy.playerlite.playback.process

import androidx.media3.common.MediaItem
import com.wxy.playerlite.playback.model.PlayableItem
import com.wxy.playerlite.playback.model.PlaybackPreviewClip
import com.wxy.playerlite.playback.model.PlayableItemSnapshot

internal data class PlaybackTrack(
    val playable: PlayableItem
) {
    val id: String
        get() = playable.id

    val uri: String
        get() = playable.playbackUri

    val requestHeaders: Map<String, String>
        get() = playable.requestHeaders

    val songId: String?
        get() = playable.songId

    val durationHintMs: Long
        get() = playable.durationMs

    val previewClip: PlaybackPreviewClip?
        get() = playable.previewClip

    val requiresAuthorization: Boolean
        get() = playable.requiresAuthorization

    val displayName: String
        get() = playable.title

    fun toMediaItem(statusText: String? = null): MediaItem {
        return playable.toMediaItem(statusText = statusText)
    }

    companion object {
        fun fromMediaItem(mediaItem: MediaItem): PlaybackTrack? {
            val playable = PlayableItemSnapshot.fromMediaItem(mediaItem) ?: return null
            return PlaybackTrack(playable = playable)
        }
    }
}
