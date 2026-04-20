package com.wxy.playerlite.playback.process

import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackPreviewClip
import com.wxy.playerlite.playback.model.SongAudioQualityCatalog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal data class SourceMetadata(
    val id: String,
    val name: String,
    val author: String? = null,
    val version: String? = null,
    val type: String = ""
)

internal data class SourceState(
    val enabled: Boolean = true,
    val active: Boolean = false,
    val initError: String? = null,
    val detailMessage: String? = null
)

internal sealed interface SourceConfig {
    val type: String
}

internal data class NeteaseCompatibleSourceConfig(
    val baseUrl: String
) : SourceConfig {
    override val type: String = TYPE

    companion object {
        const val TYPE = "netease-compatible"
    }
}

internal enum class HttpMappingMethod {
    GET,
    POST
}

internal data class HttpMappingRequestConfig(
    val method: HttpMappingMethod,
    val url: String,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val jsonBody: Any? = null
)

internal data class HttpMappingStreamConfig(
    val headers: Map<String, String> = emptyMap(),
    val inheritRequestHeaders: Boolean = false
)

internal data class HttpMappingResponseConfig(
    val playbackUrlPath: String,
    val durationMsPath: String? = null,
    val contentLengthBytesPath: String? = null,
    val expiresAtMsPath: String? = null,
    val errorMessagePath: String? = null,
    val previewStartMsPath: String? = null,
    val previewEndMsPath: String? = null
)

internal data class HttpMappingSourceConfig(
    val request: HttpMappingRequestConfig,
    val stream: HttpMappingStreamConfig? = null,
    val response: HttpMappingResponseConfig,
    val qualityMap: Map<PlaybackAudioQuality, String> = emptyMap()
) : SourceConfig {
    override val type: String = TYPE

    companion object {
        const val TYPE = "http-mapping"
    }
}

internal sealed interface SourceAction {
    data object ResolveMusicUrl : SourceAction
    data object ResolveLyric : SourceAction
    data object ResolvePic : SourceAction
}

internal data class SourceActionContext(
    val songId: String?,
    val title: String,
    val artistText: String?,
    val albumTitle: String?,
    val durationMs: Long,
    val preferredAudioQuality: PlaybackAudioQuality,
    val requestHeaders: Map<String, String>,
    val previewClip: PlaybackPreviewClip?
)

internal sealed interface SourceActionResult {
    data class MusicUrl(
        val playbackUrl: String,
        val requestHeaders: Map<String, String> = emptyMap(),
        val contentLengthBytes: Long? = null,
        val durationMs: Long? = null,
        val expiresAtMs: Long? = null,
        val previewClip: PlaybackPreviewClip? = null,
        val appliedAudioQuality: PlaybackAudioQuality
    ) : SourceActionResult
}

internal data class SourceMusicUrlPreview(
    val appliedAudioQuality: PlaybackAudioQuality,
    val legacyFallbackBitrate: Int?,
    val preferredClipMode: OnlineClipMode
)

internal interface SourceAdapter {
    val metadata: SourceMetadata
    val normalizedConfigJson: String?

    fun init(): Result<SourceState>

    suspend fun previewResolveMusicUrl(
        context: SourceActionContext
    ): Result<SourceMusicUrlPreview> = Result.failure(
        UnsupportedOperationException("ResolveMusicUrl preview is not supported for ${metadata.id}")
    )

    suspend fun handle(
        action: SourceAction,
        context: SourceActionContext
    ): Result<SourceActionResult>

    fun clearCaches() = Unit
}

internal interface SourceAdapterFactory {
    fun create(configJson: String?): Result<SourceAdapter>
}

internal class DefaultSourceAdapterFactory(
    private val builtInBaseUrl: String
) : SourceAdapterFactory {
    override fun create(configJson: String?): Result<SourceAdapter> {
        if (configJson == null) {
            return Result.success(
                NeteaseCompatibleSourceAdapter(
                    metadata = SourceMetadata(
                        id = "builtin-default-source",
                        name = "Built-in Netease Source",
                        type = NeteaseCompatibleSourceConfig.TYPE
                    ),
                    config = NeteaseCompatibleSourceConfig(
                        baseUrl = normalizeBaseUrl(builtInBaseUrl)
                    ),
                    normalizedConfigJson = null
                )
            )
        }
        return parseSourceConfigJson(configJson).map { parsed ->
            when (val config = parsed.config) {
                is NeteaseCompatibleSourceConfig -> {
                    NeteaseCompatibleSourceAdapter(
                        metadata = SourceMetadata(
                            id = "source-${config.type}",
                            name = "Netease Compatible Source",
                            type = config.type
                        ),
                        config = config,
                        normalizedConfigJson = parsed.normalizedConfigJson
                    )
                }

                is HttpMappingSourceConfig -> {
                    HttpMappingSourceAdapter(
                        metadata = SourceMetadata(
                            id = "source-${config.type}",
                            name = "HTTP Mapping Source",
                            type = config.type
                        ),
                        config = config,
                        normalizedConfigJson = parsed.normalizedConfigJson
                    )
                }
            }
        }
    }
}

internal class NeteaseCompatibleSourceAdapter(
    override val metadata: SourceMetadata,
    private val config: NeteaseCompatibleSourceConfig,
    override val normalizedConfigJson: String?,
    private val catalogProvider: CachedSongAudioQualityCatalogProvider =
        CachedSongAudioQualityCatalogProvider(
            remoteDataSource = NeteaseSongAudioQualityCatalogRemoteDataSource(
                baseUrlProvider = { config.baseUrl }
            ),
            sourceIdentityProvider = { config.baseUrl }
        ),
    private val resolver: OnlinePlaybackResolver = OnlinePlaybackUrlResolver(
        remoteDataSource = NeteaseOnlinePlaybackRemoteDataSource(
            baseUrlProvider = { config.baseUrl }
        ),
        sourceIdentityProvider = { config.baseUrl }
    )
) : SourceAdapter {
    override fun init(): Result<SourceState> {
        return if (config.baseUrl.isBlank()) {
            Result.failure(IllegalArgumentException("Missing baseUrl"))
        } else {
            Result.success(SourceState())
        }
    }

    override suspend fun previewResolveMusicUrl(
        context: SourceActionContext
    ): Result<SourceMusicUrlPreview> {
        return buildSourceMusicUrlPreview(
            context = context,
            audioQualityCatalogProvider = catalogProvider::getCatalog
        )
    }

    override suspend fun handle(
        action: SourceAction,
        context: SourceActionContext
    ): Result<SourceActionResult> {
        return when (action) {
            SourceAction.ResolveMusicUrl -> resolveMusicUrl(context)
            SourceAction.ResolveLyric,
            SourceAction.ResolvePic -> Result.failure(
                UnsupportedOperationException("Action $action is not supported yet")
            )
        }
    }

    override fun clearCaches() {
        (resolver as? OnlinePlaybackUrlResolver)?.clear()
        catalogProvider.clear()
    }

    private suspend fun resolveMusicUrl(
        context: SourceActionContext
    ): Result<SourceActionResult> {
        val preview = previewResolveMusicUrl(context)
            .getOrElse { return Result.failure(it) }
        val songId = context.songId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(
                OnlinePlaybackResolutionException(
                    OnlinePlaybackFailure(
                        kind = OnlinePlaybackFailureKind.UNSUPPORTED,
                        message = "Missing songId for netease source"
                    )
                )
            )
        val resolved = resolver.resolve(
            songId = songId,
            requestHeaders = context.requestHeaders,
            requestedLevel = preview.appliedAudioQuality.wireValue,
            fallbackBitrate = preview.legacyFallbackBitrate,
            preferredClipMode = preview.preferredClipMode,
            expectedDurationMs = context.durationMs
        )
        return resolved.map { stream ->
            SourceActionResult.MusicUrl(
                playbackUrl = stream.playbackUrl,
                requestHeaders = stream.requestHeaders,
                contentLengthBytes = stream.contentLengthBytes,
                durationMs = stream.durationMs,
                expiresAtMs = stream.expiresAtMs,
                previewClip = stream.previewClip ?: context.previewClip,
                appliedAudioQuality = preview.appliedAudioQuality
            )
        }
    }
}

internal data class ExecutedHttpMappingRequest(
    val method: String,
    val url: String,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val jsonBody: JsonObject? = null
)

internal class HttpMappingSourceAdapter(
    override val metadata: SourceMetadata,
    private val config: HttpMappingSourceConfig,
    override val normalizedConfigJson: String? = null,
    private val executeRequest: suspend (ExecutedHttpMappingRequest) -> JsonObject =
        ::defaultExecuteHttpMappingRequest
) : SourceAdapter {
    override fun init(): Result<SourceState> {
        return Result.success(SourceState())
    }

    override suspend fun previewResolveMusicUrl(
        context: SourceActionContext
    ): Result<SourceMusicUrlPreview> {
        return Result.success(
            SourceMusicUrlPreview(
                appliedAudioQuality = context.preferredAudioQuality,
                legacyFallbackBitrate = null,
                preferredClipMode = if (context.previewClip != null) {
                    OnlineClipMode.PREVIEW
                } else {
                    OnlineClipMode.FULL
                }
            )
        )
    }

    override suspend fun handle(
        action: SourceAction,
        context: SourceActionContext
    ): Result<SourceActionResult> {
        return when (action) {
            SourceAction.ResolveMusicUrl -> resolveMusicUrl(context)
            SourceAction.ResolveLyric,
            SourceAction.ResolvePic -> Result.failure(
                UnsupportedOperationException("Action $action is not supported yet")
            )
        }
    }

    private suspend fun resolveMusicUrl(
        context: SourceActionContext
    ): Result<SourceActionResult> {
        val templateContext = HttpMappingTemplateContext(
            songId = context.songId,
            title = context.title,
            artistText = context.artistText,
            albumTitle = context.albumTitle,
            durationMs = context.durationMs,
            quality = config.qualityMap[context.preferredAudioQuality]
                ?: context.preferredAudioQuality.wireValue,
            requestHeaders = context.requestHeaders
        )
        val renderedQuery = renderTemplateMap(config.request.query, templateContext)
        val renderedHeaders = renderTemplateMap(config.request.headers, templateContext)
        val renderedBody = renderJsonTemplate(config.request.jsonBody, templateContext)?.jsonObject
        val response = executeRequest(
            ExecutedHttpMappingRequest(
                method = config.request.method.name,
                url = config.request.url,
                query = renderedQuery,
                headers = renderedHeaders,
                jsonBody = renderedBody
            )
        )
        val playbackUrl = extractJsonString(response, config.response.playbackUrlPath)
            ?: return Result.failure(
                OnlinePlaybackResolutionException(
                    OnlinePlaybackFailure(
                        kind = OnlinePlaybackFailureKind.RESOURCE_UNAVAILABLE,
                        message = config.response.errorMessagePath
                            ?.let { extractJsonString(response, it) }
                            ?: "Failed to resolve playbackUrl"
                    )
                )
            )
        val previewStartMs = config.response.previewStartMsPath
            ?.let { extractJsonLong(response, it) }
        val previewEndMs = config.response.previewEndMsPath
            ?.let { extractJsonLong(response, it) }
        val previewClip = if (
            previewStartMs != null &&
            previewEndMs != null &&
            previewEndMs > previewStartMs
        ) {
            PlaybackPreviewClip(previewStartMs, previewEndMs)
        } else {
            null
        }
        val streamHeaders = buildMap {
            if (config.stream?.inheritRequestHeaders == true) {
                putAll(context.requestHeaders.filterValues { it.isNotBlank() })
            }
            config.stream?.headers
                ?.let { renderTemplateMap(it, templateContext) }
                ?.let(::putAll)
        }
        return Result.success(
            SourceActionResult.MusicUrl(
                playbackUrl = playbackUrl,
                requestHeaders = streamHeaders,
                contentLengthBytes = config.response.contentLengthBytesPath
                    ?.let { extractJsonLong(response, it) },
                durationMs = config.response.durationMsPath
                    ?.let { extractJsonLong(response, it) },
                expiresAtMs = config.response.expiresAtMsPath
                    ?.let { extractJsonLong(response, it) },
                previewClip = previewClip,
                appliedAudioQuality = context.preferredAudioQuality
            )
        )
    }
}

internal class ResolverBackedSourceAdapter(
    override val metadata: SourceMetadata = SourceMetadata(
        id = "resolver-backed-source",
        name = "Resolver Backed Source"
    ),
    private val resolver: OnlinePlaybackResolver,
    private val audioQualityCatalogProvider: suspend (String, Map<String, String>) -> SongAudioQualityCatalog? =
        { _, _ -> null }
) : SourceAdapter {
    override val normalizedConfigJson: String? = null

    override fun init(): Result<SourceState> = Result.success(SourceState())

    override suspend fun previewResolveMusicUrl(
        context: SourceActionContext
    ): Result<SourceMusicUrlPreview> {
        return buildSourceMusicUrlPreview(
            context = context,
            audioQualityCatalogProvider = audioQualityCatalogProvider
        )
    }

    override suspend fun handle(
        action: SourceAction,
        context: SourceActionContext
    ): Result<SourceActionResult> {
        return when (action) {
            SourceAction.ResolveMusicUrl -> {
                val songId = context.songId?.takeIf { it.isNotBlank() }
                    ?: return Result.failure(
                        OnlinePlaybackResolutionException(
                            OnlinePlaybackFailure(
                                kind = OnlinePlaybackFailureKind.UNSUPPORTED,
                                message = "Missing songId for online track"
                            )
                        )
                    )
                val preview = previewResolveMusicUrl(context)
                    .getOrElse { return Result.failure(it) }
                resolver.resolve(
                    songId = songId,
                    requestHeaders = context.requestHeaders,
                    requestedLevel = preview.appliedAudioQuality.wireValue,
                    fallbackBitrate = preview.legacyFallbackBitrate,
                    preferredClipMode = preview.preferredClipMode,
                    expectedDurationMs = context.durationMs
                ).map { resolved ->
                    SourceActionResult.MusicUrl(
                        playbackUrl = resolved.playbackUrl,
                        requestHeaders = resolved.requestHeaders,
                        contentLengthBytes = resolved.contentLengthBytes,
                        durationMs = resolved.durationMs,
                        expiresAtMs = resolved.expiresAtMs,
                        previewClip = resolved.previewClip ?: context.previewClip,
                        appliedAudioQuality = preview.appliedAudioQuality
                    )
                }
            }

            SourceAction.ResolveLyric,
            SourceAction.ResolvePic -> Result.failure(
                UnsupportedOperationException("Action $action is not supported yet")
            )
        }
    }
}

private data class ParsedSourceRuntimeConfig(
    val normalizedConfigJson: String,
    val config: SourceConfig
)

private suspend fun buildSourceMusicUrlPreview(
    context: SourceActionContext,
    audioQualityCatalogProvider: suspend (String, Map<String, String>) -> SongAudioQualityCatalog?
): Result<SourceMusicUrlPreview> {
    val songId = context.songId?.takeIf { it.isNotBlank() }
        ?: return Result.failure(IllegalArgumentException("Missing songId for online track"))
    val qualityCatalog = runCatching {
        audioQualityCatalogProvider(songId, context.requestHeaders)
    }.getOrNull()
    val selectedAudioQuality = selectSourceAppliedAudioQuality(
        preferredAudioQuality = context.preferredAudioQuality,
        catalog = qualityCatalog
    )
    return Result.success(
        SourceMusicUrlPreview(
            appliedAudioQuality = selectedAudioQuality.appliedAudioQuality,
            legacyFallbackBitrate = selectedAudioQuality.legacyFallbackBitrate,
            preferredClipMode = if (context.previewClip != null) {
                OnlineClipMode.PREVIEW
            } else {
                OnlineClipMode.FULL
            }
        )
    )
}

private fun parseSourceConfigJson(rawJson: String): Result<ParsedSourceRuntimeConfig> {
    return runCatching {
        val json = JSONObject(rawJson)
        when (json.optString("type").trim()) {
            NeteaseCompatibleSourceConfig.TYPE -> {
                val baseUrl = normalizeBaseUrl(
                    json.optString("baseUrl").trim()
                        .ifEmpty { json.optString("url").trim() }
                )
                require(baseUrl.isNotBlank()) { "Missing baseUrl" }
                require(isValidHttpUrl(baseUrl)) { "Invalid baseUrl" }
                ParsedSourceRuntimeConfig(
                    normalizedConfigJson = JSONObject()
                        .put("type", NeteaseCompatibleSourceConfig.TYPE)
                        .put("baseUrl", baseUrl)
                        .toString(),
                    config = NeteaseCompatibleSourceConfig(baseUrl = baseUrl)
                )
            }

            HttpMappingSourceConfig.TYPE -> {
                val requestJson = json.optJSONObject("request")
                    ?: error("http-mapping missing request")
                val method = requestJson.optString("method").trim().uppercase()
                require(method == "GET" || method == "POST") { "http-mapping only supports GET/POST" }
                val requestUrl = requestJson.optString("url").trim()
                require(isValidHttpUrl(requestUrl)) { "Invalid request.url" }
                if (method == "GET" && requestJson.has("jsonBody")) {
                    error("GET request does not support jsonBody")
                }
                val responseJson = json.optJSONObject("response")
                    ?: error("http-mapping missing response")
                val playbackUrlPath = responseJson.optString("playbackUrl").trim()
                require(playbackUrlPath.isNotBlank()) { "Missing response.playbackUrl" }
                val requestConfig = HttpMappingRequestConfig(
                    method = HttpMappingMethod.valueOf(method),
                    url = requestUrl,
                    query = requestJson.optJSONObject("query")?.toStringMap().orEmpty(),
                    headers = requestJson.optJSONObject("headers")?.toStringMap().orEmpty(),
                    jsonBody = requestJson.opt("jsonBody")?.let(::jsonValueToKotlin)
                )
                requestConfig.query.values.forEach(::validateTemplateVariables)
                requestConfig.headers.values.forEach(::validateTemplateVariables)
                validateJsonTemplate(requestConfig.jsonBody)
                val streamConfig = json.optJSONObject("stream")?.let { streamJson ->
                    val headers = streamJson.optJSONObject("headers")?.toStringMap().orEmpty()
                    headers.values.forEach(::validateTemplateVariables)
                    HttpMappingStreamConfig(
                        headers = headers,
                        inheritRequestHeaders = streamJson.optBoolean("inheritRequestHeaders", false)
                    )
                }
                val qualityMap = json.optJSONObject("qualityMap")
                    ?.let { qualityJson ->
                        buildMap {
                            qualityJson.keys().forEach { key ->
                                val quality = parsePlaybackAudioQualityKey(key)
                                    ?: error("Unsupported qualityMap key: $key")
                                put(quality, qualityJson.optString(key).trim())
                            }
                        }
                    }
                    .orEmpty()
                ParsedSourceRuntimeConfig(
                    normalizedConfigJson = normalizeSourceRuntimeJson(json).toString(),
                    config = HttpMappingSourceConfig(
                        request = requestConfig,
                        stream = streamConfig,
                        response = HttpMappingResponseConfig(
                            playbackUrlPath = playbackUrlPath,
                            durationMsPath = responseJson.optString("durationMs").trim().ifBlank { null },
                            contentLengthBytesPath = responseJson.optString("contentLengthBytes").trim().ifBlank { null },
                            expiresAtMsPath = responseJson.optString("expiresAtMs").trim().ifBlank { null },
                            errorMessagePath = responseJson.optString("errorMessage").trim().ifBlank { null },
                            previewStartMsPath = responseJson.optString("previewStartMs").trim().ifBlank { null },
                            previewEndMsPath = responseJson.optString("previewEndMs").trim().ifBlank { null }
                        ),
                        qualityMap = qualityMap
                    )
                )
            }

            else -> error("Unsupported source config type")
        }
    }
}

internal suspend fun defaultExecuteHttpMappingRequest(
    request: ExecutedHttpMappingRequest
): JsonObject {
    val client = OkHttpClient()
    val url = request.url
    val finalUrl = buildString {
        append(url)
        if (request.query.isNotEmpty()) {
            append(if ('?' in url) '&' else '?')
            append(
                request.query.entries.joinToString("&") { (key, value) ->
                    "${java.net.URLEncoder.encode(key, "UTF-8")}=${
                        java.net.URLEncoder.encode(value, "UTF-8")
                    }"
                }
            )
        }
    }
    val builder = Request.Builder().url(finalUrl)
    request.headers.forEach { (key, value) ->
        if (value.isNotBlank()) {
            builder.header(key, value)
        }
    }
    val prepared = when (request.method) {
        "POST" -> {
            val payload = (request.jsonBody ?: buildJsonObject { }).toString()
            builder.post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
        }

        else -> builder.get()
    }.build()
    client.newCall(prepared).execute().use { response ->
        val body = response.body?.string().orEmpty()
        return Json.parseToJsonElement(body).jsonObject
    }
}

private data class HttpMappingTemplateContext(
    val songId: String?,
    val title: String,
    val artistText: String?,
    val albumTitle: String?,
    val durationMs: Long,
    val quality: String,
    val requestHeaders: Map<String, String>
)

private data class SelectedSourceAudioQuality(
    val appliedAudioQuality: PlaybackAudioQuality,
    val legacyFallbackBitrate: Int?
)

private fun selectSourceAppliedAudioQuality(
    preferredAudioQuality: PlaybackAudioQuality,
    catalog: SongAudioQualityCatalog?
): SelectedSourceAudioQuality {
    val options = catalog?.options.orEmpty()
    if (options.isEmpty()) {
        return SelectedSourceAudioQuality(
            appliedAudioQuality = preferredAudioQuality,
            legacyFallbackBitrate = null
        )
    }
    val uniqueOptions = options
        .sortedWith(
            compareByDescending<com.wxy.playerlite.playback.model.SongAudioQualityOption> { it.quality.sortOrder }
                .thenBy { it.rawKey }
        )
        .distinctBy { it.quality }
    val optionsByQuality = uniqueOptions.associateBy { it.quality }
    val exactMatch = optionsByQuality[preferredAudioQuality]
    val appliedOption = exactMatch
        ?: PlaybackAudioQuality.descendingPreference
            .dropWhile { it != preferredAudioQuality }
            .drop(1)
            .firstNotNullOfOrNull { optionsByQuality[it] }
        ?: uniqueOptions.first()
    val fallbackBitrate = if (appliedOption.bitRate <= 0) {
        null
    } else if (appliedOption.quality.sortOrder <= PlaybackAudioQuality.LOSSLESS.sortOrder) {
        appliedOption.bitRate
    } else {
        uniqueOptions.firstOrNull { option ->
            option.bitRate > 0 &&
                option.quality.sortOrder <= PlaybackAudioQuality.LOSSLESS.sortOrder
        }?.bitRate ?: appliedOption.bitRate
    }
    return SelectedSourceAudioQuality(
        appliedAudioQuality = appliedOption.quality,
        legacyFallbackBitrate = fallbackBitrate
    )
}

private fun parsePlaybackAudioQualityKey(raw: String): PlaybackAudioQuality? {
    return PlaybackAudioQuality.entries.firstOrNull { candidate ->
        candidate.name.equals(raw, ignoreCase = true) ||
            candidate.wireValue.equals(raw, ignoreCase = true)
    }
}

private fun renderTemplateMap(
    templates: Map<String, String>,
    context: HttpMappingTemplateContext
): Map<String, String> {
    return buildMap {
        templates.forEach { (key, template) ->
            renderTemplate(template, context)?.let { put(key, it) }
        }
    }
}

private fun renderTemplate(
    template: String,
    context: HttpMappingTemplateContext
): String? {
    val matches = TEMPLATE_PLACEHOLDER_REGEX.findAll(template).toList()
    if (matches.isEmpty()) {
        return template.takeIf { it.isNotBlank() }
    }
    val rendered = buildString {
        var lastIndex = 0
        matches.forEach { match ->
            append(template.substring(lastIndex, match.range.first))
            val replacement = resolvePlaceholder(match.groupValues[1], context) ?: return null
            append(replacement)
            lastIndex = match.range.last + 1
        }
        append(template.substring(lastIndex))
    }
    return rendered.takeIf { it.isNotBlank() }
}

private fun resolvePlaceholder(
    placeholder: String,
    context: HttpMappingTemplateContext
): String? {
    return when {
        placeholder == "songId" -> context.songId
        placeholder == "title" -> context.title
        placeholder == "artistText" -> context.artistText
        placeholder == "albumTitle" -> context.albumTitle
        placeholder == "durationMs" -> context.durationMs.toString()
        placeholder == "quality" -> context.quality
        placeholder.startsWith("header.") -> {
            val headerName = placeholder.removePrefix("header.")
            context.requestHeaders.entries.firstOrNull { it.key.equals(headerName, ignoreCase = true) }?.value
        }

        else -> null
    }?.takeIf { it.isNotBlank() }
}

private fun renderJsonTemplate(
    value: Any?,
    context: HttpMappingTemplateContext
): JsonElement? {
    return when (value) {
        null -> null
        is String -> renderTemplate(value, context)?.let(::JsonPrimitive)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> {
            buildJsonObject {
                value.forEach { (key, child) ->
                    val renderedChild = renderJsonTemplate(child, context) ?: return@forEach
                    put(key.toString(), renderedChild)
                }
            }
        }

        is List<*> -> {
            buildJsonArray {
                value.forEach { child ->
                    renderJsonTemplate(child, context)?.let(::add)
                }
            }
        }

        else -> JsonPrimitive(value.toString())
    }
}

private fun extractJsonString(payload: JsonObject, path: String): String? {
    return extractJsonElement(payload, path)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun extractJsonLong(payload: JsonObject, path: String): Long? {
    return extractJsonString(payload, path)?.toLongOrNull()
}

private fun extractJsonElement(payload: JsonObject, path: String): JsonElement? {
    val tokens = parseJsonPath(path) ?: return null
    var current: JsonElement = payload
    tokens.forEach { token ->
        current = when (token) {
            is JsonPathToken.ObjectKey -> (current as? JsonObject)?.get(token.key) ?: return null
            is JsonPathToken.ArrayIndex -> (current as? JsonArray)?.getOrNull(token.index) ?: return null
        }
    }
    return current
}

private sealed interface JsonPathToken {
    data class ObjectKey(val key: String) : JsonPathToken
    data class ArrayIndex(val index: Int) : JsonPathToken
}

private fun parseJsonPath(path: String): List<JsonPathToken>? {
    if (path.isBlank()) {
        return null
    }
    val tokens = mutableListOf<JsonPathToken>()
    var index = 0
    while (index < path.length) {
        if (path[index] == '.') {
            index += 1
            continue
        }
        if (path[index] == '[') {
            val end = path.indexOf(']', startIndex = index)
            if (end <= index + 1) {
                return null
            }
            val parsedIndex = path.substring(index + 1, end).toIntOrNull() ?: return null
            tokens += JsonPathToken.ArrayIndex(parsedIndex)
            index = end + 1
            continue
        }
        val nextDelimiter = buildList {
            path.indexOf('.', startIndex = index).takeIf { it >= 0 }?.let(::add)
            path.indexOf('[', startIndex = index).takeIf { it >= 0 }?.let(::add)
        }.minOrNull() ?: path.length
        val key = path.substring(index, nextDelimiter)
        if (key.isBlank()) {
            return null
        }
        tokens += JsonPathToken.ObjectKey(key)
        index = nextDelimiter
    }
    return tokens
}

private fun normalizeSourceRuntimeJson(json: JSONObject): JSONObject {
    return JSONObject().apply {
        json.keys().asSequence().toList().sorted().forEach { key ->
            put(key, normalizeSourceRuntimeValue(json.opt(key)))
        }
    }
}

private fun normalizeSourceRuntimeValue(value: Any?): Any? {
    return when (value) {
        is JSONObject -> normalizeSourceRuntimeJson(value)
        is JSONArray -> JSONArray().apply {
            for (index in 0 until value.length()) {
                put(normalizeSourceRuntimeValue(value.opt(index)))
            }
        }

        is String -> value.trim()
        null -> JSONObject.NULL
        else -> value
    }
}

private fun JSONObject.toStringMap(): Map<String, String> {
    return buildMap {
        keys().forEach { key ->
            val value = optString(key).trim()
            if (value.isNotBlank()) {
                put(key, value)
            }
        }
    }
}

private fun jsonValueToKotlin(value: Any?): Any? {
    return when (value) {
        JSONObject.NULL -> null
        is JSONObject -> {
            buildMap {
                value.keys().forEach { key ->
                    put(key, jsonValueToKotlin(value.opt(key)))
                }
            }
        }

        is JSONArray -> buildList {
            for (index in 0 until value.length()) {
                add(jsonValueToKotlin(value.opt(index)))
            }
        }

        else -> value
    }
}

private fun validateTemplateVariables(template: String) {
    TEMPLATE_PLACEHOLDER_REGEX.findAll(template).forEach { match ->
        val placeholder = match.groupValues[1]
        require(
            placeholder == "songId" ||
                placeholder == "title" ||
                placeholder == "artistText" ||
                placeholder == "albumTitle" ||
                placeholder == "durationMs" ||
                placeholder == "quality" ||
                placeholder.startsWith("header.")
        ) {
            "Unsupported template variable: {$placeholder}"
        }
    }
}

private fun validateJsonTemplate(value: Any?) {
    when (value) {
        is String -> validateTemplateVariables(value)
        is Map<*, *> -> value.values.forEach(::validateJsonTemplate)
        is List<*> -> value.forEach(::validateJsonTemplate)
    }
}

private fun normalizeBaseUrl(raw: String): String {
    return raw.trim().trimEnd('/')
}

private fun isValidHttpUrl(raw: String): Boolean {
    if (raw.isBlank()) {
        return false
    }
    return try {
        val url = java.net.URI(raw.trim())
        val scheme = url.scheme?.lowercase().orEmpty()
        scheme in setOf("http", "https") && !url.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }
}

private val TEMPLATE_PLACEHOLDER_REGEX = Regex("\\{([^{}]+)\\}")
