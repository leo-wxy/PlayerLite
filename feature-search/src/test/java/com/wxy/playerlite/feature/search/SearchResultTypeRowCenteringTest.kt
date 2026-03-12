package com.wxy.playerlite.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultTypeRowCenteringTest {
    @Test
    fun calculateTypeRowCenterScrollDelta_shouldReturnPositiveValueWhenItemIsOnRightSide() {
        assertEquals(
            100f,
            calculateTypeRowCenterScrollDelta(
                viewportStartOffset = 0,
                viewportEndOffset = 300,
                itemOffset = 220,
                itemSize = 60
            ),
            0.001f
        )
    }

    @Test
    fun calculateTypeRowCenterScrollDelta_shouldReturnZeroWhenItemIsAlreadyCentered() {
        assertEquals(
            0f,
            calculateTypeRowCenterScrollDelta(
                viewportStartOffset = 0,
                viewportEndOffset = 300,
                itemOffset = 120,
                itemSize = 60
            ),
            0.001f
        )
    }

    @Test
    fun calculateTypeRowCenterScrollDelta_shouldReturnNegativeValueWhenItemIsOnLeftSide() {
        assertEquals(
            -110f,
            calculateTypeRowCenterScrollDelta(
                viewportStartOffset = 0,
                viewportEndOffset = 300,
                itemOffset = 10,
                itemSize = 60
            ),
            0.001f
        )
    }
}
