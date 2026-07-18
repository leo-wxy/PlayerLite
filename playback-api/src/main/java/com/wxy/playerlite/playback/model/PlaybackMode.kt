package com.wxy.playerlite.playback.model

enum class PlaybackMode(val wireValue: String) {
    LIST_LOOP("list_loop"),
    SINGLE_LOOP("single_loop"),
    SHUFFLE("shuffle");

    companion object {
        fun fromWireValue(value: String?): PlaybackMode {
            return when (value) {
                null,
                "",
                "sequential" -> LIST_LOOP
                else -> entries.firstOrNull { it.wireValue == value } ?: LIST_LOOP
            }
        }
    }
}
