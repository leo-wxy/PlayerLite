package com.wxy.playerlite.feature.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerScreenMotionSpecTest {
    @Test
    fun disabledEnterMotion_shouldRenderImmediately() {
        val motion = PlayerScreenMotionSpec.resolve(enableEnterMotion = false, hasRevealed = false)

        assertEquals(1f, motion.alpha)
        assertEquals(0, motion.offsetDp)
    }

    @Test
    fun enabledEnterMotion_beforeReveal_shouldStartSlightlyOffsetAndTransparent() {
        val motion = PlayerScreenMotionSpec.resolve(enableEnterMotion = true, hasRevealed = false)

        assertEquals(0f, motion.alpha)
        assertEquals(12, motion.offsetDp)
    }
}
