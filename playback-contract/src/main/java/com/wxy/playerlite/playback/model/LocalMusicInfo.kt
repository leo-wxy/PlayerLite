package com.wxy.playerlite.playback.model

data class LocalMusicInfo(
    override val id: String,
    override val title: String,
    override val playbackUri: String,
    override val artistText: String? = null,
    override val albumTitle: String? = null,
    override val coverUrl: String? = null,
    override val durationMs: Long = 0L,
    override val songId: String? = null,
    override val playbackContext: PlaybackContext? = null,
    override val previewClip: PlaybackPreviewClip? = null,
    override val requestHeaders: Map<String, String> = emptyMap(),
    override val requiresAuthorization: Boolean = false
) : PlayableItem {
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
}
