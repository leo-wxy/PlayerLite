package com.wxy.playerlite.cache.core.session

import java.nio.ByteBuffer

interface CacheSession {
    val sessionId: Long
    val resourceKey: String

    fun read(size: Int): Result<ByteArray>

    fun readAt(offset: Long, size: Int): Result<ByteArray>

    fun readAtDirect(offset: Long, buffer: ByteBuffer, size: Int): Result<Int> {
        if (!buffer.isDirect) {
            return Result.failure(IllegalArgumentException("buffer must be direct"))
        }
        val maxRead = size.coerceAtMost(buffer.remaining())
        if (maxRead <= 0) {
            return Result.success(0)
        }
        return readAt(offset, maxRead).map { bytes ->
            val copied = bytes.size.coerceAtMost(maxRead)
            buffer.put(bytes, 0, copied)
            copied
        }
    }

    fun seek(offset: Long, whence: Int): Result<Long>

    fun cancelPendingRead()

    fun close()
}
