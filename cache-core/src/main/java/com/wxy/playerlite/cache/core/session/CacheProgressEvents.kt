package com.wxy.playerlite.cache.core.session

data class CacheProgressChunk(
    val offset: Long,
    val length: Int
)

interface CacheProgressChunkEmitter {
    fun setCacheProgressChunkListener(listener: ((CacheProgressChunk) -> Unit)?)
}
