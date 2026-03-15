package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.playback.model.LocalMusicInfo
import com.wxy.playerlite.playback.model.MusicInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistItemPlayableMapperTest {
    @Test
    fun toQueuePlayableItem_mapsLocalPlaylistItemToLocalMusicInfo() {
        val item = PlaylistItem(
            id = "local-1",
            uri = "content://media/external/audio/media/1",
            displayName = "夜曲.mp3",
            title = "夜曲",
            itemType = PlaylistItemType.LOCAL
        )

        val playable = item.toQueuePlayableItem()

        assertTrue(playable is LocalMusicInfo)
        val local = playable as LocalMusicInfo
        assertEquals("local-1", local.id)
        assertEquals("夜曲", local.title)
        assertEquals("content://media/external/audio/media/1", local.playbackUri)
    }

    @Test
    fun toQueuePlayableItem_mapsOnlinePlaylistItemToSemanticMusicInfo() {
        val item = PlaylistItem(
            id = "queue-online-1",
            uri = "",
            displayName = "夜曲",
            songId = "1973665667",
            title = "夜曲",
            artistText = "周杰伦 / 杨瑞代",
            albumTitle = "十一月的萧邦",
            coverUrl = "https://example.com/night.jpg",
            durationMs = 213_000L,
            itemType = PlaylistItemType.ONLINE,
            contextType = "playlist",
            contextId = "24381616",
            contextTitle = "深夜单曲循环"
        )

        val playable = item.toQueuePlayableItem()

        assertTrue(playable is MusicInfo)
        val music = playable as MusicInfo
        assertEquals("1973665667", music.songId)
        assertEquals("夜曲", music.title)
        assertEquals(listOf("周杰伦", "杨瑞代"), music.artistNames)
        assertEquals("十一月的萧邦", music.albumTitle)
        assertEquals("https://example.com/night.jpg", music.coverUrl)
        assertEquals(213_000L, music.durationMs)
        assertEquals("playlist", music.playbackContext?.sourceType)
        assertEquals("24381616", music.playbackContext?.sourceId)
        assertEquals("深夜单曲循环", music.playbackContext?.sourceTitle)
    }
}
