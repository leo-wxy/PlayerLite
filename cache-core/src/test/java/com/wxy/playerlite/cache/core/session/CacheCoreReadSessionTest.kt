package com.wxy.playerlite.cache.core.session

import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheCoreReadSessionTest {
    private val createdRoots = mutableListOf<File>()

    @After
    fun tearDown() {
        CacheCore.shutdown()
        createdRoots.forEach { it.deleteRecursively() }
        createdRoots.clear()
    }

    @Test
    fun readReturnsProviderBytesForValidSession() {
        val root = createRoot()
        assertTrue(CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).isSuccess)

        val provider = RecordingProvider("hello-native-cache".encodeToByteArray())
        val session = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = "song_read_source",
                provider = provider,
                config = SessionCacheConfig(blockSizeBytes = 8)
            )
        ).getOrThrow()

        val read = session.readAt(offset = 0L, size = 5).getOrThrow()

        assertArrayEquals("hello".encodeToByteArray(), read)
        assertTrue(provider.callCount.get() > 0)
    }

    @Test
    fun reopenedSessionReadsFromDiskCacheWithoutExtraProviderFetch() {
        val root = createRoot()
        val key = "song_read_reopen_disk_hit"
        val payload = "hello-native-cache".encodeToByteArray()

        assertTrue(CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).isSuccess)
        val providerA = RecordingProvider(payload)
        val sessionA = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = key,
                provider = providerA,
                config = SessionCacheConfig(blockSizeBytes = 8)
            )
        ).getOrThrow()
        assertArrayEquals(payload, sessionA.readAt(offset = 0L, size = payload.size).getOrThrow())
        sessionA.close()
        CacheCore.shutdown()

        assertTrue(CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).isSuccess)
        val providerB = AlwaysEmptyProvider()
        val sessionB = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = key,
                provider = providerB,
                config = SessionCacheConfig(blockSizeBytes = 8)
            )
        ).getOrThrow()

        val secondRead = sessionB.readAt(offset = 0L, size = payload.size).getOrThrow()
        assertArrayEquals(payload, secondRead)
        assertEquals(0, providerB.callCount.get())
    }

    private fun createRoot(): File {
        val root = Files.createTempDirectory("cache-core-read-test-").toFile()
        createdRoots += root
        return root
    }

    private class RecordingProvider(
        private val payload: ByteArray
    ) : RangeDataProvider {
        val callCount = AtomicInteger(0)

        override fun readAt(offset: Long, size: Int, callback: RangeDataProvider.ReadCallback) {
            callCount.incrementAndGet()
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

        override fun queryContentLength(): Long? = payload.size.toLong()
    }

    private class AlwaysEmptyProvider : RangeDataProvider {
        val callCount = AtomicInteger(0)

        override fun readAt(offset: Long, size: Int, callback: RangeDataProvider.ReadCallback) {
            callCount.incrementAndGet()
            callback.onDataBegin(offset, size)
            callback.onDataEnd(false)
        }

        override fun cancelInFlightRead() = Unit

        override fun queryContentLength(): Long? = null
    }
}
