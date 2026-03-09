package com.wxy.playerlite.feature.player.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Autorenew
import com.wxy.playerlite.playback.model.PlaybackMode
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModeVisualsTest {
    @Test
    fun listLoop_usesLoopSpecificIcon() {
        assertEquals(
            Icons.Rounded.Autorenew,
            PlaybackMode.LIST_LOOP.modeIcon()
        )
        assertNotEquals(
            Icons.AutoMirrored.Rounded.PlaylistPlay,
            PlaybackMode.LIST_LOOP.modeIcon()
        )
    }

    @Test
    fun modes_useDistinctAccentPalettes() {
        val listLoop = PlaybackMode.LIST_LOOP.modePalette()
        val singleLoop = PlaybackMode.SINGLE_LOOP.modePalette()
        val shuffle = PlaybackMode.SHUFFLE.modePalette()

        assertNotEquals(listLoop.containerColor, singleLoop.containerColor)
        assertNotEquals(listLoop.containerColor, shuffle.containerColor)
        assertNotEquals(singleLoop.containerColor, shuffle.containerColor)
    }

    @Test
    fun shuffleMode_enablesAnimatedEmphasis() {
        assertTrue(PlaybackMode.SHUFFLE.modeMotionSpec().animated)
        assertFalse(PlaybackMode.SHUFFLE.modeMotionSpec().repeat)
        assertFalse(PlaybackMode.LIST_LOOP.modeMotionSpec().animated)
        assertFalse(PlaybackMode.SINGLE_LOOP.modeMotionSpec().animated)
    }
}
