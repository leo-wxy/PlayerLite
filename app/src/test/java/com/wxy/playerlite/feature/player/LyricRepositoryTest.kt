package com.wxy.playerlite.feature.player

import com.sun.net.httpserver.HttpServer
import com.wxy.playerlite.network.core.AuthHeaderProvider
import com.wxy.playerlite.network.core.JsonHttpClient
import java.io.File
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricRepositoryTest {
    @Test
    fun fetchLyrics_shouldParseTimedLinesAndPersistRawLyric() = runBlocking {
        val directory = createTempDir(prefix = "lyric-repo-test")
        try {
            val repository = DefaultLyricRepository(
                remoteDataSource = FakeLyricRemoteDataSource(
                    payload = jsonObject(
                        """
                        {
                          "code": 200,
                          "lrc": {
                            "lyric": "[00:12.00]天空好想下雨\n[00:34.56]我好想住你隔壁\n[ar:周杰伦]"
                          }
                        }
                        """
                    )
                ),
                localStore = LyricLocalStore(directory = directory)
            )

            val lyric = repository.fetchLyrics("33894312")

            requireNotNull(lyric)
            assertEquals("33894312", lyric.songId)
            assertEquals(2, lyric.lines.size)
            assertEquals(12_000L, lyric.lines[0].timestampMs)
            assertEquals("天空好想下雨", lyric.lines[0].text)
            assertEquals(34_560L, lyric.lines[1].timestampMs)
            assertEquals("我好想住你隔壁", lyric.lines[1].text)
            assertTrue(File(directory, "33894312.lrc").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun readCachedLyrics_shouldParseExistingLocalLyricFile() = runBlocking {
        val directory = createTempDir(prefix = "lyric-cache-test")
        try {
            val localStore = LyricLocalStore(directory = directory)
            localStore.write(
                songId = "1973665667",
                rawLyric = "[00:01.50]从前从前\n[00:02.00]有个人爱你很久"
            )
            val repository = DefaultLyricRepository(
                remoteDataSource = FakeLyricRemoteDataSource(
                    payload = jsonObject("""{"code":200,"lrc":{"lyric":""}}""")
                ),
                localStore = localStore
            )

            val lyric = repository.readCachedLyrics("1973665667")

            requireNotNull(lyric)
            assertEquals(2, lyric.lines.size)
            assertEquals("从前从前", lyric.lines[0].text)
            assertEquals("有个人爱你很久", lyric.lines[1].text)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun fetchLyrics_shouldReturnNullWhenPrimaryLyricMissing() = runBlocking {
        val directory = createTempDir(prefix = "lyric-empty-test")
        try {
            val repository = DefaultLyricRepository(
                remoteDataSource = FakeLyricRemoteDataSource(
                    payload = jsonObject(
                        """
                        {
                          "code": 200,
                          "lrc": {
                            "lyric": ""
                          }
                        }
                        """
                    )
                ),
                localStore = LyricLocalStore(directory = directory)
            )

            val lyric = repository.fetchLyrics("33894312")

            assertNull(lyric)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun fetchLyricsRequest_shouldUsePublicEndpointWithoutForwardingAuthHeaders() = runBlocking {
        val server = LyricHttpServer(
            payload = """
                {
                  "code": 200,
                  "lrc": {
                    "lyric": "[00:01.00]晴天"
                  }
                }
            """.trimIndent()
        )
        val directory = createTempDir(prefix = "lyric-http-test")
        server.start()
        try {
            val client = JsonHttpClient(
                baseUrl = server.baseUrl,
                authHeaderProvider = AuthHeaderProvider {
                    mapOf(
                        "Cookie" to "MUSIC_U=token; __csrf=csrf-token;",
                        "X-CSRF-Token" to "csrf-token"
                    )
                }
            )
            val remoteDataSource = NeteaseLyricRemoteDataSource(client)
            val repository = DefaultLyricRepository(
                remoteDataSource = remoteDataSource,
                localStore = LyricLocalStore(directory = directory)
            )

            repository.fetchLyrics("33894312")

            assertEquals(listOf("/lyric?id=33894312"), server.requestPaths)
            assertEquals(listOf(""), server.cookieHeaders)
            assertEquals(listOf(""), server.csrfHeaders)
        } finally {
            server.close()
            directory.deleteRecursively()
        }
    }
}

private class FakeLyricRemoteDataSource(
    private val payload: JsonObject
) : LyricRemoteDataSource {
    override suspend fun fetchLyrics(songId: String): JsonObject = payload
}

private class LyricHttpServer(
    private val payload: String
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
            val bytes = payload.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { output ->
                output.write(bytes)
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

private fun jsonObject(raw: String): JsonObject {
    return Json.parseToJsonElement(raw.trimIndent()).jsonObject
}
