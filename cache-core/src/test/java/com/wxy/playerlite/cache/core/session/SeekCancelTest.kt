package com.wxy.playerlite.cache.core.session

import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekCancelTest {
    private val createdRoots = mutableListOf<File>()

    @After
    fun tearDown() {
        CacheCore.shutdown()
        createdRoots.forEach { it.deleteRecursively() }
        createdRoots.clear()
    }

    @Test
    fun seekCancelsInFlightReadAndAllowsNextRead() {
        val root = createRoot()
        assertTrue(CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).isSuccess)

        val provider = BlockingProvider()
        val session = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = "song_seek_cancel",
                provider = provider,
                config = SessionCacheConfig(blockSizeBytes = 8)
            )
        ).getOrThrow()

        val executor = Executors.newSingleThreadExecutor()
        try {
            val pending = executor.submit<ByteArray> { session.readAt(offset = 0L, size = 8).getOrThrow() }
            assertTrue(provider.awaitFirstReadStarted())

            val seekResult = session.seek(offset = 64L, whence = 0).getOrThrow()
            assertEquals(64L, seekResult)

            val cancelledRead = pending.get(1, TimeUnit.SECONDS)
            assertTrue(cancelledRead.isEmpty())
            assertEquals(1, provider.cancelCount.get())

            val nextRead = session.readAt(offset = 64L, size = 4).getOrThrow()
            assertArrayEquals("seek".encodeToByteArray(), nextRead)
        } finally {
            session.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun backwardSeekReadsBytesFromTargetOffset() {
        val root = createRoot()
        assertTrue(
            CacheCore.init(
                CacheCoreConfig(
                    cacheRootDirPath = root.absolutePath,
                    memoryCacheCapBytes = 64L
                )
            ).isSuccess
        )

        val payload = ByteArray(512) { index -> index.toByte() }
        val provider = PatternProvider(payload)
        val session = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = "song_seek_backward",
                provider = provider,
                config = SessionCacheConfig(blockSizeBytes = 32)
            )
        ).getOrThrow()

        try {
            val forward = session.readAt(offset = 256L, size = 16).getOrThrow()
            assertArrayEquals(payload.copyOfRange(256, 272), forward)

            val seekResult = session.seek(offset = 128L, whence = 0).getOrThrow()
            assertEquals(128L, seekResult)

            val backward = session.readAt(offset = 128L, size = 16).getOrThrow()
            assertArrayEquals(payload.copyOfRange(128, 144), backward)
            assertTrue(provider.cancelCount.get() >= 1)
        } finally {
            session.close()
        }
    }

    private fun createRoot(): File {
        val root = Files.createTempDirectory("cache-core-seek-cancel-test-").toFile()
        createdRoots += root
        return root
    }

    private class BlockingProvider : RangeDataProvider {
        private val firstReadStarted = CountDownLatch(1)
        private val cancelSignal = CountDownLatch(1)
        val cancelCount = AtomicInteger(0)

        override fun readAt(offset: Long, size: Int, callback: RangeDataProvider.ReadCallback) {
            callback.onDataBegin(offset, size)
            when (offset) {
                0L -> {
                    firstReadStarted.countDown()
                    cancelSignal.await(1, TimeUnit.SECONDS)
                    callback.onDataEnd(false)
                }

                64L -> {
                    val chunk = "seek".encodeToByteArray()
                    callback.onDataSend(chunk, chunk.size)
                    callback.onDataEnd(true)
                }

                else -> callback.onDataEnd(false)
            }
        }

        override fun cancelInFlightRead() {
            cancelCount.incrementAndGet()
            cancelSignal.countDown()
        }

        override fun queryContentLength(): Long? = 1024L

        fun awaitFirstReadStarted(): Boolean = firstReadStarted.await(1, TimeUnit.SECONDS)
    }

    private class PatternProvider(
        private val payload: ByteArray
    ) : RangeDataProvider {
        val cancelCount = AtomicInteger(0)

        override fun readAt(offset: Long, size: Int, callback: RangeDataProvider.ReadCallback) {
            callback.onDataBegin(offset, size)
            if (offset < 0L || size <= 0 || offset >= payload.size) {
                callback.onDataEnd(false)
                return
            }
            val start = offset.toInt()
            val end = (start + size).coerceAtMost(payload.size)
            val chunk = payload.copyOfRange(start, end)
            callback.onDataSend(chunk, chunk.size)
            callback.onDataEnd(true)
        }

        override fun cancelInFlightRead() {
            cancelCount.incrementAndGet()
        }

        override fun queryContentLength(): Long? = payload.size.toLong()
    }
}
