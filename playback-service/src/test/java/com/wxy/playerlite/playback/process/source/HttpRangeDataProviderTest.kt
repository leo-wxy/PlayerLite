package com.wxy.playerlite.playback.process.source

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRangeDataProviderTest {
    @Test
    fun readAtUsesOpenEndedRangeHeaderAndReturnsBody() {
        val factory = FakeConnectionFactory(
            listOf(
                FakeResponse(
                    code = 206,
                    headers = mapOf("Content-Range" to "bytes 0-4/5"),
                    body = "hello".encodeToByteArray()
                )
            )
        )
        val provider = HttpRangeDataProvider(
            url = "https://example.com/audio.mp3",
            connectionFactory = factory::create
        )

        val bytes = provider.readAtBytes(offset = 0L, size = 5)

        assertArrayEquals("hello".encodeToByteArray(), bytes)
        assertEquals("bytes=0-", factory.requests.single().headers["Range"])
    }

    @Test
    fun contiguousReadsReuseSameHttpRequest() {
        val factory = FakeConnectionFactory(
            listOf(
                FakeResponse(
                    code = 206,
                    headers = mapOf("Content-Range" to "bytes 0-4/5"),
                    body = "hello".encodeToByteArray()
                )
            )
        )
        val provider = HttpRangeDataProvider(
            url = "https://example.com/audio.mp3",
            connectionFactory = factory::create
        )

        val first = provider.readAtBytes(offset = 0L, size = 2)
        val second = provider.readAtBytes(offset = 2L, size = 3)

        assertArrayEquals("he".encodeToByteArray(), first)
        assertArrayEquals("llo".encodeToByteArray(), second)
        assertEquals(1, factory.requests.size)
        assertEquals("bytes=0-", factory.requests.single().headers["Range"])
    }

    @Test
    fun contiguousReadsCanCrossTwoMegBoundary() {
        val payload = ByteArray((2.5 * 1024 * 1024).toInt()) { index ->
            (index % 251).toByte()
        }
        val factory = FakeConnectionFactory(
            listOf(
                FakeResponse(
                    code = 206,
                    headers = mapOf(
                        "Content-Range" to "bytes 0-${payload.lastIndex}/${payload.size}",
                        "Content-Length" to payload.size.toString()
                    ),
                    body = payload
                )
            )
        )
        val provider = HttpRangeDataProvider(
            url = "https://example.com/audio.flac",
            connectionFactory = factory::create
        )

        var offset = 0L
        repeat(40) {
            val bytes = provider.readAtBytes(offset = offset, size = 64 * 1024)
            if (bytes.isEmpty()) {
                return@repeat
            }
            val expected = payload.copyOfRange(offset.toInt(), offset.toInt() + bytes.size)
            assertArrayEquals(expected, bytes)
            offset += bytes.size
        }

        assertTrue(offset > 2L * 1024L * 1024L)
        assertEquals(1, factory.requests.size)
    }

    @Test
    fun nonContiguousReadStartsNewRangeRequest() {
        val factory = FakeConnectionFactory(
            listOf(
                FakeResponse(
                    code = 206,
                    headers = mapOf("Content-Range" to "bytes 0-4/12"),
                    body = "hello".encodeToByteArray()
                ),
                FakeResponse(
                    code = 206,
                    headers = mapOf("Content-Range" to "bytes 10-11/12"),
                    body = "ld".encodeToByteArray()
                )
            )
        )
        val provider = HttpRangeDataProvider(
            url = "https://example.com/audio.mp3",
            connectionFactory = factory::create
        )

        val first = provider.readAtBytes(offset = 0L, size = 2)
        val second = provider.readAtBytes(offset = 10L, size = 2)

        assertArrayEquals("he".encodeToByteArray(), first)
        assertArrayEquals("ld".encodeToByteArray(), second)
        assertEquals(2, factory.requests.size)
        assertEquals("bytes=0-", factory.requests[0].headers["Range"])
        assertEquals("bytes=10-", factory.requests[1].headers["Range"])
    }

    @Test
    fun queryContentLengthUsesRangeProbeAndCachesResult() {
        val factory = FakeConnectionFactory(
            listOf(
                FakeResponse(
                    code = 206,
                    headers = mapOf("Content-Range" to "bytes 0-0/12345", "Content-Length" to "1"),
                    body = byteArrayOf(0x01)
                )
            )
        )
        val provider = HttpRangeDataProvider(
            url = "https://example.com/audio.mp3",
            connectionFactory = factory::create
        )

        val first = provider.queryContentLength()
        val second = provider.queryContentLength()

        assertEquals(12345L, first)
        assertEquals(12345L, second)
        assertEquals(1, factory.requests.size)
        assertEquals("GET", factory.requests.single().method)
        assertEquals("bytes=0-0", factory.requests.single().headers["Range"])
        assertNotNull(factory.requests.single().headers["Accept-Encoding"])
    }

    @Test
    fun httpsReadShouldKeepOriginalHostnameInsteadOfResolvedIpv4() {
        val factory = FakeConnectionFactory(
            listOf(
                FakeResponse(
                    code = 206,
                    headers = mapOf("Content-Range" to "bytes 0-4/5"),
                    body = "hello".encodeToByteArray()
                )
            )
        )
        val provider = HttpRangeDataProvider(
            url = "https://example.com/audio.mp3",
            connectionFactory = factory::create,
            ipv4Resolver = { "1.2.3.4" }
        )

        val bytes = provider.readAtBytes(offset = 0L, size = 5)

        assertArrayEquals("hello".encodeToByteArray(), bytes)
        assertEquals("example.com", factory.requests.single().url.host)
        assertTrue(!factory.requests.single().headers.containsKey("Host"))
    }

    @Test
    fun readAtAppliesCustomRequestHeaders() {
        val factory = FakeConnectionFactory(
            listOf(
                FakeResponse(
                    code = 206,
                    headers = mapOf("Content-Range" to "bytes 0-4/5"),
                    body = "hello".encodeToByteArray()
                )
            )
        )
        val provider = HttpRangeDataProvider(
            url = "https://example.com/audio.mp3",
            requestHeaders = mapOf(
                "Cookie" to "MUSIC_U=test",
                "X-CSRF-Token" to "csrf-token"
            ),
            connectionFactory = factory::create
        )

        provider.readAtBytes(offset = 0L, size = 5)

        val request = factory.requests.single()
        assertEquals("MUSIC_U=test", request.headers["Cookie"])
        assertEquals("csrf-token", request.headers["X-CSRF-Token"])
    }

    @Test
    fun unexpectedImmediateEofShouldReconnectAndRetrySameRange() {
        val factory = FakeConnectionFactory(
            listOf(
                FakeResponse(
                    code = 206,
                    headers = mapOf(
                        "Content-Range" to "bytes 1024-1028/4096",
                        "Content-Length" to "5"
                    ),
                    bodyStreamFactory = { ByteArrayInputStream(ByteArray(0)) }
                ),
                FakeResponse(
                    code = 206,
                    headers = mapOf(
                        "Content-Range" to "bytes 1024-1028/4096",
                        "Content-Length" to "5"
                    ),
                    body = "hello".encodeToByteArray()
                )
            )
        )
        val provider = HttpRangeDataProvider(
            url = "https://example.com/audio.mp3",
            connectionFactory = factory::create
        )

        val bytes = provider.readAtBytes(offset = 1024L, size = 5)

        assertArrayEquals("hello".encodeToByteArray(), bytes)
        assertEquals(2, factory.requests.size)
        assertEquals("bytes=1024-", factory.requests[0].headers["Range"])
        assertEquals("bytes=1024-", factory.requests[1].headers["Range"])
    }

    @Test
    fun transientZeroByteReadShouldKeepReadingSameConnection() {
        val factory = FakeConnectionFactory(
            listOf(
                FakeResponse(
                    code = 206,
                    headers = mapOf(
                        "Content-Range" to "bytes 0-4/5",
                        "Content-Length" to "5"
                    ),
                    bodyStreamFactory = { ZeroThenPayloadInputStream("hello".encodeToByteArray()) }
                )
            )
        )
        val provider = HttpRangeDataProvider(
            url = "https://example.com/audio.mp3",
            connectionFactory = factory::create
        )

        val bytes = provider.readAtBytes(offset = 0L, size = 5)

        assertArrayEquals("hello".encodeToByteArray(), bytes)
        assertEquals(1, factory.requests.size)
    }

    private data class FakeResponse(
        val code: Int,
        val headers: Map<String, String>,
        val body: ByteArray = ByteArray(0),
        val bodyStreamFactory: (() -> InputStream)? = null
    )

    private data class CapturedRequest(
        val url: URL,
        val method: String,
        val headers: Map<String, String>
    )

    private class FakeConnectionFactory(
        responses: List<FakeResponse>
    ) {
        private val queue: ArrayDeque<FakeResponse> = ArrayDeque(responses)
        val requests: MutableList<CapturedRequest> = mutableListOf()

        fun create(url: URL): HttpURLConnection {
            val response = if (queue.isEmpty()) {
                FakeResponse(code = 500, headers = emptyMap(), body = ByteArray(0))
            } else {
                queue.removeFirst()
            }
            return FakeHttpURLConnection(url, response) { requestUrl, method, headers ->
                requests += CapturedRequest(url = requestUrl, method = method, headers = headers)
            }
        }
    }

    private class FakeHttpURLConnection(
        url: URL,
        private val response: FakeResponse,
        private val onRequest: (URL, String, Map<String, String>) -> Unit
    ) : HttpURLConnection(url) {
        private val requestHeaders = mutableMapOf<String, String>()
        private var captured = false

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun setRequestProperty(key: String, value: String?) {
            if (value == null) {
                requestHeaders.remove(key)
            } else {
                requestHeaders[key] = value
            }
        }

        override fun getHeaderField(name: String?): String? {
            if (name == null) {
                return null
            }
            return response.headers[name]
        }

        override fun getResponseCode(): Int {
            captureIfNeeded()
            return response.code
        }

        override fun getInputStream(): InputStream {
            captureIfNeeded()
            return response.bodyStreamFactory?.invoke() ?: ByteArrayInputStream(response.body)
        }

        private fun captureIfNeeded() {
            if (captured) {
                return
            }
            captured = true
            onRequest(url, requestMethod, requestHeaders.toMap())
        }
    }

    private class ZeroThenPayloadInputStream(
        private val payload: ByteArray
    ) : InputStream() {
        private var returnedZero = false
        private var offset = 0

        override fun read(): Int {
            val buffer = ByteArray(1)
            val read = read(buffer, 0, 1)
            return if (read <= 0) {
                -1
            } else {
                buffer[0].toInt() and 0xFF
            }
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int
        ): Int {
            if (!returnedZero) {
                returnedZero = true
                return 0
            }
            if (offset >= payload.size) {
                return -1
            }
            val copySize = len.coerceAtMost(payload.size - offset)
            payload.copyInto(
                destination = b,
                destinationOffset = off,
                startIndex = offset,
                endIndex = offset + copySize
            )
            offset += copySize
            return copySize
        }
    }
}
