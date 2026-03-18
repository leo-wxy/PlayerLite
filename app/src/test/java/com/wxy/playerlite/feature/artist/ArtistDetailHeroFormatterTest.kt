package com.wxy.playerlite.feature.artist

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistDetailHeroFormatterTest {
    @Test
    fun formatArtistFansCount_shouldKeepRawValueBelowWan() {
        assertEquals("0", formatArtistFansCount(0))
        assertEquals("9999", formatArtistFansCount(9_999))
    }

    @Test
    fun formatArtistFansCount_shouldConvertToWanAboveThreshold() {
        assertEquals("1w", formatArtistFansCount(10_000))
        assertEquals("1.6w", formatArtistFansCount(15_580))
        assertEquals("1326.8w", formatArtistFansCount(13_267_753))
    }
}
