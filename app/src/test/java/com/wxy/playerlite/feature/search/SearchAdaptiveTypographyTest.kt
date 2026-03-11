package com.wxy.playerlite.feature.search

import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAdaptiveTypographyTest {
    @Test
    fun usesExpandedSearchTypography_shouldAlwaysBeEnabled() {
        assertTrue(usesExpandedSearchTypography())
    }
}
