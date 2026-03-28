package com.wxy.playerlite.feature.webplaylistimport

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface WebPlaylistImportUrlResolver {
    suspend fun resolve(rawUrl: String): String
}

internal class DefaultWebPlaylistImportUrlResolver(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 5_000
) : WebPlaylistImportUrlResolver {
    override suspend fun resolve(rawUrl: String): String = withContext(ioDispatcher) {
        val normalized = rawUrl.trim()
        if (normalized.isBlank()) {
            return@withContext normalized
        }
        runCatching {
            val connection = (URL(normalized).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            }
            connection.useAndResolveFinalUrl()
        }.getOrDefault(normalized)
    }

    private fun HttpURLConnection.useAndResolveFinalUrl(): String {
        return try {
            responseCode
            inputStream?.close()
            url.toString()
        } finally {
            disconnect()
        }
    }

    private companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
    }
}
