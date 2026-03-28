package com.wxy.playerlite.feature.player.model

import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.player.AudioEffectPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerUiStateAudioQualityTest {
    @Test
    fun currentAppliedAudioQualityDisplayName_returnsNullWhenAppliedQualityMissing() {
        val state = PlayerUiState(
            preferredAudioQuality = PlaybackAudioQuality.HIRES,
            appliedAudioQuality = null
        )

        assertEquals("Hi-Res", state.currentPreferredAudioQualityDisplayName)
        assertNull(state.currentAppliedAudioQualityDisplayName)
    }

    @Test
    fun withAudioQuality_updatesPreferredAndAppliedQuality() {
        val updated = PlayerUiState().withAudioQuality(
            preferredAudioQuality = PlaybackAudioQuality.JYMASTER,
            appliedAudioQuality = PlaybackAudioQuality.LOSSLESS
        )

        assertEquals(PlaybackAudioQuality.JYMASTER, updated.preferredAudioQuality)
        assertEquals(PlaybackAudioQuality.LOSSLESS, updated.appliedAudioQuality)
        assertEquals("超清母带", updated.currentPreferredAudioQualityDisplayName)
        assertEquals("无损", updated.currentAppliedAudioQualityDisplayName)
    }

    @Test
    fun combinedStatusUi_showsOnlyAudioQualityWhenEffectIsDefault() {
        val state = PlayerUiState(
            appliedAudioQuality = PlaybackAudioQuality.LOSSLESS,
            audioEffectPreset = AudioEffectPreset.DEFAULT
        )

        assertEquals("无损", state.combinedStatusUi?.audioQualityLabel)
        assertNull(state.combinedStatusUi?.audioEffectLabel)
    }

    @Test
    fun combinedStatusUi_showsOnlyAudioEffectWhenQualityIsUnavailable() {
        val state = PlayerUiState(
            appliedAudioQuality = null,
            audioEffectPreset = AudioEffectPreset.BRIGHT
        )

        assertNull(state.combinedStatusUi?.audioQualityLabel)
        assertEquals("清亮高频", state.combinedStatusUi?.audioEffectLabel)
    }
}
