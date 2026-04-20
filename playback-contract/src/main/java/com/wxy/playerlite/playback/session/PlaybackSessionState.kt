package com.wxy.playerlite.playback.session

data class PlaybackSessionState(
    val activeItemId: String,
    val positionMs: Long,
    val playWhenReady: Boolean,
    val savedAtMs: Long
)
