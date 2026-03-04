package com.wxy.playerlite.cache.core.config

data class CacheCoreConfig(
    val cacheRootDirPath: String,
    val memoryCacheCapBytes: Long = 5L * 1024L * 1024L,
    val diskCacheMaxBytes: Long = 500L * 1024L * 1024L,
    val diskCacheCleanRangeMin: Double = 0.8,
    val diskCacheCleanRangeMax: Double = 1.0,
    val readRetryCount: Int = 3
)
