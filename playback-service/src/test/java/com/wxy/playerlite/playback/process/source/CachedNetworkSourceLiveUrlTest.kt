package com.wxy.playerlite.playback.process.source

import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.session.SessionCacheConfig
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class CachedNetworkSourceLiveUrlTest {
    private val createdRoots = mutableListOf<File>()

    @After
    fun tearDown() {
        CacheCore.shutdown()
        createdRoots.forEach { it.deleteRecursively() }
        createdRoots.clear()
    }

    @Test
    fun liveUrlReadAndSeekShouldGrowCacheAndPersistBlocks() {
        assumeTrue(
            "Set RUN_LIVE_NETWORK_TESTS=true to execute live network regression test",
            System.getenv("RUN_LIVE_NETWORK_TESTS")?.equals("true", ignoreCase = true) == true
        )

        val root = createRoot()
        CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).getOrThrow()

        val resourceKey = "live_seek_test_song_1"
        val source = CachedNetworkSource(
            resourceKey = resourceKey,
            provider = HttpRangeDataProvider("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
            sessionConfig = SessionCacheConfig(blockSizeBytes = 64 * 1024)
        )

        try {
            source.open()
            val firstBuffer = ByteArray(16 * 1024)
            val firstRead = source.read(firstBuffer, firstBuffer.size)
            assertTrue("first read should return bytes", firstRead > 0)

            val seekTo = 2L * 1024L * 1024L
            val seekResult = source.seek(seekTo, com.wxy.playerlite.player.source.IPlaysource.SEEK_SET)
            assertTrue("seek should move forward", seekResult >= seekTo)

            val secondBuffer = ByteArray(16 * 1024)
            val secondRead = source.read(secondBuffer, secondBuffer.size)
            assertTrue("second read after seek should return bytes", secondRead > 0)
        } finally {
            source.close()
        }

        val dataFile = File(root, "$resourceKey.data")
        val configFile = File(root, "${resourceKey}_config.json")
        assertTrue("data file should exist", dataFile.exists())
        assertTrue("data file should grow", dataFile.length() > 0L)
        assertTrue("config file should exist", configFile.exists())

        val config = configFile.readText()
        assertTrue("config should contain block metadata", config.contains("\"blocks\": ["))
        assertTrue("config should include resource key", config.contains("\"resourceKey\": \"$resourceKey\""))
    }

    private fun createRoot(): File {
        val root = Files.createTempDirectory("playback-live-cache-test-").toFile()
        createdRoots += root
        return root
    }
}

