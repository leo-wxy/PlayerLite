package com.wxy.playerlite.playback.process

internal fun shouldIgnorePlaybackCallback(
    callbackTrackId: String,
    currentTrackId: String?
): Boolean {
    return currentTrackId != callbackTrackId
}
