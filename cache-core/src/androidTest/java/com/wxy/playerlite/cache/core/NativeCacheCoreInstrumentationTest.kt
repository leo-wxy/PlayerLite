package com.wxy.playerlite.cache.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.OpenSessionParams
import com.wxy.playerlite.cache.core.session.SessionCacheConfig
import java.io.File
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

    private class ByteArrayProvider(
        private val payload: ByteArray
    ) : RangeDataProvider {
        override fun readAt(offset: Long, size: Int): ByteArray {
            if (offset >= payload.size || size <= 0) {
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

        override fun queryContentLength(): Long = payload.size.toLong()

        override fun close() = Unit
    }
}
