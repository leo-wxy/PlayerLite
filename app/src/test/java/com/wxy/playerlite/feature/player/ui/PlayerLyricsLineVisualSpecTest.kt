package com.wxy.playerlite.feature.player.ui

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLyricsLineVisualSpecTest {
    @Test
    fun activeLineVisuals_shouldUseGlowEmphasisWithoutAggressiveScaling() {
        val inactive = resolvePlayerLyricsLineVisuals(isActiveLine = false)
        val active = resolvePlayerLyricsLineVisuals(isActiveLine = true)

        assertEquals(1.0f, inactive.scale, 0f)
        assertTrue(active.scale > inactive.scale)
        assertTrue(active.scale <= 1.02f)
        assertEquals(FontWeight.SemiBold, active.fontWeight)
        assertTrue(active.inactiveAlphaFloor <= 0.34f)
        assertTrue(active.glowAlpha >= 0.2f)
    }
}
