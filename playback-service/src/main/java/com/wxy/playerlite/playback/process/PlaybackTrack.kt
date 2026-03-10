package com.wxy.playerlite.playback.process

import androidx.media3.common.MediaItem
import com.wxy.playerlite.playback.model.MusicInfo

internal data class PlaybackTrack(
    val music: MusicInfo
) {
    val id: String
        get() = music.id

    val uri: String
        get() = music.playbackUri

    val requestHeaders: Map<String, String>
        get() = music.requestHeaders

    val requiresAuthorization: Boolean
        get() = music.requiresAuthorization

    val displayName: String
        get() = music.title

    fun toMediaItem(statusText: String? = null): MediaItem {
        return music.toMediaItem(statusText = statusText)
    }

    companion object {
        fun fromMediaItem(mediaItem: MediaItem): PlaybackTrack? {
            val music = MusicInfo.fromMediaItem(mediaItem) ?: return null
            return PlaybackTrack(music = music)
        }
    }
}
