package com.wxy.playerlite.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricDisplayProjectionTest {
    @Test
    fun resolveActiveLyricLineIndex_shouldAdvanceAtCompensatedLineBoundary() {
        val lines = listOf(
            LyricLine(timestampMs = 1_000L, text = "第一句"),
            LyricLine(timestampMs = 3_000L, text = "第二句")
        )

        assertEquals(0, resolveActiveLyricLineIndex(lines, currentPositionMs = 2_499L))
        assertEquals(1, resolveActiveLyricLineIndex(lines, currentPositionMs = 2_500L))
    }
}
