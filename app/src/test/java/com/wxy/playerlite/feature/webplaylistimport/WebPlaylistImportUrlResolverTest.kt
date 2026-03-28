package com.wxy.playerlite.feature.webplaylistimport

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WebPlaylistImportUrlResolverTest {
    @Test
    fun resolve_shouldFollowRedirectAndReturnFinalUrl() = runBlocking {
        val finalUrlPath = "/final/4204621746"
        val server = RedirectHttpServer(location = finalUrlPath)
        server.start()
        try {
            val resolved = DefaultWebPlaylistImportUrlResolver().resolve(
                "${server.baseUrl}/short"
            )

            assertEquals("${server.baseUrl}$finalUrlPath", resolved)
        } finally {
            server.close()
        }
    }
}

private class RedirectHttpServer(
    private val location: String
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress(0), 0)

    val baseUrl: String
        get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/short") { exchange ->
            exchange.responseHeaders.add("Location", location)
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/final/4204621746") { exchange ->
            val payload = "ok".toByteArray()
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
