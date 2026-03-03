package com.wxy.playerlite.cache.core.provider

interface RangeDataProvider {
    fun readAt(offset: Long, size: Int): ByteArray

    fun cancelInFlightRead()

    fun queryContentLength(): Long?

    fun close() = Unit
}
