package com.wxy.playerlite.feature.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerCoverCardTransformTest {

    @Test
    fun resolvePlayerCoverCardTransform_shouldStayStaticEvenWhenPlaying() {
        val playing = resolvePlayerCoverCardTransform(isPlaying = true, isPaused = false)
        val paused = resolvePlayerCoverCardTransform(isPlaying = false, isPaused = true)

        assertEquals(1f, playing.scale)
        assertEquals(0f, playing.translationY)
        assertEquals(1f, paused.scale)
        assertEquals(0f, paused.translationY)
    }
}
