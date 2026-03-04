package com.wxy.playerlite.cache.core.persistence

import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.OpenSessionParams
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class CachePersistenceRecoveryTest {
    private val createdRoots = mutableListOf<File>()

    @After
    fun tearDown() {
        CacheCore.shutdown()
        createdRoots.forEach { it.deleteRecursively() }
        createdRoots.clear()
    }

    @Test
    fun openSessionCreatesDataConfigAndExtraFiles() {
        val root = createRoot()
        assertTrue(CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).isSuccess)

        val resourceKey = "track_persist_a"
        val open = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = resourceKey,
                provider = EmptyProvider()
            )
        )
        assertTrue(open.isSuccess)
        open.getOrThrow().close()

        assertTrue(File(root, "$resourceKey.data").exists())
        assertTrue(File(root, "${resourceKey}_config.json").exists())
        assertTrue(File(root, "${resourceKey}_extra.json").exists())
    }

    @Test
    fun reopenAfterShutdownCanReusePersistedSessionFiles() {
        val root = createRoot()
        val resourceKey = "track_persist_b"

        assertTrue(CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).isSuccess)
        val firstOpen = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = resourceKey,
                provider = EmptyProvider()
            )
        )
        assertTrue(firstOpen.isSuccess)
        firstOpen.getOrThrow().close()
        CacheCore.shutdown()

        assertTrue(CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath)).isSuccess)
        val secondOpen = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = resourceKey,
                provider = EmptyProvider()
            )
        )
        assertTrue(secondOpen.isSuccess)
        secondOpen.getOrThrow().close()
    }

    private fun createRoot(): File {
        val root = Files.createTempDirectory("cache-core-persist-test-").toFile()
        createdRoots += root
        return root
    }

    private class EmptyProvider : RangeDataProvider {
        override fun readAt(offset: Long, size: Int, callback: RangeDataProvider.ReadCallback) {
            callback.onDataBegin(offset, size)
            callback.onDataEnd(false)
        }

        override fun cancelInFlightRead() = Unit

        override fun queryContentLength(): Long? = null
    }
}
