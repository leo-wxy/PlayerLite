package com.wxy.playerlite.playback.process

import com.wxy.playerlite.playback.model.PlaybackMode

internal enum class PlaybackCompletionAction {
    AUTO_NEXT,
    LOOP_TO_FIRST,
    REPEAT_CURRENT,
    STOP_AT_END,
    STOP_WITH_ERROR;

    companion object {
        fun resolve(
            playCode: Int,
            activeIndex: Int,
            trackCount: Int,
            playbackMode: PlaybackMode
        ): PlaybackCompletionAction {
            if (playCode != 0) {
                return STOP_WITH_ERROR
            }
            if (trackCount <= 0) {
                return STOP_AT_END
            }
            if (playbackMode == PlaybackMode.SINGLE_LOOP) {
                return REPEAT_CURRENT
            }
            val hasNextTrack = activeIndex >= 0 && activeIndex < trackCount - 1
            return when {
                hasNextTrack -> AUTO_NEXT
                playbackMode == PlaybackMode.LIST_LOOP -> LOOP_TO_FIRST
                else -> STOP_AT_END
            }
        }
    }
}
