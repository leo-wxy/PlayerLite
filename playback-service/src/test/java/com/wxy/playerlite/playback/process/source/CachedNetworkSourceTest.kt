package com.wxy.playerlite.playback.process.source

import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.SessionCacheConfig
import com.wxy.playerlite.player.source.IPlaysource
import com.wxy.playerlite.playback.process.OnlineCacheMetadata
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

    @Test
    fun openShouldPersistExtraMetadataWhenProvided() {
        val root = createRoot()
        CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).getOrThrow()

        val resourceKey = "song_cached_source_extra"
        val source = CachedNetworkSource(
            resourceKey = resourceKey,
            provider = RecordingProvider("hello-native-cache".encodeToByteArray()),
            sessionConfig = SessionCacheConfig(blockSizeBytes = 8),
            extraMetadata = mapOf(OnlineCacheMetadata.CLIP_MODE_KEY to "full")
        )

        assertEquals(IPlaysource.AudioSourceCode.ASC_SUCCESS, source.open())
        source.close()

        val extraFile = File(root, "${resourceKey}_extra.json")
        val extraText = extraFile.readText()
        assertTrue(extraText.contains("\"${OnlineCacheMetadata.CLIP_MODE_KEY}\""))
        assertTrue(extraText.contains("\"full\""))
    }

    @Test
    fun staleContentLengthHintShouldBeCorrectedAndAllowReadingPastOldBoundary() {
        val root = createRoot()
        CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).getOrThrow()

        val payload = "hello-native-cache".encodeToByteArray()
        val resourceKey = "song_cached_source_stale_hint"
        val source = CachedNetworkSource(
            resourceKey = resourceKey,
            provider = RecordingProvider(payload),
            sessionConfig = SessionCacheConfig(blockSizeBytes = 8),
            contentLengthHint = 8L
        )

        assertEquals(IPlaysource.AudioSourceCode.ASC_SUCCESS, source.open())

        val head = ByteArray(8)
        assertEquals(8, source.read(head, head.size))
        assertArrayEquals(payload.copyOfRange(0, 8), head)

        val tail = ByteArray(payload.size - 8)
        val tailRead = source.read(tail, tail.size)
        assertEquals(payload.size - 8, tailRead)
        assertArrayEquals(payload.copyOfRange(8, payload.size), tail.copyOf(tailRead))

        val snapshot = CacheCore.lookup(resourceKey).getOrThrow()
        assertEquals(payload.size.toLong(), snapshot?.contentLength)
    }

    @Test
    fun transientEmptyReadBeforeKnownTailShouldRetrySameOffset() {
        val root = createRoot()
        CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).getOrThrow()

        val payload = "hello-native-cache".encodeToByteArray()
        val source = CachedNetworkSource(
            resourceKey = "song_cached_source_retry_tail",
            provider = TransientEmptyTailProvider(
                payload = payload,
                transientEmptyOffset = 8L
            ),
            sessionConfig = SessionCacheConfig(blockSizeBytes = 8),
            contentLengthHint = payload.size.toLong()
        )

        assertEquals(IPlaysource.AudioSourceCode.ASC_SUCCESS, source.open())

        val head = ByteArray(8)
        assertEquals(8, source.read(head, head.size))
        assertArrayEquals(payload.copyOfRange(0, 8), head)

        val tail = ByteArray(payload.size - 8)
        val tailRead = source.read(tail, tail.size)
        assertEquals(payload.size - 8, tailRead)
        assertArrayEquals(payload.copyOfRange(8, payload.size), tail.copyOf(tailRead))
    }

    @Test
    fun emptyReadBeforeKnownEofShouldBeReportedAsError() {
        val root = createRoot()
        CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).getOrThrow()

        val source = CachedNetworkSource(
            resourceKey = "song_cached_source_known_length_empty",
            provider = AlwaysEmptyKnownLengthProvider(contentLength = 32L),
            sessionConfig = SessionCacheConfig(blockSizeBytes = 8),
            contentLengthHint = 32L
        )

        assertEquals(IPlaysource.AudioSourceCode.ASC_SUCCESS, source.open())

        val buffer = ByteArray(8)
        val read = source.read(buffer, buffer.size)

        assertEquals(-1, read)
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

    private class TransientEmptyTailProvider(
        private val payload: ByteArray,
        private val transientEmptyOffset: Long
    ) : RangeDataProvider {
        private var emptyReturned = false

        override fun readAt(offset: Long, size: Int, callback: RangeDataProvider.ReadCallback) {
            callback.onDataBegin(offset, size)
            if (!emptyReturned && offset == transientEmptyOffset) {
                emptyReturned = true
                callback.onDataEnd(true)
                return
            }
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

    private class AlwaysEmptyKnownLengthProvider(
        private val contentLength: Long
    ) : RangeDataProvider {
        override fun readAt(offset: Long, size: Int, callback: RangeDataProvider.ReadCallback) {
            callback.onDataBegin(offset, size)
            callback.onDataEnd(false)
        }

        override fun cancelInFlightRead() = Unit

        override fun queryContentLength(): Long? = contentLength
    }
}
