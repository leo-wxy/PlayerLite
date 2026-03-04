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
}
