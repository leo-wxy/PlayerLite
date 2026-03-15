package com.wxy.playerlite.playback.process.source

import android.os.Looper
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import android.util.Log
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class HttpRangeDataProvider(
    url: String,
    private val requestHeaders: Map<String, String> = emptyMap(),
    private val connectionFactory: (URL) -> HttpURLConnection = { target ->
        target.openConnection() as HttpURLConnection
    },
    private val ipv4Resolver: (String) -> String? = DEFAULT_IPV4_RESOLVER,
    private val connectTimeoutMs: Int = 2_000,
    private val readTimeoutMs: Int = 8_000,
    private val maxReadBurstBytes: Int = 256 * 1024,
    private val streamChunkBytes: Int = 32 * 1024,
    private val openRetryCount: Int = 0,
    private val openRetryBackoffMs: Long = 120L
) : RangeDataProvider {
    private val targetUrl = URL(url)
    private val inFlightLock = Any()
    private val dnsResolveExecutor = Executors.newSingleThreadExecutor()
    private val openCallExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var closed = false

    @Volatile
    private var cachedContentLength: Long? = null

    @Volatile
    private var contentLengthProbeAttempted = false

    @Volatile
    private var ipv4ResolveAttempted = false

    @Volatile
    private var cachedIpv4Host: String? = null

    private var activeRead: ActiveRead? = null
    private var openingConnection: HttpURLConnection? = null

    override fun readAt(offset: Long, size: Int, callback: RangeDataProvider.ReadCallback) {
        callback.onDataBegin(offset, size)
        if (closed || size <= 0 || offset < 0L) {
            callback.onDataEnd(false)
            return
        }
        repeat((UNEXPECTED_EMPTY_READ_RETRY_COUNT + 1).coerceAtLeast(1)) { attemptIndex ->
            val read = obtainActiveRead(offset)
            if (read == null) {
                callback.onDataEnd(false)
                return
            }

            val consumed = readFromStream(
                input = read.stream,
                size = size,
                callback = callback
            )
            val consumedBytes = consumed.coerceAtLeast(0).toLong()
            val success = consumed >= 0
            val shouldRetryUnexpectedEmpty = shouldRetryUnexpectedEmptyRead(
                offset = offset,
                consumedBytes = consumedBytes,
                attemptIndex = attemptIndex
            )

            synchronized(inFlightLock) {
                val active = activeRead
                if (active === read) {
                    active.nextOffset = active.nextOffset + consumedBytes
                    if (!success || consumedBytes <= 0L || closed || shouldRetryUnexpectedEmpty) {
                        closeActiveReadLocked()
                    }
                }
            }

            if (success && consumedBytes > 0L) {
                callback.onDataEnd(!closed)
                return
            }

            if (!shouldRetryUnexpectedEmpty) {
                callback.onDataEnd(false)
                return
            }
        }
        callback.onDataEnd(false)
    }

    override fun cancelInFlightRead() {
        synchronized(inFlightLock) {
            closeActiveReadLocked()
            closeOpeningConnectionLocked()
        }
    }

    override fun queryContentLength(): Long? {
        if (closed) {
            return null
        }
        cachedContentLength?.let { return it }
        if (isMainThread()) {
            safeLogD("queryContentLength skipped on main thread: url=$targetUrl")
            return null
        }
        if (contentLengthProbeAttempted) {
            return cachedContentLength
        }
        contentLengthProbeAttempted = true

        val connection = connectionFactory(targetUrl).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            useCaches = false
            applyRequestHeaders()
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Connection", "close")
            setRequestProperty("Range", "bytes=0-0")
        }
        return try {
            val code = connection.responseCode
            val contentRange = connection.getHeaderField("Content-Range")
            val contentLengthHeader = connection.getHeaderField("Content-Length")
            updateContentLength(contentRange = contentRange, contentLengthHeader = contentLengthHeader)
            safeLogD(
                "queryContentLength: url=$targetUrl code=$code contentRange=$contentRange contentLength=$contentLengthHeader"
            )
            cachedContentLength
        } catch (error: Exception) {
            safeLogE("queryContentLength exception: url=$targetUrl", error)
            cachedContentLength
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    override fun close() {
        closed = true
        cancelInFlightRead()
        dnsResolveExecutor.shutdownNow()
        openCallExecutor.shutdownNow()
    }

    private fun obtainActiveRead(offset: Long): ActiveRead? {
        synchronized(inFlightLock) {
            if (closed) {
                return null
            }
            val current = activeRead
            if (current != null && current.nextOffset == offset) {
                return current
            }
            closeActiveReadLocked()
        }

        val opened = openRangeRead(offset) ?: return null
        synchronized(inFlightLock) {
            if (closed) {
                runCatching { opened.stream.close() }
                runCatching { opened.connection.disconnect() }
                return null
            }
            activeRead = opened
            return opened
        }
    }

    private fun openRangeRead(offset: Long): ActiveRead? {
        val attempts = (openRetryCount + 1).coerceAtLeast(1)
        repeat(attempts) { attemptIndex ->
            val preferredUrl = buildPreferredTargetUrl()
            val connection = connectionFactory(preferredUrl).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                useCaches = false
                applyRequestHeaders()
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Connection", "close")
                setRequestProperty("Range", "bytes=$offset-")
                if (preferredUrl.host != targetUrl.host) {
                    setRequestProperty("Host", targetUrl.host)
                }
            }
            try {
                synchronized(inFlightLock) {
                    if (closed) {
                        runCatching { connection.disconnect() }
                        return null
                    }
                    openingConnection = connection
                }
                val startNs = System.nanoTime()
                safeLogD(
                    "openRangeRead start: url=$targetUrl resolvedUrl=$preferredUrl offset=$offset timeout=${connectTimeoutMs}/${readTimeoutMs}ms attempt=${attemptIndex + 1}/$attempts"
                )
                val openTimeoutMs = (connectTimeoutMs.toLong() + readTimeoutMs.toLong() + 500L)
                    .coerceAtLeast(1_500L)
                val responseCodeFuture = openCallExecutor.submit<Int> { connection.responseCode }
                val code = try {
                    responseCodeFuture.get(openTimeoutMs, TimeUnit.MILLISECONDS)
                } catch (timeout: TimeoutException) {
                    responseCodeFuture.cancel(true)
                    runCatching { connection.disconnect() }
                    safeLogE(
                        "openRangeRead timeout: url=$targetUrl offset=$offset waitMs=$openTimeoutMs attempt=${attemptIndex + 1}/$attempts",
                        timeout
                    )
                    if (attemptIndex < attempts - 1 && !closed) {
                        sleepBeforeRetry(attemptIndex)
                        return@repeat
                    }
                    return null
                }
                val contentRange = connection.getHeaderField("Content-Range")
                val contentLengthHeader = connection.getHeaderField("Content-Length")
                updateContentLength(contentRange = contentRange, contentLengthHeader = contentLengthHeader)
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                safeLogD(
                    "openRangeRead: url=$targetUrl offset=$offset code=$code contentRange=$contentRange contentLength=$contentLengthHeader elapsedMs=$elapsedMs attempt=${attemptIndex + 1}/$attempts"
                )

                if (code !in 200..299) {
                    safeLogE(
                        "openRangeRead failed with non-2xx: url=$targetUrl offset=$offset code=$code attempt=${attemptIndex + 1}/$attempts",
                        IllegalStateException("HTTP $code")
                    )
                    runCatching { connection.disconnect() }
                    if (attemptIndex < attempts - 1 && !closed) {
                        sleepBeforeRetry(attemptIndex)
                        return@repeat
                    }
                    return null
                }
                if (offset > 0L && code != HttpURLConnection.HTTP_PARTIAL) {
                    safeLogE(
                        "openRangeRead rejected non-partial response: url=$targetUrl offset=$offset code=$code attempt=${attemptIndex + 1}/$attempts",
                        IllegalStateException("HTTP $code without partial content")
                    )
                    runCatching { connection.disconnect() }
                    if (attemptIndex < attempts - 1 && !closed) {
                        sleepBeforeRetry(attemptIndex)
                        return@repeat
                    }
                    return null
                }

                val stream = connection.inputStream
                return ActiveRead(
                    connection = connection,
                    stream = stream,
                    nextOffset = offset
                )
            } catch (error: Exception) {
                safeLogE(
                    "openRangeRead exception: url=$targetUrl offset=$offset attempt=${attemptIndex + 1}/$attempts",
                    error
                )
                runCatching { connection.disconnect() }
                if (attemptIndex < attempts - 1 && !closed) {
                    sleepBeforeRetry(attemptIndex)
                }
            } finally {
                synchronized(inFlightLock) {
                    if (openingConnection === connection) {
                        openingConnection = null
                    }
                }
            }
        }
        return null
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

    private fun readUpTo(
        input: InputStream,
        maxBytes: Int,
        callback: RangeDataProvider.ReadCallback
    ): Int {
        // Reuse a single buffer to avoid per-chunk allocations/GC. Callers must
        // respect `length` and copy the bytes if they need to retain them.
        val chunkBytes = streamChunkBytes.coerceIn(4 * 1024, 256 * 1024)
        val buffer = ByteArray(chunkBytes)
        var total = 0
        var zeroByteReads = 0
        while (total < maxBytes && !closed) {
            val toRead = (maxBytes - total).coerceAtMost(buffer.size)
            val read = runCatching { input.read(buffer, 0, toRead) }.getOrElse {
                return if (total > 0) total else -1
            }
            if (read < 0) {
                break
            }
            if (read == 0) {
                zeroByteReads += 1
                if (zeroByteReads > ZERO_BYTE_READ_RETRY_COUNT) {
                    return if (total > 0) total else -1
                }
                Thread.yield()
                continue
            }
            zeroByteReads = 0
            val accepted = runCatching { callback.onDataSend(buffer, read) }.getOrDefault(false)
            if (!accepted) {
                return -1
            }
            total += read
        }
        return total
    }

    private fun shouldRetryUnexpectedEmptyRead(
        offset: Long,
        consumedBytes: Long,
        attemptIndex: Int
    ): Boolean {
        if (closed || consumedBytes > 0L || attemptIndex >= UNEXPECTED_EMPTY_READ_RETRY_COUNT) {
            return false
        }
        val knownLength = cachedContentLength
        return knownLength == null || knownLength <= 0L || offset < knownLength
    }

    private fun readFromStream(
        input: InputStream,
        size: Int,
        callback: RangeDataProvider.ReadCallback
    ): Int {
        if (size <= 0) {
            return 0
        }
        val burstLimit = maxReadBurstBytes.coerceAtLeast(4 * 1024)
        val targetSize = size.coerceAtMost(burstLimit)
        return readUpTo(input, targetSize, callback)
    }

    private fun closeActiveReadLocked() {
        val read = activeRead ?: return
        activeRead = null
        runCatching { read.stream.close() }
        runCatching { read.connection.disconnect() }
    }

    private fun closeOpeningConnectionLocked() {
        val opening = openingConnection ?: return
        openingConnection = null
        runCatching { opening.disconnect() }
    }

    private fun buildPreferredTargetUrl(): URL {
        if (targetUrl.protocol.equals("https", ignoreCase = true)) {
            return targetUrl
        }
        val host = resolveIpv4Host(targetUrl.host) ?: return targetUrl
        return runCatching {
            if (targetUrl.port >= 0) {
                URL(targetUrl.protocol, host, targetUrl.port, targetUrl.file)
            } else {
                URL(targetUrl.protocol, host, targetUrl.file)
            }
        }.getOrDefault(targetUrl)
    }

    private fun resolveIpv4Host(host: String): String? {
        if (host.isBlank()) {
            return null
        }
        if (host.firstOrNull()?.isDigit() == true) {
            return host
        }
        if (ipv4ResolveAttempted) {
            return cachedIpv4Host
        }
        ipv4ResolveAttempted = true
        val resolved = runCatching {
            val future = dnsResolveExecutor.submit<String?> { ipv4Resolver(host) }
            try {
                future.get(1_500L, TimeUnit.MILLISECONDS)
            } finally {
                future.cancel(true)
            }
        }.getOrNull()
        if (resolved.isNullOrBlank()) {
            safeLogD("resolveIpv4Host fallback to original host: $host")
            return null
        }
        cachedIpv4Host = resolved
        safeLogD("resolveIpv4Host success: host=$host ipv4=$resolved")
        return resolved
    }

    private fun sleepBeforeRetry(attemptIndex: Int) {
        val sleepMs = (openRetryBackoffMs * (attemptIndex + 1L)).coerceAtLeast(0L)
        if (sleepMs <= 0L) {
            return
        }
        try {
            Thread.sleep(sleepMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun HttpURLConnection.applyRequestHeaders() {
        requestHeaders.forEach { (key, value) ->
            if (value.isNotBlank()) {
                setRequestProperty(key, value)
            }
        }
    }

    private companion object {
        private const val TAG = "HttpRangeDataProvider"
        private const val UNEXPECTED_EMPTY_READ_RETRY_COUNT = 1
        private const val ZERO_BYTE_READ_RETRY_COUNT = 4
        private val DEFAULT_IPV4_RESOLVER: (String) -> String? = { host ->
            InetAddress.getAllByName(host)
                .firstOrNull { it is Inet4Address }
                ?.hostAddress
        }
    }

    private data class ActiveRead(
        val connection: HttpURLConnection,
        val stream: InputStream,
        var nextOffset: Long
    )

    private fun isMainThread(): Boolean {
        return runCatching {
            Looper.getMainLooper() != null && Looper.myLooper() == Looper.getMainLooper()
        }.getOrDefault(false)
    }

    private fun safeLogD(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun safeLogE(message: String, throwable: Throwable) {
        runCatching { Log.e(TAG, message, throwable) }
    }
}
