package com.wxy.playerlite.playback.process

internal object PlaybackFormatter {
    fun formatPlaybackResult(playCode: Int, lastError: String): String {
        return when (playCode) {
            0 -> "Playback finished"
            -2001 -> "Stopped"
            -2005 -> "Playback already in progress"
            -2006 -> "Seek is available only while playback is active"
            else -> "Playback failed($playCode): $lastError"
        }
    }
}
