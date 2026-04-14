package com.wxy.playerlite.feature.main

import com.wxy.playerlite.feature.home.HomeContentTarget
import com.wxy.playerlite.feature.search.SearchRouteTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFeatureRouteBridgeTest {
    @Test
    fun dailyRecommendedTarget_shouldMapToInternalEntryAction() {
        assertEquals(
            ContentEntryAction.OpenDailyRecommendedSongs,
            HomeContentTarget.DailyRecommendedSongs.toContentEntryAction()
        )
    }

    @Test
    fun detailTargets_shouldMapToSearchRouteDetails() {
        assertEquals(
            ContentEntryAction.OpenDetail(
                SearchRouteTarget.Song(songId = "song-1")
            ),
            HomeContentTarget.Song(songId = "song-1").toContentEntryAction()
        )
        assertEquals(
            ContentEntryAction.OpenDetail(
                SearchRouteTarget.Artist(artistId = "artist-1")
            ),
            HomeContentTarget.Artist(artistId = "artist-1").toContentEntryAction()
        )
        assertEquals(
            ContentEntryAction.OpenDetail(
                SearchRouteTarget.Album(albumId = "album-1")
            ),
            HomeContentTarget.Album(albumId = "album-1").toContentEntryAction()
        )
        assertEquals(
            ContentEntryAction.OpenDetail(
                SearchRouteTarget.Playlist(playlistId = "playlist-1")
            ),
            HomeContentTarget.Playlist(playlistId = "playlist-1").toContentEntryAction()
        )
    }

    @Test
    fun externalAndUnsupportedTargets_shouldPreserveFallbackMessages() {
        assertEquals(
            ContentEntryAction.OpenUri(
                uri = "https://music.163.com/topic?id=1",
                fallbackMessage = "打开失败"
            ),
            HomeContentTarget.ExternalUri(
                uri = "https://music.163.com/topic?id=1",
                fallbackMessage = "打开失败"
            ).toContentEntryAction()
        )
        assertEquals(
            ContentEntryAction.Unsupported(message = "当前内容暂不支持打开"),
            HomeContentTarget.Unsupported(
                message = "当前内容暂不支持打开"
            ).toContentEntryAction()
        )
    }
}
