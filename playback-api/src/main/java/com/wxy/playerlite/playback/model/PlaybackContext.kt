package com.wxy.playerlite.playback.model

data class PlaybackContext(
    val sourceType: String,
    val sourceId: String? = null,
    val sourceTitle: String? = null
)
