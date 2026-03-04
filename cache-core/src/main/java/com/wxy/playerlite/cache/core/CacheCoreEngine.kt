package com.wxy.playerlite.cache.core

import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import com.wxy.playerlite.cache.core.session.CacheSession
import com.wxy.playerlite.cache.core.session.OpenSessionParams

internal interface CacheCoreEngine {
    fun init(config: CacheCoreConfig): Result<Unit>

    fun shutdown()

    fun isInitialized(): Boolean

    fun openSession(params: OpenSessionParams): Result<CacheSession>

    fun clearAll(): Result<Unit>

    fun lookup(resourceKey: String): Result<CacheLookupSnapshot?>

    fun lookupByPrefix(prefix: String, limit: Int): Result<List<CacheLookupSnapshot>>
}

internal object NativeProviderRegistry {
    private val nextHandle = java.util.concurrent.atomic.AtomicLong(1L)
    private val providers = java.util.concurrent.ConcurrentHashMap<Long, RangeDataProvider>()

    fun register(provider: RangeDataProvider): Long {
        val handle = nextHandle.getAndIncrement()
        providers[handle] = provider
        return handle
    }

    fun readAtBytes(handle: Long, offset: Long, size: Int): ByteArray {
        val provider = providers[handle] ?: return ByteArray(0)
        return runCatching { provider.readAtBytes(offset, size) }.getOrDefault(ByteArray(0))
    }

    fun readAtStream(
        handle: Long,
        offset: Long,
        size: Int,
        callback: RangeDataProvider.ReadCallback
    ): Boolean {
        val provider = providers[handle] ?: return false
        return runCatching {
            provider.readAt(offset, size, callback)
            true
        }.getOrDefault(false)
    }

    fun cancelInFlightRead(handle: Long) {
        providers[handle]?.let { provider ->
            runCatching { provider.cancelInFlightRead() }
        }
    }

    fun queryContentLength(handle: Long): Long {
        val provider = providers[handle] ?: return -1L
        return runCatching { provider.queryContentLength() ?: -1L }.getOrDefault(-1L)
    }

    fun close(handle: Long) {
        val provider = providers.remove(handle) ?: return
        runCatching { provider.close() }
    }

    fun release(handle: Long) {
        providers.remove(handle)
    }

    fun clearForTesting() {
        providers.clear()
    }
}
