package com.wxy.playerlite.playback.model

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class MusicInfo(
    val id: String,
    val title: String,
    val playbackUri: String
) {
    fun toMediaItem(statusText: String? = null): MediaItem {
        val normalizedTitle = title.ifBlank { "Unknown audio" }
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(normalizedTitle)
            .setExtras(
                Bundle().apply {
                    putString(EXTRA_PLAYBACK_URI, playbackUri)
                }
            )
        if (!statusText.isNullOrBlank()) {
            metadataBuilder.setSubtitle(statusText)
        }

        return MediaItem.Builder()
            .setMediaId(id.ifBlank { playbackUri })
            .setUri(playbackUri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    companion object {
        private const val EXTRA_PLAYBACK_URI = "playback_uri"

        fun fromMediaItem(mediaItem: MediaItem): MusicInfo? {
            val uri = mediaItem.mediaMetadata.extras?.getString(EXTRA_PLAYBACK_URI)
                ?: mediaItem.localConfiguration?.uri?.toString()
                ?: return null

            val id = mediaItem.mediaId.takeIf { it.isNotBlank() } ?: uri
            val title = mediaItem.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown audio"
            return MusicInfo(
                id = id,
                title = title,
                playbackUri = uri
            )
        }
    }
}
