package com.wxy.playerlite.playback.orchestrator

import com.wxy.playerlite.player.AudioEffectPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioEffectPresetSyncResolverTest {
    @Test
    fun onLocalRequest_keepsRequestedPresetPendingUntilRemoteConfirms() {
        val result = AudioEffectPresetSyncResolver.onLocalRequest(AudioEffectPreset.WARM)

        assertEquals(AudioEffectPreset.WARM, result.resolvedPreset)
        assertEquals(AudioEffectPreset.WARM, result.pendingPreset)
    }

    @Test
    fun onRemoteUpdate_clearsPendingPresetWhenRemoteMatches() {
        val result = AudioEffectPresetSyncResolver.onRemoteUpdate(
            remotePreset = AudioEffectPreset.WARM,
            pendingPreset = AudioEffectPreset.WARM
        )

        assertEquals(AudioEffectPreset.WARM, result.resolvedPreset)
        assertNull(result.pendingPreset)
    }

    @Test
    fun onCommandRejected_revertsToFallbackAndClearsPending() {
        val result = AudioEffectPresetSyncResolver.onCommandRejected(AudioEffectPreset.BRIGHT)

        assertEquals(AudioEffectPreset.BRIGHT, result.resolvedPreset)
        assertNull(result.pendingPreset)
    }
}
