package com.wxy.playerlite.cache.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.OpenSessionParams
import com.wxy.playerlite.cache.core.session.SessionCacheConfig
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeCacheCoreInstrumentationTest {
    private val createdRoots = mutableListOf<File>()

    @After
    fun tearDown() {
        CacheCore.shutdown()
        createdRoots.forEach { it.deleteRecursively() }
        createdRoots.clear()
    }

    @Test
    fun nativeEngineCanReadPersistLookupAndClear() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(appContext.cacheDir, "cache-core-native-it-${System.currentTimeMillis()}").apply {
            mkdirs()
        }
        createdRoots += root

        CacheCore.init(
            CacheCoreConfig(
                cacheRootDirPath = root.absolutePath,
                memoryCacheCapBytes = 5L * 1024L * 1024L
            )
        ).getOrThrow()

        val key = "native_it_track_001"
        val payload = "hello-native-instrumentation".encodeToByteArray()
        val session = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = key,
                provider = ByteArrayProvider(payload),
                config = SessionCacheConfig(blockSizeBytes = 8)
            )
        ).getOrThrow()

        val firstRead = session.readAt(0L, 5).getOrThrow()
        assertArrayEquals("hello".encodeToByteArray(), firstRead)
        session.close()

        val snapshot = CacheCore.lookup(key).getOrThrow()
        assertNotNull(snapshot)
        val found = snapshot ?: return
        assertTrue(found.dataFileSizeBytes > 0L)
        assertTrue(found.cachedBlocks.isNotEmpty())

        CacheCore.clearAll().getOrThrow()
        assertTrue(CacheCore.lookupByPrefix("native_it_").getOrThrow().isEmpty())
    }

    @Test
    fun nativeEngineCanPrefetchPlaybackAndNextTrackConcurrently() {
        val root = createRoot("cache-core-native-concurrent")
        CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).getOrThrow()
        val firstPayload = ByteArray(512 * 1024) { (it % 127).toByte() }
        val secondPayload = ByteArray(512 * 1024) { ((it + 31) % 127).toByte() }
        val first = openSession("native_concurrent_first", firstPayload)
        val second = openSession("native_concurrent_second", secondPayload)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val firstRead = executor.submit<ByteArray> {
                first.readAt(0L, 128 * 1024).getOrThrow()
            }
            val secondRead = executor.submit<ByteArray> {
                second.readAt(0L, 128 * 1024).getOrThrow()
            }

            assertArrayEquals(
                firstPayload.copyOf(128 * 1024),
                firstRead.get(5, TimeUnit.SECONDS)
            )
            assertArrayEquals(
                secondPayload.copyOf(128 * 1024),
                secondRead.get(5, TimeUnit.SECONDS)
            )
        } finally {
            first.close()
            second.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun nativeEngineReadsDirectlyIntoByteBuffer() {
        val root = createRoot("cache-core-native-direct")
        CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).getOrThrow()
        val payload = "native-direct-buffer".encodeToByteArray()
        val session = openSession("native_direct", payload)
        val buffer = ByteBuffer.allocateDirect(payload.size)

        val read = session.readAtDirect(0L, buffer, buffer.remaining()).getOrThrow()
        assertTrue(read == payload.size)
        buffer.flip()
        val actual = ByteArray(buffer.remaining())
        buffer.get(actual)

        assertArrayEquals(payload, actual)
        session.close()
    }

    @Test
    fun nativeEngineCachedSeekDoesNotCancelProvider() {
        val root = createRoot("cache-core-native-cached-seek")
        CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).getOrThrow()
        val payload = ByteArray(256 * 1024) { (it % 113).toByte() }
        val provider = ByteArrayProvider(payload)
        val session = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = "native_cached_seek",
                provider = provider,
                config = SessionCacheConfig(blockSizeBytes = 64 * 1024)
            )
        ).getOrThrow()

        assertArrayEquals(payload.copyOfRange(0, 64 * 1024), session.readAt(0L, 64 * 1024).getOrThrow())
        val cancellationsBeforeSeek = provider.cancelCount.get()

        assertTrue(session.seek(1024L, 0).getOrThrow() == 1024L)
        assertTrue(provider.cancelCount.get() == cancellationsBeforeSeek)
        assertArrayEquals(payload.copyOfRange(1024, 2048), session.readAt(1024L, 1024).getOrThrow())
        session.close()
    }

    @Test
    fun nativeEnginePrefetchSkipsPersistedRangeAfterReopen() {
        val root = createRoot("cache-core-native-disk-prefetch")
        CacheCore.init(
            CacheCoreConfig(
                cacheRootDirPath = root.absolutePath,
                memoryCacheCapBytes = 1024L * 1024L
            )
        ).getOrThrow()
        val payload = ByteArray(4 * 1024 * 1024) { (it % 109).toByte() }
        val resourceKey = "native_disk_prefetch"
        val firstProvider = ByteArrayProvider(payload)
        val firstSession = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = resourceKey,
                provider = firstProvider,
                config = SessionCacheConfig(blockSizeBytes = 64 * 1024)
            )
        ).getOrThrow()

        val persistedBytes = 1024 * 1024
        var offset = 0L
        while (offset < persistedBytes) {
            val read = firstSession.readAt(offset, 64 * 1024).getOrThrow()
            assertTrue(read.isNotEmpty())
            offset += read.size
        }
        firstSession.close()

        val secondProvider = ByteArrayProvider(payload)
        val secondSession = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = resourceKey,
                provider = secondProvider,
                config = SessionCacheConfig(blockSizeBytes = 64 * 1024)
            )
        ).getOrThrow()
        try {
            secondSession.seek(persistedBytes.toLong() - 64L * 1024L, 0).getOrThrow()
            val firstNetworkOffset = secondProvider.awaitFirstReadOffset()
            assertTrue(
                "Expected prefetch to skip persisted range, offset=$firstNetworkOffset",
                firstNetworkOffset >= persistedBytes.toLong()
            )
        } finally {
            secondSession.close()
        }
    }

    private fun createRoot(prefix: String): File {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        return File(appContext.cacheDir, "$prefix-${System.currentTimeMillis()}").apply {
            mkdirs()
            createdRoots += this
        }
    }

    private fun openSession(resourceKey: String, payload: ByteArray) = CacheCore.openSession(
        OpenSessionParams(
            resourceKey = resourceKey,
            provider = ByteArrayProvider(payload),
            config = SessionCacheConfig(blockSizeBytes = 64 * 1024)
        )
    ).getOrThrow()

    private class ByteArrayProvider(
        private val payload: ByteArray
    ) : RangeDataProvider {
        val cancelCount = AtomicInteger(0)
        private val firstReadOffset = AtomicLong(Long.MIN_VALUE)
        private val firstReadStarted = java.util.concurrent.CountDownLatch(1)

        override fun readAt(offset: Long, size: Int, callback: RangeDataProvider.ReadCallback) {
            if (firstReadOffset.compareAndSet(Long.MIN_VALUE, offset)) {
                firstReadStarted.countDown()
            }
            callback.onDataBegin(offset, size)
            if (offset >= payload.size || size <= 0) {
                callback.onDataEnd(false)
                return
            }
            val start = offset.toInt().coerceAtLeast(0)
            val end = (start + size).coerceAtMost(payload.size)
            if (start >= end) {
                callback.onDataEnd(false)
                return
            }
            val bytes = payload.copyOfRange(start, end)
            callback.onDataSend(bytes, bytes.size)
            callback.onDataEnd(true)
        }

        override fun cancelInFlightRead() {
            cancelCount.incrementAndGet()
        }

        override fun queryContentLength(): Long = payload.size.toLong()

        override fun close() = Unit

        fun awaitFirstReadOffset(): Long {
            assertTrue(firstReadStarted.await(5, TimeUnit.SECONDS))
            return firstReadOffset.get()
        }
    }
}
