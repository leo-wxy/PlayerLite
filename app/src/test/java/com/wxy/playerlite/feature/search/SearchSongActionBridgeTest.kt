package com.wxy.playerlite.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchSongActionBridgeTest {
    @Test
    fun buildSearchPlaybackRequest_shouldUseVisibleSongResultsAndClickedSongIndex() {
        val songs = listOf(
            SearchResultUiModel.Song(
                id = "song-1",
                title = "无人知晓",
                artistText = "田馥甄",
                albumTitle = "无人知晓",
                primaryArtistId = "artist-1",
                albumId = "album-1",
                coverUrl = "https://example.com/1.jpg",
                routeTarget = SearchRouteTarget.Song(songId = "song-1"),
                durationMs = 235_000L
            ),
            SearchResultUiModel.Song(
                id = "song-2",
                title = "先知",
                artistText = "田馥甄",
                albumTitle = "无人知晓",
                primaryArtistId = "artist-1",
                albumId = "album-1",
                coverUrl = "https://example.com/2.jpg",
                routeTarget = SearchRouteTarget.Song(songId = "song-2"),
                durationMs = 241_000L
            )
        )

        val request = buildSearchPlaybackRequest(
            songs = songs,
            activeSongId = "song-2"
        )

        assertNotNull(request)
        assertEquals(1, request?.activeIndex)
        assertEquals(listOf("song-1", "song-2"), request?.items?.map { it.songId })
        assertEquals(
            listOf("search:song-1:0", "search:song-2:1"),
            request?.items?.map { it.id }
        )
        val firstItem = requireNotNull(request?.items?.firstOrNull())
        assertEquals("", firstItem.uri)
        assertEquals("artist-1", firstItem.primaryArtistId)
        assertEquals("album-1", firstItem.albumId)
        assertEquals("search_song_result", firstItem.contextType)
        assertEquals("song-1", firstItem.contextId)
        assertEquals("无人知晓", firstItem.contextTitle)
    }

    @Test
    fun buildSearchPlaybackRequest_shouldReturnNullWhenActiveSongIsMissing() {
        val request = buildSearchPlaybackRequest(
            songs = listOf(
                SearchResultUiModel.Song(
                    id = "song-1",
                    title = "无人知晓",
                    artistText = "田馥甄",
                    albumTitle = "无人知晓",
                    coverUrl = null,
                    routeTarget = SearchRouteTarget.Song(songId = "song-1")
                )
            ),
            activeSongId = "missing"
        )

        assertNull(request)
    }
}
