package com.wxy.playerlite.playback.process.source

import android.os.Looper
import android.util.Log
import com.wxy.playerlite.cache.core.provider.RangeDataProvider
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class OkHttpRangeDataProvider(
    url: String,
    private val requestHeaders: Map<String, String> = emptyMap(),
    private val connectTimeoutMs: Int = 2_000,
    private val readTimeoutMs: Int = 8_000,
    private val maxReadBurstBytes: Int = 256 * 1024,
    private val streamChunkBytes: Int = 32 * 1024
) : RangeDataProvider {
    private val targetUrl = url
    private val inFlightLock = Any()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .dns(Ipv4FirstDns)
        .build()

    @Volatile
    private var closed = false

    @Volatile
    private var cachedContentLength: Long? = null

    @Volatile
    private var contentLengthProbeAttempted = false

    private var activeCall: Call? = null
    private var activeResponse: Response? = null
    private var activeStream: InputStream? = null
    private var activeNextOffset: Long = -1L
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
            callback = callback
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
        callback.onDataEnd(success && !closed)
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
        if (contentLengthProbeAttempted) {
            return cachedContentLength
        }
        contentLengthProbeAttempted = true

        val request = Request.Builder()
            .url(targetUrl)
            .get()
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=0-0")
            .applyRequestHeaders()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val contentRange = response.header("Content-Range")
                val contentLengthHeader = response.header("Content-Length")
                updateContentLength(contentRange = contentRange, contentLengthHeader = contentLengthHeader)
                safeLogD(
                    "queryContentLength: url=$targetUrl code=$code contentRange=$contentRange contentLength=$contentLengthHeader"
                )
            }
            cachedContentLength
        }.getOrElse { error ->
            safeLogE("queryContentLength exception: url=$targetUrl", error)
            cachedContentLength
        }
    }

    override fun close() {
        closed = true
        cancelInFlightRead()
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
        val request = Request.Builder()
            .url(targetUrl)
            .get()
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=$offset-")
            .applyRequestHeaders()
            .build()
        val call = client.newCall(request)
        synchronized(inFlightLock) {
            if (closed) {
                call.cancel()
                return null
            }
            openingCall = call
        }

        var response: Response? = null
        try {
            safeLogD(
                "openRangeRead start: url=$targetUrl offset=$offset timeout=${connectTimeoutMs}/${readTimeoutMs}ms"
            )
            response = call.execute()
            val code = response.code
            val contentRange = response.header("Content-Range")
            val contentLengthHeader = response.header("Content-Length")
            updateContentLength(contentRange = contentRange, contentLengthHeader = contentLengthHeader)
            safeLogD(
                "openRangeRead: url=$targetUrl offset=$offset code=$code contentRange=$contentRange contentLength=$contentLengthHeader"
            )

                if (code !in 200..299) {
                safeLogE(
                    "openRangeRead failed with non-2xx: url=$targetUrl offset=$offset code=$code",
                    IllegalStateException("HTTP $code")
                )
                response.close()
                    return null
                }

                val stream = response.body?.byteStream() ?: run {
                    safeLogE(
                        "openRangeRead empty body: url=$targetUrl offset=$offset",
                        IllegalStateException("empty response body")
                )
                response.close()
                return null
            }

            synchronized(inFlightLock) {
                if (closed) {
                    response.close()
                    return null
                }

                if (offset > 0L && code != 206) {
                    if (code == 200) {
                        // Some servers ignore Range and always return full-body 200.
                        // Fallback by discarding bytes until requested offset so seek
                        // can still progress instead of hard failing.
                        val skipped = skipFully(stream, offset)
                        if (!skipped) {
                            safeLogE(
                                "openRangeRead skip fallback failed: url=$targetUrl offset=$offset code=$code",
                                IllegalStateException("failed to skip to target offset")
                            )
                            response.close()
                            return null
                        }
                        safeLogD(
                            "openRangeRead fallback: url=$targetUrl offset=$offset code=$code skipped=$offset"
                        )
                    } else {
                        safeLogE(
                            "openRangeRead rejected non-partial response: url=$targetUrl offset=$offset code=$code",
                            IllegalStateException("HTTP $code without partial content")
                        )
                        response.close()
                        return null
                    }
                }
                openingCall = null
                activeCall = call
                activeResponse = response
                activeStream = stream
                activeNextOffset = offset
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

    private fun Request.Builder.applyRequestHeaders(): Request.Builder {
        requestHeaders.forEach { (key, value) ->
            if (value.isNotBlank()) {
                header(key, value)
            }
        }
        return this
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
        val chunkBytes = streamChunkBytes.coerceIn(4 * 1024, 256 * 1024)
        val buffer = ByteArray(chunkBytes)
        var total = 0
        while (total < targetSize && !closed) {
            val toRead = (targetSize - total).coerceAtMost(buffer.size)
            val read = runCatching { input.read(buffer, 0, toRead) }.getOrElse { return -1 }
            if (read <= 0) {
                break
            }
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
    }

    private fun skipFully(input: InputStream, bytesToSkip: Long): Boolean {
        if (bytesToSkip <= 0L) {
            return true
        }
        var remaining = bytesToSkip
        val scratch = ByteArray(streamChunkBytes.coerceIn(4 * 1024, 256 * 1024))
        while (remaining > 0L && !closed) {
            val step = remaining.coerceAtMost(scratch.size.toLong()).toInt()
            val read = runCatching { input.read(scratch, 0, step) }.getOrElse { return false }
            if (read <= 0) {
                return false
            }
            remaining -= read.toLong()
        }
        return remaining <= 0L
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
