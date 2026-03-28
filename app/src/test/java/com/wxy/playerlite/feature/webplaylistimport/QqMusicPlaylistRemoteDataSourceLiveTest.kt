package com.wxy.playerlite.feature.webplaylistimport

import com.wxy.playerlite.network.core.JsonHttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class QqMusicPlaylistRemoteDataSourceLiveTest {
    @Test
    fun fetchPlaylistDetail_shouldReturnStructuredPayloadFromQqUpstream() = runBlocking {
        assumeTrue(
            "Set RUN_LIVE_NETWORK_TESTS=true to execute live QQ playlist import test",
            System.getenv("RUN_LIVE_NETWORK_TESTS")?.equals("true", ignoreCase = true) == true
        )

        val remoteDataSource = DefaultQqMusicPlaylistRemoteDataSource(
            httpClient = JsonHttpClient(baseUrl = QQ_MUSIC_API_BASE_URL)
        )

        val payload = remoteDataSource.fetchPlaylistDetail("4204621746")
        val playlist = payload["cdlist"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?: error("qq upstream returned empty cdlist")

        assertEquals("0", payload["code"]?.jsonPrimitive?.content)
        assertEquals("4204621746", playlist["disstid"]?.jsonPrimitive?.content)
        assertTrue(playlist["dissname"]?.jsonPrimitive?.content.orEmpty().isNotBlank())
        assertTrue(playlist["nickname"]?.jsonPrimitive?.content.orEmpty().isNotBlank())
        assertTrue(playlist["songlist"]?.jsonArray.orEmpty().isNotEmpty())
    }
}
