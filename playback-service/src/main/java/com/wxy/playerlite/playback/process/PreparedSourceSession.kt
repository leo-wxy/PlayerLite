package com.wxy.playerlite.playback.process

import com.wxy.playerlite.player.source.IPlaysource

internal class PreparedSourceSession {
    private var selectedSource: IPlaysource? = null
    private var preparedItemId: String? = null
    private var playbackStartedForPreparedSource: Boolean = false

    fun currentSource(): IPlaysource? {
        return selectedSource
    }

    fun isPreparedFor(itemId: String): Boolean {
        return selectedSource != null && preparedItemId == itemId
    }

    fun markPrepared(itemId: String, source: IPlaysource) {
        selectedSource = source
        preparedItemId = itemId
        playbackStartedForPreparedSource = false
    }

    fun shouldRewindBeforePlayback(itemId: String): Boolean {
        return isPreparedFor(itemId) && playbackStartedForPreparedSource
    }

    fun markPlaybackStarting(itemId: String) {
        if (isPreparedFor(itemId)) {
            playbackStartedForPreparedSource = true
        }
    }

    fun stopCurrent() {
        selectedSource?.stop()
    }

    fun release() {
        selectedSource?.abort()
        selectedSource?.close()
        selectedSource = null
        preparedItemId = null
        playbackStartedForPreparedSource = false
    }
}
