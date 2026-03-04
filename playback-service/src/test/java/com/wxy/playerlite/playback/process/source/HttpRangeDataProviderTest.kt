package com.wxy.playerlite.playback.process.source

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private data class FakeResponse(
        val code: Int,
        val headers: Map<String, String>,
        val body: ByteArray
    )

    private data class CapturedRequest(
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
            return FakeHttpURLConnection(url, response) { method, headers ->
                requests += CapturedRequest(method = method, headers = headers)
            }
        }
    }

    private class FakeHttpURLConnection(
        url: URL,
        private val response: FakeResponse,
        private val onRequest: (String, Map<String, String>) -> Unit
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

        override fun getInputStream() = ByteArrayInputStream(response.body).also { captureIfNeeded() }

        private fun captureIfNeeded() {
            if (captured) {
                return
            }
            captured = true
            onRequest(requestMethod, requestHeaders.toMap())
        }
    }
}
