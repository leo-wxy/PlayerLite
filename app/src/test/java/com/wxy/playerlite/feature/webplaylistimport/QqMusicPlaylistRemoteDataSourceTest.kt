package com.wxy.playerlite.feature.webplaylistimport

import com.sun.net.httpserver.HttpServer
import com.wxy.playerlite.network.core.JsonHttpClient
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QqMusicPlaylistRemoteDataSourceTest {
    @Test
    fun fetchPlaylistDetail_shouldUseQqPlaylistEndpointWithOriginAndReferer() = runBlocking {
        val server = HeaderCaptureHttpServer(
            response = """{"code":0,"cdlist":[]}"""
        )
        server.start()
        try {
            val remoteDataSource = DefaultQqMusicPlaylistRemoteDataSource(
                httpClient = JsonHttpClient(baseUrl = server.baseUrl)
            )

            remoteDataSource.fetchPlaylistDetail("7217720898")

            assertEquals(
                "/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=7217720898&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0",
                server.requestPath
            )
            assertEquals("https://y.qq.com", server.originHeader)
            assertEquals(
                "https://y.qq.com/n/yqq/playsquare/7217720898.html",
                server.refererHeader
            )
            assertTrue(server.userAgentHeader.isNotBlank())
        } finally {
            server.close()
        }
    }
}

private class HeaderCaptureHttpServer(
    private val response: String
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress(0), 0)

    var requestPath: String = ""
        private set
    var originHeader: String = ""
        private set
    var refererHeader: String = ""
        private set
    var userAgentHeader: String = ""
        private set

    val baseUrl: String
        get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { exchange ->
            requestPath = buildString {
                append(exchange.requestURI.path)
                exchange.requestURI.rawQuery?.let { query ->
                    append('?')
                    append(query)
                }
            }
            originHeader = exchange.requestHeaders.getFirst("Origin").orEmpty()
            refererHeader = exchange.requestHeaders.getFirst("Referer").orEmpty()
            userAgentHeader = exchange.requestHeaders.getFirst("User-Agent").orEmpty()
            val payload = response.toByteArray()
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { output ->
                output.write(payload)
            }
        }
    }

    fun start() {
        server.start()
    }

    override fun close() {
        server.stop(0)
    }
}
