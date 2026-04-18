package com.wxy.playerlite.feature.song

import com.wxy.playerlite.core.playback.SongDetailRepository
import com.wxy.playerlite.feature.player.SongWikiRepository
import com.wxy.playerlite.playback.model.MusicInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongDetailFeatureRepositoryTest {
    @Test
    fun loadSongDetail_whenOnlineDetailMissing_shouldUseFallbackSnapshotAndKeepRecentRemovalKey() = runTest {
        val repository = AppSongDetailFeatureRepository(
            songDetailRepository = object : SongDetailRepository {
                override suspend fun fetchSongs(songIds: List<String>): List<MusicInfo> = emptyList()
            },
            songWikiRepository = object : SongWikiRepository {
                override suspend fun fetchSongWiki(songId: String) = null
            },
            favoriteRepository = object : SongFavoriteRepository {
                override suspend fun favoriteSong(songId: String) = Result.success(Unit)
            },
            recentRecordKey = "online:1973665667",
            onlineFallbackSnapshot = OnlineSongFallbackSnapshot(
                title = "夜曲",
                artistText = "周杰伦",
                albumTitle = "十一月的肖邦",
                durationMs = 213000L,
                coverUrl = "https://example.com/cover.jpg",
                primaryArtistId = "artist-1",
                albumId = "album-1"
            )
        )

        val detail = repository.loadSongDetail(SongRef.Online(songId = "1973665667"))

        assertEquals(SongDetailSource.ONLINE, detail.source)
        assertEquals("online:1973665667", detail.recentRecordKey)
        assertEquals("夜曲", detail.title)
        assertEquals("周杰伦", detail.artistText)
        assertEquals("十一月的肖邦", detail.albumTitle)
        assertEquals("artist-1", detail.primaryArtistId)
        assertEquals("album-1", detail.albumId)
        assertEquals("1973665667", detail.playlistItem.songId)
        assertTrue(detail.canRemoveFromRecent)
        assertTrue(detail.canFavorite)
        assertNull(detail.wiki)
    }
}
