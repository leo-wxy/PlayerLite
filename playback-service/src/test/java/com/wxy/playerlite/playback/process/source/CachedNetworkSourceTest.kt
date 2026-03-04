package com.wxy.playerlite.playback.process.source

import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.SessionCacheConfig
import com.wxy.playerlite.player.source.IPlaysource
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedNetworkSourceTest {
    private val createdRoots = mutableListOf<File>()

    @After
    fun tearDown() {
        CacheCore.shutdown()
        createdRoots.forEach { it.deleteRecursively() }
        createdRoots.clear()
    }

    @Test
    fun openAndReadReturnsExpectedBytes() {
        val root = createRoot()
        CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).getOrThrow()

        val provider = RecordingProvider("hello-native-cache".encodeToByteArray())
        val source = CachedNetworkSource(
            resourceKey = "song_cached_source_a",
            provider = provider,
            sessionConfig = SessionCacheConfig(blockSizeBytes = 8)
        )

        assertEquals(IPlaysource.AudioSourceCode.ASC_SUCCESS, source.open())
        assertEquals("open should not trigger content-length probe", 0, provider.queryContentLengthCalls)
        val buffer = ByteArray(5)
        val read = source.read(buffer, 5)
        assertEquals(5, read)
        assertArrayEquals("hello".encodeToByteArray(), buffer)
        source.close()
        assertTrue(provider.closed)
    }

    private fun createRoot(): File {
        val root = Files.createTempDirectory("playback-cached-source-test-").toFile()
        createdRoots += root
        return root
    }

    private class RecordingProvider(
        private val payload: ByteArray
    ) : RangeDataProvider {
        var closed: Boolean = false
        var queryContentLengthCalls: Int = 0

        override fun readAt(offset: Long, size: Int, callback: RangeDataProvider.ReadCallback) {
            callback.onDataBegin(offset, size)
            if (size <= 0 || offset >= payload.size) {
                callback.onDataEnd(false)
                return
            }
            val start = offset.toInt().coerceAtLeast(0)
            val end = (start + size).coerceAtMost(payload.size)
            if (start >= end) {
                callback.onDataEnd(false)
                return
            }
            val chunk = payload.copyOfRange(start, end)
            callback.onDataSend(chunk, chunk.size)
            callback.onDataEnd(true)
        }

        override fun cancelInFlightRead() = Unit

        override fun queryContentLength(): Long? {
            queryContentLengthCalls += 1
            return payload.size.toLong()
        }

        override fun close() {
            closed = true
        }
    }
}
