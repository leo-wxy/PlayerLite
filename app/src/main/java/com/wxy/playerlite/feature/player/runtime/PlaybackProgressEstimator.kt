package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.player.PlaybackSpeed
import kotlin.math.roundToLong

internal object PlaybackProgressEstimator {
    fun estimatePositionMs(
        anchorPositionMs: Long,
        anchorElapsedRealtimeMs: Long,
        nowElapsedRealtimeMs: Long,
        playbackState: Int,
        playbackSpeed: Float,
        durationMs: Long
    ): Long {
        val boundedAnchor = boundPosition(anchorPositionMs, durationMs)
        if (playbackState != AUDIO_TRACK_PLAYSTATE_PLAYING) {
            return boundedAnchor
        }

        val elapsedRealtimeMs = (nowElapsedRealtimeMs - anchorElapsedRealtimeMs).coerceAtLeast(0L)
        val advancedMs = (elapsedRealtimeMs * PlaybackSpeed.normalizeValue(playbackSpeed)).roundToLong()
        return boundPosition(boundedAnchor + advancedMs, durationMs)
    }

    private fun boundPosition(positionMs: Long, durationMs: Long): Long {
        return if (durationMs > 0L) {
            positionMs.coerceIn(0L, durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }
    }
}
