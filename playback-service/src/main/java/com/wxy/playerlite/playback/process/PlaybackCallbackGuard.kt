package com.wxy.playerlite.playback.process

internal fun shouldIgnorePlaybackCallback(
    callbackTrackId: String,
    currentTrackId: String?,
    callbackGeneration: Long,
    currentGeneration: Long
): Boolean {
    return currentTrackId != callbackTrackId || callbackGeneration != currentGeneration
}
