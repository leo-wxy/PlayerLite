package com.wxy.playerlite.feature.song

import com.sun.net.httpserver.HttpServer
import com.wxy.playerlite.network.core.AuthHeaderProvider
import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.user.UserSessionInvalidException
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongFavoriteRepositoryTest {
    @Test
    fun favoriteSong_shouldReturnSuccessWhenResponseCodeIs200() = runBlocking {
        val server = SongFavoriteHttpServer(
            responseBody = """{"code":200}"""
        )
        server.start()
        try {
            val repository = createRepository(server)

            val result = repository.favoriteSong("1973665667")

            assertTrue(result.isSuccess)
            assertEquals(listOf("/like?id=1973665667&like=true"), server.requestPaths)
            assertTrue(server.cookieHeaders.single().contains("MUSIC_U=token"))
            assertEquals(listOf("csrf-token"), server.csrfHeaders)
        } finally {
            server.close()
        }
    }

    @Test
    fun favoriteSong_shouldReturnSessionInvalidWhenResponseCodeIs301() = runBlocking {
        val server = SongFavoriteHttpServer(
            responseBody = """{"code":301,"message":"需要登录"}"""
        )
        server.start()
        try {
            val repository = createRepository(server)

            val result = repository.favoriteSong("1973665667")

            assertTrue(result.exceptionOrNull() is UserSessionInvalidException)
        } finally {
            server.close()
        }
    }

    @Test
    fun favoriteSong_shouldReturnSessionInvalidWhenResponseCodeIs302() = runBlocking {
        val server = SongFavoriteHttpServer(
            responseBody = """{"code":302,"message":"登录已过期"}"""
        )
        server.start()
        try {
            val repository = createRepository(server)

            val result = repository.favoriteSong("1973665667")

            assertTrue(result.exceptionOrNull() is UserSessionInvalidException)
        } finally {
            server.close()
        }
    }

    @Test
    fun favoriteSong_shouldReturnFailureWhenResponseCodeIsNotSuccess() = runBlocking {
        val server = SongFavoriteHttpServer(
            responseBody = """{"code":500,"message":"收藏失败"}"""
        )
        server.start()
        try {
            val repository = createRepository(server)

            val result = repository.favoriteSong("1973665667")

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals("收藏失败", result.exceptionOrNull()?.message)
        } finally {
            server.close()
        }
    }

    private fun createRepository(server: SongFavoriteHttpServer): SongFavoriteRepository {
        return DefaultSongFavoriteRepository(
            remoteDataSource = NeteaseSongFavoriteRemoteDataSource(
                httpClient = JsonHttpClient(
                    baseUrl = server.baseUrl,
                    authHeaderProvider = AuthHeaderProvider {
                        mapOf(
                            "Cookie" to "MUSIC_U=token; __csrf=csrf-token;",
                            "X-CSRF-Token" to "csrf-token"
                        )
                    }
                )
            )
        )
    }
}

private class SongFavoriteHttpServer(
    private val responseBody: String
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress(0), 0)

    val requestPaths = mutableListOf<String>()
    val cookieHeaders = mutableListOf<String>()
    val csrfHeaders = mutableListOf<String>()

    val baseUrl: String
        get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { exchange ->
            requestPaths += buildString {
                append(exchange.requestURI.path)
                exchange.requestURI.rawQuery?.let { query ->
                    append('?')
                    append(query)
                }
            }
            cookieHeaders += exchange.requestHeaders.getFirst("Cookie").orEmpty()
            csrfHeaders += exchange.requestHeaders.getFirst("X-CSRF-Token").orEmpty()
            val bytes = responseBody.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    fun start() {
        server.start()
    }

    override fun close() {
        server.stop(0)
    }
}
