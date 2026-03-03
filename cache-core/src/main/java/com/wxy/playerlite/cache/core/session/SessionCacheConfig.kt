package com.wxy.playerlite.cache.core.session

enum class CacheMode {
    MEMORY_ONLY,
    MEMORY_AND_DISK
}

data class SessionCacheConfig(
    val cacheMode: CacheMode = CacheMode.MEMORY_AND_DISK,
    val blockSizeBytes: Int = 64 * 1024
)
