package com.wxy.playerlite.feature.local

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocalSongsSnapshotStorageTest {
    @Test
    fun writeThenRead_shouldRestoreCachedSongs() {
        val context = RuntimeEnvironment.getApplication()
        val storage = LocalSongsSnapshotStorage(
            preferences = context.getSharedPreferences("local_songs_snapshot_test", Context.MODE_PRIVATE)
        )
        val songs = listOf(
            LocalSongEntry(
                id = "local-1",
                contentUri = "content://media/external/audio/media/1",
                title = "晴天",
                artist = "周杰伦",
                album = "叶惠美",
                durationMs = 269000L
            )
        )

        storage.write(songs)
        val restored = storage.read()

        assertEquals(1, restored.size)
        assertEquals("晴天", restored.single().title)
        assertTrue(restored.single().contentUri.contains("/1"))
    }
}
