package com.wxy.playerlite.feature.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTrackTextResolverTest {
    @Test
    fun artistDashTitleFileName_shouldSplitIntoArtistAndTitle() {
        val text = resolvePlayerTrackText("周杰伦 - 夜曲.mp3")

        assertEquals("夜曲", text.title)
        assertEquals("周杰伦", text.artist)
    }

    @Test
    fun plainFileName_shouldFallbackToLocalAudioArtist() {
        val text = resolvePlayerTrackText("夜曲.flac")

        assertEquals("夜曲", text.title)
        assertEquals("本地音频", text.artist)
    }
}
