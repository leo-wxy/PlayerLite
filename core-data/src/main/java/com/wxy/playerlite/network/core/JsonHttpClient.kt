package com.wxy.playerlite.network.core

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class JsonHttpClient(
    private val baseUrl: String,
    private val authHeaderProvider: AuthHeaderProvider = AuthHeaderProvider { emptyMap() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    connectTimeoutMs: Int = 8_000,
    readTimeoutMs: Int = 8_000
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val requiresAuth = request.header(HEADER_REQUIRE_AUTH) == TRUE_VALUE
            val builder = request.newBuilder()
                .removeHeader(HEADER_REQUIRE_AUTH)
            if (requiresAuth) {
                authHeaderProvider.currentAuthHeaders().forEach { (key, value) ->
                    if (value.isNotBlank()) {
                        builder.header(key, value)
                    }
                }
            }
            chain.proceed(builder.build())
        }
        .build()
    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun get(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
        requiresAuth: Boolean = false,
        headers: Map<String, String> = emptyMap()
    ): JsonObject = withContext(ioDispatcher) {
        val urlBuilder = buildUrl(path).newBuilder()
        queryParams.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        executeJson(
            Request.Builder()
                .url(urlBuilder.build())
                .get()
                .applyAuthMarker(requiresAuth)
                .applyExtraHeaders(headers)
                .build()
        )
    }

    suspend fun postForm(
        path: String,
        formParams: Map<String, String>,
        requiresAuth: Boolean = false,
        headers: Map<String, String> = emptyMap()
    ): JsonObject = withContext(ioDispatcher) {
        val formBody = FormBody.Builder().apply {
            formParams.forEach { (key, value) ->
                add(key, value)
            }
        }.build()
        executeJson(
            Request.Builder()
                .url(buildUrl(path))
                .post(formBody)
                .applyAuthMarker(requiresAuth)
                .applyExtraHeaders(headers)
                .build()
        )
    }

    private fun executeJson(request: Request): JsonObject {
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    throw NetworkRequestException(
                        message = "Empty response body",
                        statusCode = response.code
                    )
                }
                val payload: JsonObject = runCatching {
                    json.parseToJsonElement(body).jsonObject
                }.getOrElse {
                    throw NetworkRequestException(
                        message = "Invalid JSON response",
                        statusCode = response.code
                    )
                }
                buildJsonObject {
                    payload.entries.forEach { entry ->
                        put(entry.key, entry.value)
                    }
                    put(KEY_HTTP_STATUS, JsonPrimitive(response.code))
                }
            }
        } catch (error: IOException) {
            throw NetworkRequestException(
                message = error.message ?: "Network request failed"
            )
        }
    }

    private fun buildUrl(path: String) = baseUrl
        .trimEnd('/')
        .plus(if (path.startsWith("/")) path else "/$path")
        .toHttpUrl()

    private fun Request.Builder.applyAuthMarker(requiresAuth: Boolean): Request.Builder {
        return if (requiresAuth) {
            header(HEADER_REQUIRE_AUTH, TRUE_VALUE)
        } else {
            this
        }
    }

    private fun Request.Builder.applyExtraHeaders(headers: Map<String, String>): Request.Builder {
        headers.forEach { (key, value) ->
            if (value.isNotBlank()) {
                header(key, value)
            }
        }
        return this
    }

    companion object {
        private const val HEADER_REQUIRE_AUTH = "X-PlayerLite-Require-Auth"
        private const val TRUE_VALUE = "true"
        const val KEY_HTTP_STATUS = "_httpStatus"
    }
}
