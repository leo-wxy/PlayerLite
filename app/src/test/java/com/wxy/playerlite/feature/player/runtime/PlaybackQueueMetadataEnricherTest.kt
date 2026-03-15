package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.core.playback.SongDetailRepository
import com.wxy.playerlite.playback.model.MusicInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackQueueMetadataEnricherTest {
    @Test
    fun enrich_shouldPrioritizeActiveSongThenPageRemainingItems() = runTest {
        val repository = FakeSongDetailRepository(
            responses = mapOf(
                "song-1" to musicInfo(
                    songId = "song-1",
                    title = "第一首完整版",
                    primaryArtistId = "artist-1",
                    coverUrl = "https://example.com/song-1.jpg",
                    durationMs = 180_000L
                ),
                "song-2" to musicInfo(
                    songId = "song-2",
                    title = "第二首完整版",
                    primaryArtistId = "artist-2",
                    coverUrl = "https://example.com/song-2.jpg",
                    durationMs = 200_000L
                ),
                "song-3" to musicInfo(
                    songId = "song-3",
                    title = "第三首完整版",
                    primaryArtistId = "artist-3",
                    coverUrl = "https://example.com/song-3.jpg",
                    durationMs = 220_000L
                )
            )
        )
        val enricher = PlaybackQueueMetadataEnricher(
            repository = repository,
            pageSize = 2
        )
        val pages = mutableListOf<Map<String, PlaylistItem>>()

        enricher.enrich(
            items = listOf(
                onlineItem(id = "item-1", songId = "song-1", title = "第一首"),
                onlineItem(id = "item-2", songId = "song-2", title = "第二首"),
                onlineItem(id = "item-3", songId = "song-3", title = "第三首")
            ),
            activeIndex = 2,
            onPageResolved = { page -> pages.add(page) }
        )

        assertEquals(
            listOf(
                listOf("song-3"),
                listOf("song-1", "song-2")
            ),
            repository.requests
        )
        assertEquals(2, pages.size)
        assertEquals(
            "https://example.com/song-3.jpg",
            pages.first().getValue("item-3").coverUrl
        )
        assertEquals("artist-3", pages.first().getValue("item-3").primaryArtistId)
        assertEquals(
            listOf("item-1", "item-2"),
            pages.last().keys.toList()
        )
        assertEquals("artist-1", pages.last().getValue("item-1").primaryArtistId)
        assertEquals("artist-2", pages.last().getValue("item-2").primaryArtistId)
    }

    private fun onlineItem(
        id: String,
        songId: String,
        title: String
    ): PlaylistItem {
        return PlaylistItem(
            id = id,
            displayName = title,
            songId = songId,
            title = title,
            artistText = "歌手",
            albumTitle = "专辑",
            itemType = PlaylistItemType.ONLINE,
            contextType = "album",
            contextId = "album-1",
            contextTitle = "专辑一"
        )
    }

    private fun musicInfo(
        songId: String,
        title: String,
        primaryArtistId: String,
        coverUrl: String,
        durationMs: Long
    ): MusicInfo {
        return MusicInfo(
            id = songId,
            songId = songId,
            title = title,
            artistNames = listOf("歌手"),
            artistIds = listOf(primaryArtistId),
            albumTitle = "专辑",
            coverUrl = coverUrl,
            durationMs = durationMs,
            playbackUri = ""
        )
    }

    private class FakeSongDetailRepository(
        private val responses: Map<String, MusicInfo>
    ) : SongDetailRepository {
        val requests = mutableListOf<List<String>>()

        override suspend fun fetchSongs(songIds: List<String>): List<MusicInfo> {
            requests += songIds
            return songIds.mapNotNull(responses::get)
        }
    }
}
