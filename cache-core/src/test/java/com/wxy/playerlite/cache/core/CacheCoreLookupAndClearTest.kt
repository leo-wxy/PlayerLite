package com.wxy.playerlite.cache.core

import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.OpenSessionParams
import com.wxy.playerlite.cache.core.session.SessionCacheConfig
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheCoreLookupAndClearTest {
    private val createdRoots = mutableListOf<File>()

    @After
    fun tearDown() {
        CacheCore.shutdown()
        createdRoots.forEach { it.deleteRecursively() }
        createdRoots.clear()
    }

    @Test
    fun lookupReturnsSnapshotForExistingResourceKey() {
        val root = createRoot()
        CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).getOrThrow()

        val key = "mix_alpha_001"
        val session = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = key,
                provider = ByteArrayProvider("hello-cache".encodeToByteArray()),
                config = SessionCacheConfig(blockSizeBytes = 8)
            )
        ).getOrThrow()
        session.readAt(0L, 5).getOrThrow()
        session.close()

        val snapshot = CacheCore.lookup(key).getOrThrow()
        assertNotNull(snapshot)
        val found = snapshot ?: return
        assertEquals(key, found.resourceKey)
        assertTrue(found.dataFileSizeBytes > 0L)
        assertTrue(found.cachedBlocks.contains(0L))
    }

    @Test
    fun lookupByPrefixReturnsMatchedEntries() {
        val root = createRoot()
        CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).getOrThrow()

        seed("mix_alpha_001")
        seed("mix_alpha_002")
        seed("mix_beta_001")

        val snapshots = CacheCore.lookupByPrefix("mix_alpha_").getOrThrow()
        assertEquals(2, snapshots.size)
        assertEquals(listOf("mix_alpha_001", "mix_alpha_002"), snapshots.map { it.resourceKey })
    }

    @Test
    fun clearAllRemovesCachedEntries() {
        val root = createRoot()
        CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).getOrThrow()

        seed("mix_clear_001")
        assertNotNull(CacheCore.lookup("mix_clear_001").getOrThrow())

        CacheCore.clearAll().getOrThrow()

        assertEquals(null, CacheCore.lookup("mix_clear_001").getOrThrow())
        assertTrue(CacheCore.lookupByPrefix("mix_").getOrThrow().isEmpty())
    }

    private fun seed(resourceKey: String) {
        val session = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = resourceKey,
                provider = ByteArrayProvider("payload-$resourceKey".encodeToByteArray()),
                config = SessionCacheConfig(blockSizeBytes = 8)
            )
        ).getOrThrow()
        session.readAt(0L, 8).getOrThrow()
        session.close()
    }

    private fun createRoot(): File {
        val root = Files.createTempDirectory("cache-core-lookup-clear-test-").toFile()
        createdRoots += root
        return root
    }

    private class ByteArrayProvider(
        private val payload: ByteArray
    ) : RangeDataProvider {
        override fun readAt(offset: Long, size: Int): ByteArray {
            if (size <= 0 || offset >= payload.size) {
                return ByteArray(0)
            }
            val start = offset.toInt().coerceAtLeast(0)
            val end = (start + size).coerceAtMost(payload.size)
            if (start >= end) {
                return ByteArray(0)
            }
            return payload.copyOfRange(start, end)
        }

        override fun cancelInFlightRead() = Unit

        override fun queryContentLength(): Long? = payload.size.toLong()

        override fun close() = Unit
    }
}

