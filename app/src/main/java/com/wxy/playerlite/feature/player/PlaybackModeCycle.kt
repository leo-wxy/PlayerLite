package com.wxy.playerlite.feature.player

import com.wxy.playerlite.playback.model.PlaybackMode

internal fun PlaybackMode.nextPlaybackMode(): PlaybackMode {
    return when (this) {
        PlaybackMode.LIST_LOOP -> PlaybackMode.SINGLE_LOOP
        PlaybackMode.SINGLE_LOOP -> PlaybackMode.SHUFFLE
        PlaybackMode.SHUFFLE -> PlaybackMode.LIST_LOOP
    }
}

internal fun PlaybackMode.toastText(): String {
    return when (this) {
        PlaybackMode.LIST_LOOP -> "已切换为列表循环"
        PlaybackMode.SINGLE_LOOP -> "已切换为单曲循环"
        PlaybackMode.SHUFFLE -> "已切换为随机播放"
    }
}
