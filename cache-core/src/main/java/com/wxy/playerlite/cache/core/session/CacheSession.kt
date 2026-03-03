package com.wxy.playerlite.cache.core.session

interface CacheSession {
    val sessionId: Long
    val resourceKey: String

    fun read(size: Int): Result<ByteArray>

    fun readAt(offset: Long, size: Int): Result<ByteArray>

    fun seek(offset: Long, whence: Int): Result<Long>

    fun cancelPendingRead()

    fun close()
}
