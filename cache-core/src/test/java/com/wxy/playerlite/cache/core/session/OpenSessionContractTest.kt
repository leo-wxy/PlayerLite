package com.wxy.playerlite.cache.core.session

import com.wxy.playerlite.cache.core.CacheCore
import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSessionContractTest {
    @After
    fun tearDown() {
        CacheCore.shutdown()
    }

    @Test
    fun openSessionRejectsBlankResourceKey() {
        val root = File(System.getProperty("java.io.tmpdir"), "cache-core-open-session-test")
        CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath))

        val result = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = "  ",
                provider = FakeProvider(),
                config = SessionCacheConfig()
            )
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun openSessionReturnsSessionWhenParamsAreValid() {
        val root = File(System.getProperty("java.io.tmpdir"), "cache-core-open-session-test-valid")
        CacheCore.init(CacheCoreConfig(cacheRootDirPath = root.absolutePath))

        val result = CacheCore.openSession(
            OpenSessionParams(
                resourceKey = "track-001",
                provider = FakeProvider(),
                config = SessionCacheConfig()
            )
        )

        assertTrue(result.isSuccess)
    }

    private class FakeProvider : RangeDataProvider {
        override fun readAt(offset: Long, size: Int): ByteArray = ByteArray(0)

        override fun cancelInFlightRead() = Unit

        override fun queryContentLength(): Long? = null
    }
}
