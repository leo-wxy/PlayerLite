package com.wxy.playerlite.playback.process

internal enum class PlaybackCompletionAction {
    AUTO_NEXT,
    STOP_AT_END,
    STOP_WITH_ERROR;

    companion object {
        fun resolve(
            playCode: Int,
            activeIndex: Int,
            trackCount: Int
        ): PlaybackCompletionAction {
            if (playCode != 0) {
                return STOP_WITH_ERROR
            }
            val hasNextTrack = activeIndex >= 0 && activeIndex < trackCount - 1
            return if (hasNextTrack) {
                AUTO_NEXT
            } else {
                STOP_AT_END
            }
        }
    }
}
