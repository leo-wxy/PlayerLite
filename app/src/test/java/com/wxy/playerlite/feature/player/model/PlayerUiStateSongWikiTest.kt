package com.wxy.playerlite.feature.player.model

import com.wxy.playerlite.core.playlist.PlaylistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerUiStateSongWikiTest {
    @Test
    fun currentSongId_returnsActiveOnlineSongId() {
        val state = PlayerUiState(
            playlistItems = listOf(
                PlaylistItem(
                    id = "1",
                    uri = "https://example.com/song.mp3",
                    displayName = "在线歌曲",
                    songId = "1973665667"
                )
            ),
            activePlaylistIndex = 0
        )

        assertEquals("1973665667", state.currentSongId)
    }

    @Test
    fun currentSongId_returnsNullForLocalAudio() {
        val state = PlayerUiState(
            playlistItems = listOf(
                PlaylistItem(
                    id = "local-1",
                    uri = "file:///local.mp3",
                    displayName = "本地音频"
                )
            ),
            activePlaylistIndex = 0
        )

        assertNull(state.currentSongId)
    }

    @Test
    fun contentState_shouldRetainStructuredSummary() {
        val summary = demoSongWikiSummary()

        val state = PlayerUiState(
            songWikiUiState = PlayerSongWikiUiState.Content(summary)
        )

        val content = state.songWikiUiState as PlayerSongWikiUiState.Content
        assertEquals("音乐百科", content.summary.title)
        assertEquals("参与共建", content.summary.contributionText)
        assertEquals(3, content.summary.sections.size)
    }
}
