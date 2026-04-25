package com.wxy.playerlite.playback.process

import com.wxy.playerlite.playback.model.PlaybackCacheProgressSnapshot

internal interface PlaybackCacheProgressEmitter {
    fun setCacheProgressListener(listener: ((PlaybackCacheProgressSnapshot?) -> Unit)?)

    fun onPlaybackSeekPositionChanged(positionMs: Long, durationMs: Long) = Unit
}
