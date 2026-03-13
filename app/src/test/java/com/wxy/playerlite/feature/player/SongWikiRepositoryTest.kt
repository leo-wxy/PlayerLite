package com.wxy.playerlite.feature.player

import com.sun.net.httpserver.HttpServer
import com.wxy.playerlite.network.core.AuthHeaderProvider
import com.wxy.playerlite.network.core.JsonHttpClient
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongWikiRepositoryTest {
    @Test
    fun fetchSongWiki_shouldMapCompactSummaryFromBasicBlock() = runBlocking {
        val repository = DefaultSongWikiRepository(
            remoteDataSource = FakeSongWikiRemoteDataSource(
                payload = jsonObject(
                    """
                    {
                      "code": 200,
                      "data": {
                        "blocks": [
                          {
                            "code": "SONG_PLAY_ABOUT_SONG_BASIC",
                            "uiElement": {
                              "mainTitle": { "title": "音乐百科" },
                              "images": [
                                { "imageUrl": "http://example.com/wiki-cover.jpg" }
                              ],
                              "textLinks": [
                                { "text": "参与共建" }
                              ]
                            },
                            "creatives": [
                              {
                                "creativeType": "songTag",
                                "uiElement": {
                                  "mainTitle": { "title": "曲风" }
                                },
                                "resources": [
                                  {
                                    "uiElement": {
                                      "mainTitle": { "title": "流行-华语流行" }
                                    }
                                  }
                                ]
                              },
                              {
                                "creativeType": "songBizTag",
                                "uiElement": {
                                  "mainTitle": { "title": "推荐标签" }
                                },
                                "resources": [
                                  {
                                    "uiElement": {
                                      "mainTitle": { "title": "治愈" }
                                    }
                                  },
                                  {
                                    "uiElement": {
                                      "mainTitle": { "title": "悲伤" }
                                    }
                                  }
                                ]
                              },
                              {
                                "creativeType": "language",
                                "uiElement": {
                                  "mainTitle": { "title": "语种" },
                                  "textLinks": [
                                    { "text": "国语" }
                                  ]
                                },
                                "resources": []
                              },
                              {
                                "creativeType": "bpm",
                                "uiElement": {
                                  "mainTitle": { "title": "BPM" },
                                  "textLinks": [
                                    { "text": "75" }
                                  ]
                                },
                                "resources": []
                              },
                              {
                                "creativeType": "sheet",
                                "uiElement": {
                                  "mainTitle": { "title": "乐谱" },
                                  "buttons": [
                                    { "text": "2个" }
                                  ]
                                },
                                "resources": [
                                  {
                                    "uiElement": {
                                      "images": [
                                        { "title": "吉他" }
                                      ]
                                    }
                                  }
                                ]
                              },
                              {
                                "creativeType": "songComment",
                                "uiElement": {
                                  "mainTitle": { "title": "乐评" }
                                },
                                "resources": [
                                  {
                                    "uiElement": {
                                      "descriptions": [
                                        { "description": "这条不应该被映射进简要百科" }
                                      ]
                                    }
                                  }
                                ]
                              }
                            ]
                          }
                        ]
                      }
                    }
                    """
                )
            )
        )

        val summary = repository.fetchSongWiki("1973665667")

        requireNotNull(summary)
        assertEquals("音乐百科", summary.title)
        assertEquals("http://example.com/wiki-cover.jpg", summary.coverUrl)
        assertEquals("参与共建", summary.contributionText)
        assertEquals(5, summary.sections.size)
        assertEquals("曲风", summary.sections[0].title)
        assertEquals(listOf("流行-华语流行"), summary.sections[0].values)
        assertEquals("推荐标签", summary.sections[1].title)
        assertEquals(listOf("治愈", "悲伤"), summary.sections[1].values)
        assertEquals("乐谱", summary.sections[4].title)
        assertEquals(listOf("2个", "吉他"), summary.sections[4].values)
    }

    @Test
    fun fetchSongWiki_shouldReturnNullWhenBasicBlockMissing() = runBlocking {
        val repository = DefaultSongWikiRepository(
            remoteDataSource = FakeSongWikiRemoteDataSource(
                payload = jsonObject(
                    """
                    {
                      "code": 200,
                      "data": {
                        "blocks": [
                          {
                            "code": "SONG_PLAY_ABOUT_SIMILAR_SONG",
                            "creatives": []
                          }
                        ]
                      }
                    }
                    """
                )
            )
        )

        val summary = repository.fetchSongWiki("1973665667")

        assertNull(summary)
    }

    @Test
    fun fetchSongWikiRequest_shouldUseProtectedEndpointAndForwardAuthHeaders() = runBlocking {
        val server = SongWikiHttpServer(
            payload = """
                {
                  "code": 200,
                  "data": {
                    "blocks": []
                  }
                }
            """.trimIndent()
        )
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
            val remoteDataSource = NeteaseSongWikiRemoteDataSource(client)

            remoteDataSource.fetchSongWiki("1973665667")

            assertEquals(listOf("/song/wiki/summary?id=1973665667"), server.requestPaths)
            assertTrue(server.cookieHeaders.single().contains("MUSIC_U=token"))
            assertEquals(listOf("csrf-token"), server.csrfHeaders)
        } finally {
            server.close()
        }
    }
}

private class FakeSongWikiRemoteDataSource(
    private val payload: JsonObject
) : SongWikiRemoteDataSource {
    override suspend fun fetchSongWiki(songId: String): JsonObject = payload
}

private class SongWikiHttpServer(
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
