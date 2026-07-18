package com.wxy.playerlite.playback.process.source

import android.os.Looper
import android.util.Log
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Call
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class OkHttpRangeDataProvider(
    url: String,
    private val requestHeaders: Map<String, String> = emptyMap(),
    initialContentLengthHint: Long? = null,
    private val connectTimeoutMs: Int = 2_000,
    private val readTimeoutMs: Int = 8_000,
    private val maxReadBurstBytes: Int = 256 * 1024,
    private val streamChunkBytes: Int = 256 * 1024
) : RangeDataProvider {
    private val targetUrl = url
    private val inFlightLock = Any()
    private val client: OkHttpClient = SHARED_CLIENT.newBuilder()
        .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .build()
    private val resourceIdentity = HttpResourceIdentityGuard()
    private val physicalRequestCount = AtomicLong(0L)
    private val totalReadBytes = AtomicLong(0L)

    @Volatile
    private var closed = false

    @Volatile
    private var cachedContentLength: Long? = initialContentLengthHint?.takeIf { it > 0L }

    @Volatile
    private var contentLengthProbeAttempted = false

    private var activeCall: Call? = null
    private var activeResponse: Response? = null
    private var activeStream: InputStream? = null
    private var activeNextOffset: Long = -1L
    private var activeRequestId: Long = 0L
    private var activeRequestStartNs: Long = 0L
    private var activeFirstByteReported = false
    private var openingCall: Call? = null

    override fun readAt(offset: Long, size: Int, callback: RangeDataProvider.ReadCallback) {
        callback.onDataBegin(offset, size)
        if (closed || size <= 0 || offset < 0L) {
            callback.onDataEnd(false)
            return
        }

        val stream = obtainActiveRead(offset)
        if (stream == null) {
            callback.onDataEnd(false)
            return
        }

        val consumed = readFromStream(
            input = stream,
            size = size,
            callback = callback,
            onBytesRead = { bytesRead -> reportBytesRead(stream, bytesRead) }
        )
        val success = consumed >= 0
        val consumedBytes = consumed.coerceAtLeast(0).toLong()

        synchronized(inFlightLock) {
            if (stream === activeStream) {
                activeNextOffset += consumedBytes
                if (!success || consumedBytes <= 0L || closed) {
                    closeActiveReadLocked()
                }
            }
        }
        callback.onDataEnd(success && consumedBytes > 0L && !closed)
    }

    override fun cancelInFlightRead() {
        synchronized(inFlightLock) {
            closeActiveReadLocked()
            openingCall?.cancel()
            openingCall = null
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

        val request = Request.Builder()
            .url(targetUrl)
            .get()
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=0-0")
            .applyRequestHeaders()
            .build()
        val requestId = physicalRequestCount.incrementAndGet()
        val requestStartNs = System.nanoTime()
        val resolved = runCatching {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val contentRange = response.header("Content-Range")
                val contentLengthHeader = response.header("Content-Length")
                val identityAccepted = resourceIdentity.accept(
                    responseEtag = response.header("ETag"),
                    responseLastModified = response.header("Last-Modified")
                )
                if (identityAccepted && isValidRangeResponse(0L, code, contentRange)) {
                    updateContentLength(
                        responseCode = code,
                        contentRange = contentRange,
                        contentLengthHeader = contentLengthHeader
                    )
                }
                safeLogD(
                    "queryContentLength: request=$requestId url=$targetUrl code=$code contentRange=$contentRange contentLength=$contentLengthHeader elapsedMs=${elapsedMs(requestStartNs)}"
                )
            }
            cachedContentLength
        }.getOrElse { error ->
            safeLogE("queryContentLength exception: url=$targetUrl", error)
            cachedContentLength
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
            openingCall?.cancel()
            openingCall = null
        }
        safeLogD(
            "close metrics: url=$targetUrl physicalRequests=${physicalRequestCount.get()} readBytes=${totalReadBytes.get()}"
        )
    }

    private fun obtainActiveRead(offset: Long): InputStream? {
        synchronized(inFlightLock) {
            if (closed) {
                return null
            }
            val stream = activeStream
            if (stream != null && activeNextOffset == offset) {
                return stream
            }
            closeActiveReadLocked()
        }
        return openRangeRead(offset)
    }

    private fun openRangeRead(offset: Long): InputStream? {
        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .get()
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=$offset-")
            .applyRequestHeaders()
        if (offset > 0L) {
            resourceIdentity.ifRangeValue()?.let { requestBuilder.header("If-Range", it) }
        }
        val request = requestBuilder.build()
        val requestId = physicalRequestCount.incrementAndGet()
        val requestStartNs = System.nanoTime()
        val call = client.newCall(request)
        synchronized(inFlightLock) {
            if (closed) {
                call.cancel()
                return null
            }
            openingCall?.cancel()
            openingCall = call
        }

        var response: Response? = null
        try {
            response = call.execute()
            val code = response.code
            val contentRange = response.header("Content-Range")
            val contentLengthHeader = response.header("Content-Length")
            val identityAccepted = resourceIdentity.accept(
                responseEtag = response.header("ETag"),
                responseLastModified = response.header("Last-Modified")
            )
            val responseElapsedMs = elapsedMs(requestStartNs)
            if (responseElapsedMs >= SLOW_REQUEST_LOG_THRESHOLD_MS) {
                safeLogD(
                    "slow range response: request=$requestId url=$targetUrl offset=$offset code=$code contentRange=$contentRange contentLength=$contentLengthHeader elapsedMs=$responseElapsedMs"
                )
            }

            if (code !in 200..299) {
                safeLogE(
                    "openRangeRead failed with non-2xx: url=$targetUrl offset=$offset code=$code",
                    IllegalStateException("HTTP $code")
                )
                response.close()
                return null
            }
            if (!identityAccepted) {
                safeLogE(
                    "openRangeRead rejected changed resource: url=$targetUrl offset=$offset code=$code",
                    IllegalStateException("resource validator changed")
                )
                response.close()
                return null
            }
            if (!isValidRangeResponse(offset, code, contentRange)) {
                safeLogE(
                    "openRangeRead rejected mismatched range: url=$targetUrl offset=$offset code=$code contentRange=$contentRange",
                    IllegalStateException("response range does not match requested offset")
                )
                response.close()
                return null
            }
            updateContentLength(
                responseCode = code,
                contentRange = contentRange,
                contentLengthHeader = contentLengthHeader
            )

            val stream = response.body?.byteStream() ?: run {
                safeLogE(
                    "openRangeRead empty body: url=$targetUrl offset=$offset",
                    IllegalStateException("empty response body")
                )
                response.close()
                return null
            }

            synchronized(inFlightLock) {
                if (closed || openingCall !== call) {
                    response.close()
                    return null
                }

                openingCall = null
                activeCall = call
                activeResponse = response
                activeStream = stream
                activeNextOffset = offset
                activeRequestId = requestId
                activeRequestStartNs = requestStartNs
                activeFirstByteReported = false
            }
            return stream
        } catch (error: Exception) {
            safeLogE(
                "openRangeRead exception: url=$targetUrl offset=$offset",
                error
            )
            runCatching { response?.close() }
            return null
        } finally {
            synchronized(inFlightLock) {
                if (openingCall === call) {
                    openingCall = null
                }
            }
        }
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
        if (responseCode != 200) {
            return
        }
        val fromHeader = contentLengthHeader?.toLongOrNull()
        if (fromHeader != null && fromHeader >= 0L) {
            cachedContentLength = fromHeader
        }
    }

    private fun Request.Builder.applyRequestHeaders(): Request.Builder {
        requestHeaders.forEach { (key, value) ->
            if (value.isNotBlank()) {
                header(key, value)
            }
        }
        return this
    }

    private fun readFromStream(
        input: InputStream,
        size: Int,
        callback: RangeDataProvider.ReadCallback,
        onBytesRead: (Int) -> Unit
    ): Int {
        if (size <= 0) {
            return 0
        }
        val burstLimit = maxReadBurstBytes.coerceAtLeast(4 * 1024)
        val targetSize = size.coerceAtMost(burstLimit)
        val chunkBytes = streamChunkBytes.coerceIn(4 * 1024, 256 * 1024)
        val buffer = ByteArray(chunkBytes)
        var total = 0
        while (total < targetSize && !closed) {
            val toRead = (targetSize - total).coerceAtMost(buffer.size)
            val read = runCatching { input.read(buffer, 0, toRead) }.getOrElse { return -1 }
            if (read <= 0) {
                break
            }
            onBytesRead(read)
            val accepted = runCatching { callback.onDataSend(buffer, read) }.getOrDefault(false)
            if (!accepted) {
                return -1
            }
            total += read
        }
        return total
    }

    private fun closeActiveReadLocked() {
        activeCall?.cancel()
        activeCall = null
        runCatching { activeStream?.close() }
        activeStream = null
        runCatching { activeResponse?.close() }
        activeResponse = null
        activeNextOffset = -1L
        activeRequestId = 0L
        activeRequestStartNs = 0L
        activeFirstByteReported = false
    }

    private fun reportBytesRead(stream: InputStream, bytesRead: Int) {
        if (bytesRead <= 0) {
            return
        }
        val total = totalReadBytes.addAndGet(bytesRead.toLong())
        synchronized(inFlightLock) {
            if (stream !== activeStream || activeFirstByteReported) {
                return
            }
            activeFirstByteReported = true
            val firstByteElapsedMs = elapsedMs(activeRequestStartNs)
            if (firstByteElapsedMs >= SLOW_REQUEST_LOG_THRESHOLD_MS) {
                safeLogD(
                    "slow first byte: request=$activeRequestId url=$targetUrl elapsedMs=$firstByteElapsedMs totalReadBytes=$total"
                )
            }
        }
    }

    private fun elapsedMs(startNs: Long): Long {
        return if (startNs > 0L) {
            (System.nanoTime() - startNs) / 1_000_000L
        } else {
            -1L
        }
    }

    private object Ipv4FirstDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            val v4 = addresses.filterIsInstance<Inet4Address>()
            if (v4.isEmpty()) {
                return addresses
            }
            val v6 = addresses.filterNot { it is Inet4Address }
            return v4 + v6
        }
    }

    private companion object {
        private const val TAG = "OkHttpRangeProvider"
        private const val SLOW_REQUEST_LOG_THRESHOLD_MS = 500L
        private val SHARED_CLIENT = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .dns(Ipv4FirstDns)
            .build()
    }

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
