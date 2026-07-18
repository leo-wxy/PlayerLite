package com.wxy.playerlite.playback.process.source

import android.os.Looper
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import android.util.Log
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

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
    private val streamChunkBytes: Int = 256 * 1024,
    private val openRetryCount: Int = 0,
    private val openRetryBackoffMs: Long = 120L
) : RangeDataProvider {
    private val targetUrl = URL(url)
    private val inFlightLock = Any()
    private val dnsResolveExecutor = SHARED_DNS_EXECUTOR
    private val openCallExecutor = SHARED_OPEN_EXECUTOR
    private val resourceIdentity = HttpResourceIdentityGuard()

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
    private var openingResponseFuture: Future<Int>? = null

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
            cancelOpeningResponseFutureLocked()
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
        synchronized(inFlightLock) {
            if (contentLengthProbeAttempted) {
                return cachedContentLength
            }
            contentLengthProbeAttempted = true
        }

        val connection = connectionFactory(targetUrl).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            useCaches = false
            applyRequestHeaders()
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Range", "bytes=0-0")
        }
        val resolved = try {
            val code = connection.responseCode
            val contentRange = connection.getHeaderField("Content-Range")
            val contentLengthHeader = connection.getHeaderField("Content-Length")
            val identityAccepted = resourceIdentity.accept(
                responseEtag = connection.getHeaderField("ETag"),
                responseLastModified = connection.getHeaderField("Last-Modified")
            )
            if (identityAccepted && isValidRangeResponse(0L, code, contentRange)) {
                updateContentLength(
                    responseCode = code,
                    contentRange = contentRange,
                    contentLengthHeader = contentLengthHeader
                )
            }
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
        if (resolved == null) {
            synchronized(inFlightLock) {
                contentLengthProbeAttempted = false
            }
        }
        return resolved
    }

    override fun close() {
        synchronized(inFlightLock) {
            if (closed) {
                return
            }
            closed = true
            closeActiveReadLocked()
            cancelOpeningResponseFutureLocked()
            closeOpeningConnectionLocked()
        }
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
            val ifRangeValue = resourceIdentity.ifRangeValue()
            val connection = connectionFactory(preferredUrl).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                useCaches = false
                applyRequestHeaders()
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Range", "bytes=$offset-")
                if (offset > 0L && ifRangeValue != null) {
                    setRequestProperty("If-Range", ifRangeValue)
                }
                if (preferredUrl.host != targetUrl.host) {
                    setRequestProperty("Host", targetUrl.host)
                }
            }
            var responseCodeFuture: Future<Int>? = null
            try {
                synchronized(inFlightLock) {
                    if (closed) {
                        runCatching { connection.disconnect() }
                        return null
                    }
                    openingConnection = connection
                }
                val startNs = System.nanoTime()
                val openTimeoutMs = (connectTimeoutMs.toLong() + readTimeoutMs.toLong() + 500L)
                    .coerceAtLeast(1_500L)
                val future = openCallExecutor.submit<Int> { connection.responseCode }
                responseCodeFuture = future
                synchronized(inFlightLock) {
                    if (closed || openingConnection !== connection) {
                        future.cancel(true)
                        runCatching { connection.disconnect() }
                        return null
                    }
                    openingResponseFuture = future
                }
                val code = try {
                    future.get(openTimeoutMs, TimeUnit.MILLISECONDS)
                } catch (_: CancellationException) {
                    runCatching { connection.disconnect() }
                    return null
                } catch (timeout: TimeoutException) {
                    future.cancel(true)
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
                val identityAccepted = resourceIdentity.accept(
                    responseEtag = connection.getHeaderField("ETag"),
                    responseLastModified = connection.getHeaderField("Last-Modified")
                )
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                if (elapsedMs >= SLOW_REQUEST_LOG_THRESHOLD_MS) {
                    safeLogD(
                        "slow range response: url=$targetUrl offset=$offset code=$code contentRange=$contentRange contentLength=$contentLengthHeader elapsedMs=$elapsedMs attempt=${attemptIndex + 1}/$attempts"
                    )
                }

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
                if (!identityAccepted) {
                    safeLogE(
                        "openRangeRead rejected changed resource: url=$targetUrl offset=$offset code=$code",
                        IllegalStateException("resource validator changed")
                    )
                    runCatching { connection.disconnect() }
                    return null
                }
                if (!isValidRangeResponse(offset, code, contentRange)) {
                    safeLogE(
                        "openRangeRead rejected mismatched range: url=$targetUrl offset=$offset code=$code contentRange=$contentRange attempt=${attemptIndex + 1}/$attempts",
                        IllegalStateException("response range does not match requested offset")
                    )
                    runCatching { connection.disconnect() }
                    if (attemptIndex < attempts - 1 && !closed) {
                        sleepBeforeRetry(attemptIndex)
                        return@repeat
                    }
                    return null
                }
                updateContentLength(
                    responseCode = code,
                    contentRange = contentRange,
                    contentLengthHeader = contentLengthHeader
                )

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
                    if (openingResponseFuture === responseCodeFuture) {
                        openingResponseFuture = null
                    }
                    if (openingConnection === connection) {
                        openingConnection = null
                    }
                }
            }
        }
        return null
    }

    private fun updateContentLength(
        responseCode: Int,
        contentRange: String?,
        contentLengthHeader: String?
    ) {
        if (responseCode !in 200..299) {
            return
        }
        val fromRange = parseContentRange(contentRange)?.totalLength
        if (fromRange != null && fromRange >= 0L) {
            cachedContentLength = fromRange
            return
        }
        if (responseCode != HttpURLConnection.HTTP_OK) {
            return
        }
        val fromHeader = contentLengthHeader?.toLongOrNull()
        if (fromHeader != null && fromHeader >= 0L) {
            cachedContentLength = fromHeader
        }
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

    private fun cancelOpeningResponseFutureLocked() {
        val openingFuture = openingResponseFuture ?: return
        openingResponseFuture = null
        openingFuture.cancel(true)
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
        private const val SLOW_REQUEST_LOG_THRESHOLD_MS = 500L
        private const val UNEXPECTED_EMPTY_READ_RETRY_COUNT = 1
        private const val ZERO_BYTE_READ_RETRY_COUNT = 4
        private val SHARED_DNS_EXECUTOR = newBoundedExecutor(
            coreThreads = 1,
            maxThreads = 2,
            queueCapacity = 8,
            threadPrefix = "range-dns"
        )
        private val SHARED_OPEN_EXECUTOR = newBoundedExecutor(
            coreThreads = 2,
            maxThreads = 4,
            queueCapacity = 8,
            threadPrefix = "range-open"
        )
        private val DEFAULT_IPV4_RESOLVER: (String) -> String? = { host ->
            InetAddress.getAllByName(host)
                .firstOrNull { it is Inet4Address }
                ?.hostAddress
        }

        private fun newBoundedExecutor(
            coreThreads: Int,
            maxThreads: Int,
            queueCapacity: Int,
            threadPrefix: String
        ): ThreadPoolExecutor {
            val threadNumber = AtomicInteger(0)
            return ThreadPoolExecutor(
                coreThreads,
                maxThreads,
                30L,
                TimeUnit.SECONDS,
                ArrayBlockingQueue(queueCapacity),
                { task ->
                    Thread(task, "$threadPrefix-${threadNumber.incrementAndGet()}").apply {
                        isDaemon = true
                    }
                },
                ThreadPoolExecutor.AbortPolicy()
            ).apply {
                allowCoreThreadTimeOut(true)
            }
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
