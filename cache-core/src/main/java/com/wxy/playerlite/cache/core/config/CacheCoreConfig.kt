package com.wxy.playerlite.cache.core.config

data class CacheCoreConfig(
    val cacheRootDirPath: String,
    val memoryCacheCapBytes: Long = 5L * 1024L * 1024L
)
