package com.wxy.playerlite.cache.core

import com.wxy.playerlite.cache.core.config.CacheCoreConfig
import com.wxy.playerlite.cache.core.session.CacheSession
import com.wxy.playerlite.cache.core.session.OpenSessionParams

data class CacheLookupSnapshot(
    val resourceKey: String,
    val dataFilePath: String,
    val configFilePath: String,
    val extraFilePath: String,
    val dataFileSizeBytes: Long,
    val blockSizeBytes: Int,
    val contentLength: Long,
    val durationMs: Long,
    val cachedBlocks: Set<Long>,
    val lastAccessEpochMs: Long,
    val completedRanges: List<CacheCompletedRange> = emptyList()
)

data class CacheCompletedRange(
    val start: Long,
    val endExclusive: Long
)

object CacheCore {
    @Volatile
    private var engine: CacheCoreEngine = selectDefaultEngine()

    fun init(config: CacheCoreConfig): Result<Unit> {
        if (config.cacheRootDirPath.isBlank()) {
            return Result.failure(IllegalArgumentException("cacheRootDirPath cannot be blank"))
        }
        if (config.memoryCacheCapBytes <= 0L) {
            return Result.failure(IllegalArgumentException("memoryCacheCapBytes must be > 0"))
        }
        if (config.diskCacheMaxBytes <= 0L) {
            return Result.failure(IllegalArgumentException("diskCacheMaxBytes must be > 0"))
        }
        if (config.diskCacheCleanRangeMin <= 0.0 || config.diskCacheCleanRangeMin > 1.0) {
            return Result.failure(
                IllegalArgumentException("diskCacheCleanRangeMin must be in (0, 1]")
            )
        }
        if (config.diskCacheCleanRangeMax <= 0.0 || config.diskCacheCleanRangeMax > 1.0) {
            return Result.failure(
                IllegalArgumentException("diskCacheCleanRangeMax must be in (0, 1]")
            )
        }
        if (config.diskCacheCleanRangeMin > config.diskCacheCleanRangeMax) {
            return Result.failure(
                IllegalArgumentException("diskCacheCleanRangeMin cannot be greater than diskCacheCleanRangeMax")
            )
        }
        if (config.readRetryCount < 0) {
            return Result.failure(IllegalArgumentException("readRetryCount must be >= 0"))
        }
        return engine.init(config)
    }

    fun shutdown() {
        engine.shutdown()
    }

    fun isInitialized(): Boolean = engine.isInitialized()

    fun openSession(params: OpenSessionParams): Result<CacheSession> {
        return engine.openSession(params)
    }

    fun clearAll(): Result<Unit> {
        return engine.clearAll()
    }

    fun lookup(resourceKey: String): Result<CacheLookupSnapshot?> {
        return engine.lookup(resourceKey)
    }

    fun lookupByPrefix(prefix: String, limit: Int = 200): Result<List<CacheLookupSnapshot>> {
        return engine.lookupByPrefix(prefix, limit)
    }

    internal fun installEngineForTesting(testEngine: CacheCoreEngine) {
        shutdown()
        engine = testEngine
    }

    internal fun resetEngineForTesting() {
        shutdown()
        engine = selectDefaultEngine()
    }

    private fun selectDefaultEngine(): CacheCoreEngine {
        val nativeAvailable = CacheCoreNativeBridge.isAvailable()
        if (nativeAvailable) {
            return NativeCacheCoreEngine()
        }
        return if (isJvmUnitTestRuntime()) {
            JvmFakeCacheCoreEngine()
        } else {
            NativeCacheCoreEngine()
        }
    }

    private fun isJvmUnitTestRuntime(): Boolean {
        val vmName = System.getProperty("java.vm.name").orEmpty()
        return vmName.contains("OpenJDK", ignoreCase = true) ||
            vmName.contains("HotSpot", ignoreCase = true)
    }
}
