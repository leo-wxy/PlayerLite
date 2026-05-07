package com.wxy.playerlite.playback.model

enum class PlaybackPrewarmTargetType(
    val wireValue: String
) {
    CURRENT_AHEAD("current_ahead"),
    NEXT_TRACK("next_track");

    companion object {
        fun fromWireValue(value: String?): PlaybackPrewarmTargetType {
            return entries.firstOrNull { it.wireValue == value } ?: NEXT_TRACK
        }
    }
}

enum class PlaybackPrewarmState(
    val wireValue: String
) {
    IDLE("idle"),
    RESOLVING("resolving"),
    RUNNING("running"),
    READY("ready"),
    COMPLETED("completed"),
    SKIPPED("skipped"),
    FAILED("failed"),
    CANCELED("canceled");

    companion object {
        fun fromWireValue(value: String?): PlaybackPrewarmState {
            return entries.firstOrNull { it.wireValue == value } ?: IDLE
        }
    }
}

data class PlaybackPrewarmSnapshot(
    val targetId: String?,
    val targetType: PlaybackPrewarmTargetType,
    val state: PlaybackPrewarmState,
    val cachedBytes: Long = 0L,
    val targetBytes: Long? = null,
    val cachedDurationMs: Long? = null,
    val targetDurationMs: Long? = null,
    val isReady: Boolean = false,
    val isCompleted: Boolean = false,
    val reason: String? = null
)
