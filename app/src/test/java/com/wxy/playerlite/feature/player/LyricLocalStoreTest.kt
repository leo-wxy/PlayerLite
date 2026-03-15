package com.wxy.playerlite.feature.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricLocalStoreTest {
    @Test
    fun writeAndRead_shouldPersistLyricBySongId() {
        val directory = createTempDir(prefix = "lyric-store-read-write")
        try {
            val store = LyricLocalStore(directory = directory)

            store.write(
                songId = "33894312",
                rawLyric = "[00:12.00]天空好想下雨"
            )

            assertEquals(
                "[00:12.00]天空好想下雨",
                store.read("33894312")
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun read_shouldTouchEntryForLruRetention() {
        val directory = createTempDir(prefix = "lyric-store-touch")
        try {
            val store = LyricLocalStore(directory = directory)
            store.write(songId = "33894312", rawLyric = "[00:12.00]天空好想下雨")
            val file = File(directory, "33894312.lrc")
            file.setLastModified(1L)

            val content = store.read("33894312")

            assertEquals("[00:12.00]天空好想下雨", content)
            assertTrue(file.lastModified() > 1L)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun write_shouldPruneOldestEntriesWhenCountExceedsLimit() {
        val directory = createTempDir(prefix = "lyric-store-prune")
        try {
            repeat(100) { index ->
                val file = File(directory, "song-$index.lrc")
                file.writeText("[00:00.00]line-$index")
                file.setLastModified(index.toLong() + 1L)
            }
            val store = LyricLocalStore(directory = directory, maxEntries = 100)

            store.write(songId = "song-100", rawLyric = "[00:00.00]latest")

            assertFalse(File(directory, "song-0.lrc").exists())
            assertTrue(File(directory, "song-100.lrc").exists())
            assertEquals(100, directory.listFiles()?.count())
        } finally {
            directory.deleteRecursively()
        }
    }
}
