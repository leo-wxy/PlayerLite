package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.player.AudioEffectPreset

internal data class AudioEffectPresetSyncResult(
    val resolvedPreset: AudioEffectPreset,
    val pendingPreset: AudioEffectPreset?
)

internal object AudioEffectPresetSyncResolver {
    fun onLocalRequest(
        requestedPreset: AudioEffectPreset
    ): AudioEffectPresetSyncResult {
        return AudioEffectPresetSyncResult(
            resolvedPreset = requestedPreset,
            pendingPreset = requestedPreset
        )
    }

    fun onRemoteUpdate(
        remotePreset: AudioEffectPreset,
        pendingPreset: AudioEffectPreset?
    ): AudioEffectPresetSyncResult {
        if (pendingPreset == null) {
            return AudioEffectPresetSyncResult(
                resolvedPreset = remotePreset,
                pendingPreset = null
            )
        }
        if (remotePreset == pendingPreset) {
            return AudioEffectPresetSyncResult(
                resolvedPreset = remotePreset,
                pendingPreset = null
            )
        }
        return AudioEffectPresetSyncResult(
            resolvedPreset = pendingPreset,
            pendingPreset = pendingPreset
        )
    }

    fun onCommandRejected(
        fallbackPreset: AudioEffectPreset
    ): AudioEffectPresetSyncResult {
        return AudioEffectPresetSyncResult(
            resolvedPreset = fallbackPreset,
            pendingPreset = null
        )
    }
}
