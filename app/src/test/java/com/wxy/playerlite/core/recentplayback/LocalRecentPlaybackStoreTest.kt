package com.wxy.playerlite.core.recentplayback

import android.content.Context
import com.wxy.playerlite.core.playlist.PlaylistItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocalRecentPlaybackStoreTest {
    private val context = RuntimeEnvironment.getApplication()
    private val preferences = context.getSharedPreferences(
        "local_recent_playback_store_test",
        Context.MODE_PRIVATE
    )

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @Test
    fun record_duplicateUri_shouldKeepSingleRecordAndMoveToFront() {
        val store = SharedPreferencesLocalRecentPlaybackStore(preferences)

        store.record(
            record(
                playbackUri = "content://songs/1",
                title = "旧版本",
                artistText = "歌手 A"
            )
        )
        Thread.sleep(2)
        store.record(
            record(
                playbackUri = "content://songs/2",
                title = "第二首",
                artistText = "歌手 B"
            )
        )
        Thread.sleep(2)
        store.record(
            record(
                playbackUri = "content://songs/1",
                title = "新版本",
                artistText = "歌手 A"
            )
        )

        val restored = store.read(limit = 10)

        assertEquals(2, restored.size)
        assertEquals("local:content://songs/1", restored[0].recordKey)
        assertEquals("content://songs/1", restored[0].playbackUri)
        assertEquals("新版本", restored[0].title)
        assertEquals("local:content://songs/2", restored[1].recordKey)
        assertEquals("content://songs/2", restored[1].playbackUri)
        assertEquals(
            1,
            restored.count { it.recordKey == "local:content://songs/1" }
        )
        assertTrue(restored[0].playedAtMs >= restored[1].playedAtMs)
    }

    @Test
    fun record_duplicateOnlineSong_shouldKeepSingleRecordAndMoveToFront() {
        val store = SharedPreferencesLocalRecentPlaybackStore(preferences)

        store.record(onlineRecord(songId = "1"))
        Thread.sleep(2)
        store.record(onlineRecord(songId = "2"))
        Thread.sleep(2)
        store.record(
            onlineRecord(songId = "1").copy(
                title = "歌曲 1 新版本",
                artistText = "歌手 1 新版本"
            )
        )

        val restored = store.read(limit = 10)

        assertEquals(2, restored.size)
        assertEquals("online:1", restored[0].recordKey)
        assertEquals("歌曲 1 新版本", restored[0].title)
        assertEquals("online:2", restored[1].recordKey)
        assertEquals(1, restored.count { it.recordKey == "online:1" })
        assertTrue(restored[0].playedAtMs >= restored[1].playedAtMs)
    }

    @Test
    fun record_moreThanDefaultLimit_shouldTrimOldestRecords() {
        val store = SharedPreferencesLocalRecentPlaybackStore(preferences)

        repeat(101) { index ->
            store.record(onlineRecord(songId = index.toString()))
            Thread.sleep(1)
        }

        val restored = store.read(limit = Int.MAX_VALUE)

        assertEquals(LocalRecentPlaybackStore.DEFAULT_LIMIT, restored.size)
        assertEquals("online:100", restored.first().recordKey)
        assertEquals("100", restored.first().songId)
        assertFalse(restored.any { it.recordKey == "online:0" })
        assertTrue(restored.any { it.recordKey == "online:1" })
    }

    @Test
    fun read_shouldReturnRecordsInMostRecentFirstOrder() {
        val store = SharedPreferencesLocalRecentPlaybackStore(preferences)

        store.record(
            record(
                playbackUri = "content://songs/oldest",
                title = "最早播放",
                artistText = "歌手 1"
            )
        )
        Thread.sleep(2)
        store.record(
            record(
                playbackUri = "content://songs/middle",
                title = "中间播放",
                artistText = "歌手 2"
            )
        )
        Thread.sleep(2)
        store.record(
            record(
                playbackUri = "content://songs/latest",
                title = "最近播放",
                artistText = "歌手 3"
            )
        )

        val restored = store.read(limit = 3)

        assertEquals(
            listOf(
                "local:content://songs/latest",
                "local:content://songs/middle",
                "local:content://songs/oldest"
            ),
            restored.map(LocalRecentPlaybackRecord::recordKey)
        )
        assertTrue(restored[0].playedAtMs >= restored[1].playedAtMs)
        assertTrue(restored[1].playedAtMs >= restored[2].playedAtMs)
    }

    @Test
    fun remove_existingRecord_shouldDeleteAndKeepRemainingOrder() {
        val store = SharedPreferencesLocalRecentPlaybackStore(preferences)

        store.record(onlineRecord(songId = "1"))
        Thread.sleep(2)
        store.record(onlineRecord(songId = "2"))

        val removed = store.remove("online:2")
        val restored = store.read(limit = 10)

        assertTrue(removed)
        assertEquals(listOf("online:1"), restored.map(LocalRecentPlaybackRecord::recordKey))
    }

    private fun record(
        playbackUri: String,
        title: String,
        artistText: String
    ): LocalRecentPlaybackRecord = LocalRecentPlaybackRecord(
        recordKey = "local:$playbackUri",
        sourceType = PlaylistItemType.LOCAL,
        songId = playbackUri.substringAfterLast('/').takeIf { it.isNotBlank() },
        playbackUri = playbackUri,
        title = title,
        artistText = artistText,
        albumTitle = "测试专辑",
        primaryArtistId = null,
        albumId = null,
        coverUrl = null,
        durationMs = 180_000L,
        playedAtMs = 0L
    )

    private fun onlineRecord(songId: String): LocalRecentPlaybackRecord = LocalRecentPlaybackRecord(
        recordKey = "online:$songId",
        sourceType = PlaylistItemType.ONLINE,
        songId = songId,
        playbackUri = null,
        title = "歌曲 $songId",
        artistText = "歌手 $songId",
        albumTitle = "测试专辑",
        primaryArtistId = "artist-$songId",
        albumId = "album-$songId",
        coverUrl = null,
        durationMs = 180_000L,
        playedAtMs = 0L
    )
}
