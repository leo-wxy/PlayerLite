package com.wxy.playerlite.playback.orchestrator

import com.wxy.playerlite.player.PlaybackSpeed

data class PlaybackSpeedSyncResult(
    val resolvedSpeed: Float,
    val pendingSpeed: Float?
)

object PlaybackSpeedSyncResolver {
    fun onLocalRequest(
        requestedSpeed: Float
    ): PlaybackSpeedSyncResult {
        val normalizedSpeed = PlaybackSpeed.normalizeValue(requestedSpeed)
        return PlaybackSpeedSyncResult(
            resolvedSpeed = normalizedSpeed,
            pendingSpeed = normalizedSpeed
        )
    }

    fun onRemoteUpdate(
        remoteSpeed: Float,
        pendingSpeed: Float?
    ): PlaybackSpeedSyncResult {
        val normalizedRemoteSpeed = PlaybackSpeed.normalizeValue(remoteSpeed)
        val normalizedPendingSpeed = pendingSpeed?.let(PlaybackSpeed::normalizeValue)
        if (normalizedPendingSpeed == null) {
            return PlaybackSpeedSyncResult(
                resolvedSpeed = normalizedRemoteSpeed,
                pendingSpeed = null
            )
        }
        if (normalizedRemoteSpeed == normalizedPendingSpeed) {
            return PlaybackSpeedSyncResult(
                resolvedSpeed = normalizedRemoteSpeed,
                pendingSpeed = null
            )
        }
        return PlaybackSpeedSyncResult(
            resolvedSpeed = normalizedPendingSpeed,
            pendingSpeed = normalizedPendingSpeed
        )
    }

    fun onCommandRejected(
        fallbackSpeed: Float
    ): PlaybackSpeedSyncResult {
        val normalizedSpeed = PlaybackSpeed.normalizeValue(fallbackSpeed)
        return PlaybackSpeedSyncResult(
            resolvedSpeed = normalizedSpeed,
            pendingSpeed = null
        )
    }
}
