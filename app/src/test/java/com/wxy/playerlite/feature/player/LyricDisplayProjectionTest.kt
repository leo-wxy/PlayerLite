package com.wxy.playerlite.feature.player

import com.wxy.playerlite.feature.player.model.PlayerLyricUiState
import com.wxy.playerlite.feature.player.model.PlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricDisplayProjectionTest {
    @Test
    fun resolvePlayerDisplayContentProjection_shouldPreferCurrentLyricForMiniPlayerLine() {
        val projection = resolvePlayerDisplayContentProjection(
            playerState = PlayerUiState(
                hasSelection = true,
                currentTrackTitle = "夜曲",
                currentTrackArtist = "周杰伦",
                lyricUiState = PlayerLyricUiState.Content(
                    lyrics = ParsedLyrics(
                        songId = "track-1",
                        lines = listOf(
                            LyricLine(timestampMs = 1_000L, text = "第一句"),
                            LyricLine(timestampMs = 3_000L, text = "第二句")
                        ),
                        rawText = "[00:01.00]第一句\n[00:03.00]第二句"
                    )
                ),
                seekPositionMs = 3_500L
            )
        )

        assertEquals("第二句", projection.miniPlayerContentLine)
        assertEquals("第二句", projection.displayMetadataTitle)
        assertEquals("夜曲 - 周杰伦", projection.songArtistLine)
    }

    @Test
    fun resolvePlayerDisplayContentProjection_shouldShareFallbackRuleAcrossMiniPlayerAndMetadata() {
        val projection = resolvePlayerDisplayContentProjection(
            playerState = PlayerUiState(
                hasSelection = true,
                selectedFileName = "陈奕迅 - 好久不见.mp3"
            )
        )

        assertEquals("好久不见", projection.resolvedTitle)
        assertEquals("陈奕迅", projection.resolvedArtist)
        assertEquals("好久不见 - 陈奕迅", projection.songArtistLine)
        assertEquals("好久不见 - 陈奕迅", projection.miniPlayerContentLine)
        assertEquals("好久不见", projection.displayMetadataTitle)
    }
}
