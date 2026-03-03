package com.wxy.playerlite.playback.process.source

import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal class HttpRangeDataProvider(
    url: String,
    private val connectionFactory: (URL) -> HttpURLConnection = { target ->
        target.openConnection() as HttpURLConnection
    },
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000
) : RangeDataProvider {
    private val targetUrl = URL(url)
    private val inFlightLock = Any()

    @Volatile
    private var closed = false

    @Volatile
    private var cachedContentLength: Long? = null

    @Volatile
    private var inFlightConnection: HttpURLConnection? = null

    @Volatile
    private var inFlightStream: InputStream? = null

    override fun readAt(offset: Long, size: Int): ByteArray {
        if (closed || size <= 0 || offset < 0L) {
            return ByteArray(0)
        }
        val endInclusive = offset + size - 1L
        val connection = connectionFactory(targetUrl).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Range", "bytes=$offset-$endInclusive")
        }

        synchronized(inFlightLock) {
            if (closed) {
                runCatching { connection.disconnect() }
                return ByteArray(0)
            }
            inFlightConnection = connection
        }

        var stream: InputStream? = null
        return try {
            val code = connection.responseCode
            val contentRange = connection.getHeaderField("Content-Range")
            val contentLengthHeader = connection.getHeaderField("Content-Length")
            updateContentLength(contentRange = contentRange, contentLengthHeader = contentLengthHeader)

            if (code !in 200..299) {
                ByteArray(0)
            } else {
                stream = connection.inputStream
                synchronized(inFlightLock) {
                    inFlightStream = stream
                }
                val bytes = stream?.use { readUpTo(it, size) } ?: ByteArray(0)
                if (code == 206 || (offset == 0L && code == 200)) {
                    bytes
                } else {
                    ByteArray(0)
                }
            }
        } catch (_: Exception) {
            ByteArray(0)
        } finally {
            synchronized(inFlightLock) {
                if (inFlightStream === stream) {
                    inFlightStream = null
                }
                if (inFlightConnection === connection) {
                    inFlightConnection = null
                }
            }
            runCatching { stream?.close() }
            runCatching { connection.disconnect() }
        }
    }

    override fun cancelInFlightRead() {
        val connection = synchronized(inFlightLock) { inFlightConnection }
        val stream = synchronized(inFlightLock) { inFlightStream }
        runCatching { stream?.close() }
        runCatching { connection?.disconnect() }
    }

    override fun queryContentLength(): Long? {
        if (closed) {
            return null
        }
        cachedContentLength?.let { return it }

        val connection = connectionFactory(targetUrl).apply {
            requestMethod = "HEAD"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
        }
        return try {
            val contentLengthHeader = connection.getHeaderField("Content-Length")
            val contentLength = contentLengthHeader?.toLongOrNull()
            if (contentLength != null && contentLength >= 0L) {
                cachedContentLength = contentLength
            }
            cachedContentLength
        } catch (_: Exception) {
            cachedContentLength
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    override fun close() {
        closed = true
        cancelInFlightRead()
    }

    private fun updateContentLength(
        contentRange: String?,
        contentLengthHeader: String?
    ) {
        val fromRange = parseContentRangeTotal(contentRange)
        if (fromRange != null && fromRange >= 0L) {
            cachedContentLength = fromRange
            return
        }
        val fromHeader = contentLengthHeader?.toLongOrNull()
        if (fromHeader != null && fromHeader >= 0L) {
            cachedContentLength = fromHeader
        }
    }

    private fun parseContentRangeTotal(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) {
            return null
        }
        val slashIndex = contentRange.lastIndexOf('/')
        if (slashIndex <= 0 || slashIndex >= contentRange.length - 1) {
            return null
        }
        return contentRange.substring(slashIndex + 1).trim().toLongOrNull()
    }

    private fun readUpTo(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(maxBytes.coerceAtMost(8 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (total < maxBytes && !closed) {
            val toRead = (maxBytes - total).coerceAtMost(buffer.size)
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) {
                break
            }
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }
}
