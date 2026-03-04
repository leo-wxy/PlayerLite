package com.wxy.playerlite.cache.core.provider

import java.io.ByteArrayOutputStream

interface RangeDataProvider {
    interface ReadCallback {
        fun onDataBegin(offset: Long, requestedSize: Int) = Unit

        fun onDataSend(data: ByteArray, length: Int): Boolean

        fun onDataEnd(success: Boolean)
    }

    fun readAt(offset: Long, size: Int, callback: ReadCallback)

    fun readAtBytes(offset: Long, size: Int): ByteArray {
        if (offset < 0L || size <= 0) {
            return ByteArray(0)
        }
        val output = ByteArrayOutputStream(size.coerceAtMost(8 * 1024))
        var endedWithSuccess = false
        readAt(offset, size, object : ReadCallback {
            override fun onDataSend(data: ByteArray, length: Int): Boolean {
                if (length <= 0 || data.isEmpty()) {
                    return true
                }
                val bounded = length.coerceAtMost(data.size)
                output.write(data, 0, bounded)
                return true
            }

            override fun onDataEnd(success: Boolean) {
                endedWithSuccess = success
            }
        })
        return if (endedWithSuccess) output.toByteArray() else ByteArray(0)
    }

    fun cancelInFlightRead()

    fun queryContentLength(): Long?

    fun close() = Unit
}
