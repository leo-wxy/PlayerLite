package com.wxy.playerlite.feature.main

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.wxy.playerlite.core.AppEnvironmentConfig
import com.wxy.playerlite.core.playback.AppPlaybackGraph
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackPrewarmPreferences
import com.wxy.playerlite.playback.orchestrator.PlayerServiceController
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class SettingsCacheRepository(
    private val playbackCacheDirProvider: () -> File,
    private val lyricsCacheDirProvider: () -> File
) : SettingsCacheRepositoryContract {
    constructor(context: Context) : this(
        playbackCacheDirProvider = {
            File(context.applicationContext.cacheDir, PLAYBACK_CACHE_DIR_NAME)
        },
        lyricsCacheDirProvider = {
            context.applicationContext.filesDir.resolve(LYRICS_CACHE_DIR_NAME)
        }
    )

    override suspend fun readSnapshot(): ManagedCacheSnapshot {
        val entries = listOf(
            ManagedCacheEntry(
                kind = ManagedCacheKind.PLAYBACK,
                label = ManagedCacheKind.PLAYBACK.displayName,
                bytes = playbackCacheDirProvider().directorySizeBytes()
            ),
            ManagedCacheEntry(
                kind = ManagedCacheKind.LYRICS,
                label = ManagedCacheKind.LYRICS.displayName,
                bytes = lyricsCacheDirProvider().directorySizeBytes()
            )
        )
        return ManagedCacheSnapshot(
            totalBytes = entries.sumOf { it.bytes },
            entries = entries
        )
    }

    override suspend fun clearLyricsCache() {
        lyricsCacheDirProvider().deleteChildrenRecursively()
    }

    private companion object {
        const val PLAYBACK_CACHE_DIR_NAME = "cache_core"
        const val LYRICS_CACHE_DIR_NAME = "lyrics"
    }
}

internal class SettingsCacheController(
    private val serviceController: PlayerServiceController
) : SettingsCacheControllerContract {
    constructor(
        context: Context,
        onControllerError: (String) -> Unit
    ) : this(
        serviceController = AppPlaybackGraph.playerServiceController(
            context = context.applicationContext,
            onControllerError = onControllerError
        )
    )

    override suspend fun clearPlaybackCache(): Boolean {
        serviceController.ensurePlaybackServiceStartedForPlayback()
        serviceController.connectIfNeeded()
        return serviceController.clearCache()
    }
}

internal class SettingsPlaybackPreferencesRepository(
    private val preferences: SharedPreferences
) : SettingsPlaybackPreferencesRepositoryContract {
    constructor(context: Context) : this(
        preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    )

    override suspend fun readPreferredAudioQuality(): PlaybackAudioQuality {
        return sanitizePreferredAudioQuality(
            PlaybackAudioQuality.fromWireValue(preferences.getString(KEY_PREFERRED_AUDIO_QUALITY, null))
        )
    }

    override suspend fun writePreferredAudioQuality(audioQuality: PlaybackAudioQuality) {
        preferences.edit()
            .putString(
                KEY_PREFERRED_AUDIO_QUALITY,
                sanitizePreferredAudioQuality(audioQuality).wireValue
            )
            .apply()
    }

    override suspend fun readPlaybackBehaviorPreferences(): PlaybackBehaviorPreferences {
        return PlaybackBehaviorPreferences(
            restoreLastPlaybackOnStartup = preferences.getBoolean(
                KEY_RESTORE_LAST_PLAYBACK_ON_STARTUP,
                true
            ),
            resumeFromLastPosition = preferences.getBoolean(
                KEY_RESUME_FROM_LAST_POSITION,
                true
            ),
            weakNetworkAutoRetry = preferences.getBoolean(
                KEY_WEAK_NETWORK_AUTO_RETRY,
                true
            )
        )
    }

    override suspend fun writePlaybackBehaviorPreferences(
        preferences: PlaybackBehaviorPreferences
    ) {
        this.preferences.edit()
            .putBoolean(
                KEY_RESTORE_LAST_PLAYBACK_ON_STARTUP,
                preferences.restoreLastPlaybackOnStartup
            )
            .putBoolean(KEY_RESUME_FROM_LAST_POSITION, preferences.resumeFromLastPosition)
            .putBoolean(KEY_WEAK_NETWORK_AUTO_RETRY, preferences.weakNetworkAutoRetry)
            .apply()
    }

    override suspend fun readCachePolicyPreferences(): CachePolicyPreferences {
        return CachePolicyPreferences(
            showCacheFailureNotifications = preferences.getBoolean(
                KEY_SHOW_CACHE_FAILURE_NOTIFICATIONS,
                true
            )
        )
    }

    override suspend fun writeCachePolicyPreferences(preferences: CachePolicyPreferences) {
        this.preferences.edit()
            .putBoolean(
                KEY_SHOW_CACHE_FAILURE_NOTIFICATIONS,
                preferences.showCacheFailureNotifications
            )
            .apply()
    }

    override suspend fun readPlaybackPrewarmPreferences(): PlaybackPrewarmPreferences {
        return PlaybackPrewarmPreferences(
            enabled = preferences.getBoolean(
                KEY_PLAYBACK_PREWARM_ENABLED,
                true
            ),
            budgetDurationMs = preferences.getLong(
                KEY_PLAYBACK_PREWARM_BUDGET_DURATION_MS,
                PlaybackPrewarmPreferences.DEFAULT_BUDGET_DURATION_MS
            ),
            budgetBytes = preferences.getLong(
                KEY_PLAYBACK_PREWARM_BUDGET_BYTES,
                PlaybackPrewarmPreferences.DEFAULT_BUDGET_BYTES
            ),
            readyThresholdDurationMs = preferences.getLong(
                KEY_PLAYBACK_PREWARM_READY_THRESHOLD_DURATION_MS,
                PlaybackPrewarmPreferences.DEFAULT_READY_THRESHOLD_DURATION_MS
            ),
            readyThresholdBytes = preferences.getLong(
                KEY_PLAYBACK_PREWARM_READY_THRESHOLD_BYTES,
                PlaybackPrewarmPreferences.DEFAULT_READY_THRESHOLD_BYTES
            )
        ).sanitized()
    }

    override suspend fun writePlaybackPrewarmPreferences(
        preferences: PlaybackPrewarmPreferences
    ) {
        val sanitized = preferences.sanitized()
        this.preferences.edit()
            .putBoolean(KEY_PLAYBACK_PREWARM_ENABLED, sanitized.enabled)
            .putLong(KEY_PLAYBACK_PREWARM_BUDGET_DURATION_MS, sanitized.budgetDurationMs)
            .putLong(KEY_PLAYBACK_PREWARM_BUDGET_BYTES, sanitized.budgetBytes)
            .putLong(
                KEY_PLAYBACK_PREWARM_READY_THRESHOLD_DURATION_MS,
                sanitized.readyThresholdDurationMs
            )
            .putLong(KEY_PLAYBACK_PREWARM_READY_THRESHOLD_BYTES, sanitized.readyThresholdBytes)
            .apply()
    }

    override suspend fun readPlaybackCacheLimitBytes(): Long {
        return preferences.getLong(
            KEY_PLAYBACK_CACHE_LIMIT_BYTES,
            DEFAULT_PLAYBACK_CACHE_LIMIT_BYTES
        ).coerceAtLeast(MIN_PLAYBACK_CACHE_LIMIT_BYTES)
    }

    override suspend fun writePlaybackCacheLimitBytes(maxBytes: Long) {
        preferences.edit()
            .putLong(
                KEY_PLAYBACK_CACHE_LIMIT_BYTES,
                maxBytes.coerceAtLeast(MIN_PLAYBACK_CACHE_LIMIT_BYTES)
            )
            .apply()
    }

    override suspend fun readActiveAudioSourceConfigJson(): String? {
        preferences.getString(KEY_ACTIVE_AUDIO_SOURCE_CONFIG_JSON, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { persisted ->
                return normalizeStoredSourceConfigJson(persisted)
            }
        val legacyBaseUrl = preferences.getString(KEY_PREFERRED_AUDIO_SOURCE_BASE_URL, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeSourceBaseUrl)
            ?: return null
        val migratedConfigJson = buildNeteaseCompatibleConfigJson(legacyBaseUrl)
        preferences.edit()
            .putString(KEY_ACTIVE_AUDIO_SOURCE_CONFIG_JSON, migratedConfigJson)
            .apply()
        return migratedConfigJson
    }

    override suspend fun writeActiveAudioSourceConfigJson(configJson: String?) {
        preferences.edit()
            .putString(
                KEY_ACTIVE_AUDIO_SOURCE_CONFIG_JSON,
                configJson
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::normalizeStoredSourceConfigJson)
            )
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "player_playback_preferences"
        const val KEY_PREFERRED_AUDIO_QUALITY = "preferred_audio_quality"
        const val KEY_PLAYBACK_CACHE_LIMIT_BYTES = "playback_cache_limit_bytes"
        const val KEY_RESTORE_LAST_PLAYBACK_ON_STARTUP = "restore_last_playback_on_startup"
        const val KEY_RESUME_FROM_LAST_POSITION = "resume_from_last_position"
        const val KEY_WEAK_NETWORK_AUTO_RETRY = "weak_network_auto_retry"
        const val KEY_SHOW_CACHE_FAILURE_NOTIFICATIONS = "show_cache_failure_notifications"
        const val KEY_PLAYBACK_PREWARM_ENABLED = "playback_prewarm_enabled"
        const val KEY_PLAYBACK_PREWARM_BUDGET_DURATION_MS =
            "playback_prewarm_budget_duration_ms"
        const val KEY_PLAYBACK_PREWARM_BUDGET_BYTES = "playback_prewarm_budget_bytes"
        const val KEY_PLAYBACK_PREWARM_READY_THRESHOLD_DURATION_MS =
            "playback_prewarm_ready_threshold_duration_ms"
        const val KEY_PLAYBACK_PREWARM_READY_THRESHOLD_BYTES =
            "playback_prewarm_ready_threshold_bytes"
        const val KEY_ACTIVE_AUDIO_SOURCE_CONFIG_JSON = "active_audio_source_config_json"
        const val KEY_PREFERRED_AUDIO_SOURCE_BASE_URL = "preferred_audio_source_base_url"
        const val MIN_PLAYBACK_CACHE_LIMIT_BYTES = 64L * BYTES_PER_MB

        fun sanitizePreferredAudioQuality(audioQuality: PlaybackAudioQuality?): PlaybackAudioQuality {
            return when (audioQuality) {
                PlaybackAudioQuality.VIVID,
                null -> PlaybackAudioQuality.EXHIGH
                else -> audioQuality
            }
        }
    }
}

internal class SettingsPlaybackController(
    private val serviceController: PlayerServiceController
) : SettingsPlaybackControllerContract {
    constructor(
        context: Context,
        onControllerError: (String) -> Unit
    ) : this(
        serviceController = AppPlaybackGraph.playerServiceController(
            context = context.applicationContext,
            onControllerError = onControllerError
        )
    )

    override suspend fun setPreferredAudioQuality(audioQuality: PlaybackAudioQuality): Boolean {
        serviceController.ensurePlaybackServiceStartedForPlayback()
        serviceController.connectIfNeeded()
        return serviceController.setPreferredAudioQuality(audioQuality)
    }

    override suspend fun setWeakNetworkAutoRetryEnabled(enabled: Boolean): Boolean {
        serviceController.ensurePlaybackServiceStartedForPlayback()
        serviceController.connectIfNeeded()
        return serviceController.setWeakNetworkAutoRetryEnabled(enabled)
    }

    override suspend fun setCachePolicyPreferences(
        preferences: CachePolicyPreferences
    ): Boolean {
        serviceController.ensurePlaybackServiceStartedForPlayback()
        serviceController.connectIfNeeded()
        return serviceController.setCachePolicyPreferences(
            showCacheFailureNotifications = preferences.showCacheFailureNotifications
        )
    }

    override suspend fun setPlaybackPrewarmPreferences(
        preferences: PlaybackPrewarmPreferences
    ): Boolean {
        serviceController.ensurePlaybackServiceStartedForPlayback()
        serviceController.connectIfNeeded()
        return serviceController.setPlaybackPrewarmPreferences(preferences.sanitized())
    }

    override suspend fun setPlaybackCacheLimitBytes(maxBytes: Long): Boolean {
        serviceController.ensurePlaybackServiceStartedForPlayback()
        serviceController.connectIfNeeded()
        return serviceController.setPlaybackCacheLimitBytes(maxBytes)
    }

    override suspend fun setActiveAudioSourceConfigJson(configJson: String?): Boolean {
        serviceController.ensurePlaybackServiceStartedForPlayback()
        serviceController.connectIfNeeded()
        return serviceController.setActiveAudioSourceConfigJson(configJson)
    }
}

internal class AudioSourceRepository(
    private val preferences: SharedPreferences,
    private val builtInBaseUrl: String = AppEnvironmentConfig.apiBaseUrl,
    private val manifestFetcher: suspend (String) -> String = ::fetchAudioSourceManifestText,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() }
) : AudioSourceRepositoryContract {
    constructor(context: Context) : this(
        preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    )

    override suspend fun readSources(): List<ManagedAudioSource> {
        val persistedSources = readPersistedSources()
        val persistedActiveSourceId = preferences.getString(KEY_ACTIVE_SOURCE_ID, BUILT_IN_SOURCE_ID)
            ?.takeIf { it.isNotBlank() }
            ?: BUILT_IN_SOURCE_ID
        val activeSourceId = when {
            persistedActiveSourceId == BUILT_IN_SOURCE_ID -> BUILT_IN_SOURCE_ID
            persistedSources.any { it.id == persistedActiveSourceId } -> persistedActiveSourceId
            else -> BUILT_IN_SOURCE_ID
        }
        return listOf(
            buildBuiltInSource(isActive = activeSourceId == BUILT_IN_SOURCE_ID)
        ) + persistedSources.map { item ->
            item.copy(isActive = item.id == activeSourceId)
        }
    }

    override suspend fun addSource(
        displayName: String,
        baseUrl: String,
        kind: ManagedAudioSourceKind
    ): Result<ManagedAudioSource> {
        val normalizedBaseUrl = normalizeSourceBaseUrl(baseUrl)
        return createAndPersistSource(
            displayName = displayName,
            parsedConfig = ParsedSourceConfig(
                resolverType = ManagedAudioSourceResolverType.NETEASE_COMPATIBLE,
                sourceConfigJson = buildNeteaseCompatibleConfigJson(normalizedBaseUrl),
                displayBaseUrl = normalizedBaseUrl
            ),
            kind = kind,
            author = null,
            version = null,
            importUrl = null
        )
    }

    override suspend fun importSourceFromUrl(manifestUrl: String): Result<ManagedAudioSource> {
        val normalizedManifestUrl = normalizeManifestUrl(manifestUrl)
        if (!isValidBaseUrl(normalizedManifestUrl)) {
            return Result.failure(IllegalArgumentException("导入地址必须是有效的 http/https 地址"))
        }
        val rawManifest = runCatching {
            manifestFetcher(normalizedManifestUrl)
        }.getOrElse { error ->
            return Result.failure(
                IllegalStateException(error.message ?: "在线音源导入失败")
            )
        }
        return importSourceFromLocalJson(
            rawJson = rawManifest,
            displayLabel = normalizedManifestUrl
        )
    }

    override suspend fun importSourceFromLocalJson(
        rawJson: String,
        displayLabel: String?
    ): Result<ManagedAudioSource> {
        val manifest = parseManifest(
            rawJson = rawJson,
            displayLabel = displayLabel
        ).getOrElse { error ->
            return Result.failure(error)
        }
        return createAndPersistSource(
            displayName = manifest.displayName,
            parsedConfig = manifest.parsedConfig,
            kind = ManagedAudioSourceKind.CUSTOM,
            author = manifest.author,
            version = manifest.version,
            importUrl = manifest.importUrl
        )
    }

    override suspend fun setActiveSource(sourceId: String): Result<List<ManagedAudioSource>> {
        val sources = readSources()
        if (sources.none { it.id == sourceId }) {
            return Result.failure(IllegalArgumentException("音源不存在"))
        }
        preferences.edit()
            .putString(KEY_ACTIVE_SOURCE_ID, sourceId)
            .apply()
        return Result.success(readSources())
    }

    override suspend fun removeSource(sourceId: String): Result<List<ManagedAudioSource>> {
        if (sourceId == BUILT_IN_SOURCE_ID) {
            return Result.failure(IllegalArgumentException("默认音源不能删除"))
        }
        val currentItems = readPersistedSources()
        if (currentItems.none { it.id == sourceId }) {
            return Result.failure(IllegalArgumentException("音源不存在"))
        }
        writeSources(currentItems.filterNot { it.id == sourceId })
        val activeSourceId = preferences.getString(KEY_ACTIVE_SOURCE_ID, BUILT_IN_SOURCE_ID)
        if (activeSourceId == sourceId) {
            preferences.edit()
                .putString(KEY_ACTIVE_SOURCE_ID, BUILT_IN_SOURCE_ID)
                .apply()
        }
        return Result.success(readSources())
    }

    private suspend fun readPersistedSources(): List<ManagedAudioSource> {
        val raw = preferences.getString(KEY_AUDIO_SOURCES, null).orEmpty().trim()
        if (raw.isEmpty()) {
            return emptyList()
        }
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString(KEY_ID).trim()
                    val displayName = item.optString(KEY_DISPLAY_NAME).trim()
                    val parsedConfig = parsePersistedSourceConfig(item) ?: continue
                    if (id.isEmpty() || displayName.isEmpty()) {
                        continue
                    }
                    add(
                        ManagedAudioSource(
                            id = id,
                            displayName = displayName,
                            baseUrl = parsedConfig.displayBaseUrl,
                            kind = parseKind(item.optString(KEY_KIND)),
                            resolverType = parsedConfig.resolverType,
                            sourceConfigJson = parsedConfig.sourceConfigJson,
                            enabled = item.optBoolean(KEY_ENABLED, true),
                            author = item.optString(KEY_AUTHOR).trim().takeIf { it.isNotEmpty() },
                            version = item.optString(KEY_VERSION).trim().takeIf { it.isNotEmpty() },
                            importUrl = item.optString(KEY_IMPORT_URL).trim().takeIf { it.isNotEmpty() },
                            isBuiltIn = false,
                            isActive = false,
                            initError = item.optString(KEY_INIT_ERROR).trim().takeIf { it.isNotEmpty() },
                            detailMessage = item.optString(KEY_DETAIL_MESSAGE).trim()
                                .takeIf { it.isNotEmpty() },
                            addedAtMs = item.optLong(KEY_ADDED_AT_MS).coerceAtLeast(0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun buildBuiltInSource(
        isActive: Boolean
    ): ManagedAudioSource {
        return ManagedAudioSource(
            id = BUILT_IN_SOURCE_ID,
            displayName = "默认网易源",
            baseUrl = normalizeSourceBaseUrl(builtInBaseUrl),
            kind = ManagedAudioSourceKind.CUSTOM,
            resolverType = ManagedAudioSourceResolverType.NETEASE_COMPATIBLE,
            sourceConfigJson = buildNeteaseCompatibleConfigJson(builtInBaseUrl),
            enabled = true,
            author = "PlayerLite",
            version = "builtin",
            importUrl = null,
            isBuiltIn = true,
            isActive = isActive,
            initError = null,
            detailMessage = "内置默认音源",
            addedAtMs = 0L
        )
    }

    private suspend fun createAndPersistSource(
        displayName: String,
        parsedConfig: ParsedSourceConfig,
        kind: ManagedAudioSourceKind,
        author: String?,
        version: String?,
        importUrl: String?
    ): Result<ManagedAudioSource> {
        val normalizedName = displayName.trim()
        if (normalizedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("音源名称不能为空"))
        }
        if (!isValidBaseUrl(parsedConfig.displayBaseUrl)) {
            return Result.failure(IllegalArgumentException("音源地址必须是有效的 http/https 地址"))
        }
        val currentItems = readPersistedSources()
        val builtInNormalizedBaseUrl = normalizeSourceBaseUrl(builtInBaseUrl)
        if (
            (parsedConfig.resolverType == ManagedAudioSourceResolverType.NETEASE_COMPATIBLE &&
                parsedConfig.displayBaseUrl == builtInNormalizedBaseUrl) ||
            currentItems.any {
                it.sourceConfigJson == parsedConfig.sourceConfigJson ||
                    it.baseUrl == parsedConfig.displayBaseUrl
            }
        ) {
            return Result.failure(IllegalArgumentException("音源地址已存在"))
        }
        val source = ManagedAudioSource(
            id = idProvider(),
            displayName = normalizedName,
            baseUrl = parsedConfig.displayBaseUrl,
            kind = kind,
            resolverType = parsedConfig.resolverType,
            sourceConfigJson = parsedConfig.sourceConfigJson,
            enabled = true,
            author = author?.trim()?.takeIf { it.isNotEmpty() },
            version = version?.trim()?.takeIf { it.isNotEmpty() },
            importUrl = importUrl?.trim()?.takeIf { it.isNotEmpty() },
            isBuiltIn = false,
            isActive = false,
            initError = null,
            detailMessage = null,
            addedAtMs = timeProvider()
        )
        writeSources(currentItems + source)
        return Result.success(source)
    }

    private fun writeSources(items: List<ManagedAudioSource>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put(KEY_ID, item.id)
                    put(KEY_DISPLAY_NAME, item.displayName)
                    put(KEY_BASE_URL, item.baseUrl)
                    put(KEY_KIND, item.kind.name)
                    put(KEY_RESOLVER_TYPE, item.resolverType.wireValue)
                    put(KEY_SOURCE_CONFIG_JSON, item.sourceConfigJson)
                    put(KEY_ENABLED, item.enabled)
                    put(KEY_AUTHOR, item.author)
                    put(KEY_VERSION, item.version)
                    put(KEY_IMPORT_URL, item.importUrl)
                    put(KEY_INIT_ERROR, item.initError)
                    put(KEY_DETAIL_MESSAGE, item.detailMessage)
                    put(KEY_ADDED_AT_MS, item.addedAtMs)
                }
            )
        }
        preferences.edit().putString(KEY_AUDIO_SOURCES, array.toString()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "settings_audio_sources"
        const val KEY_AUDIO_SOURCES = "audio_sources"
        const val KEY_ID = "id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_BASE_URL = "base_url"
        const val KEY_KIND = "kind"
        const val KEY_RESOLVER_TYPE = "resolver_type"
        const val KEY_SOURCE_CONFIG_JSON = "source_config_json"
        const val KEY_ENABLED = "enabled"
        const val KEY_AUTHOR = "author"
        const val KEY_VERSION = "version"
        const val KEY_IMPORT_URL = "import_url"
        const val KEY_INIT_ERROR = "init_error"
        const val KEY_DETAIL_MESSAGE = "detail_message"
        const val KEY_ADDED_AT_MS = "added_at_ms"
        const val KEY_ACTIVE_SOURCE_ID = "active_source_id"
        const val BUILT_IN_SOURCE_ID = "builtin-default-source"

        fun isValidBaseUrl(raw: String): Boolean {
            return isValidSourceBaseUrl(raw)
        }

        fun parseKind(raw: String): ManagedAudioSourceKind {
            return ManagedAudioSourceKind.values().firstOrNull { it.name == raw }
                ?: ManagedAudioSourceKind.CUSTOM
        }
    }
}

private data class ImportedAudioSourceManifest(
    val displayName: String,
    val author: String?,
    val version: String?,
    val parsedConfig: ParsedSourceConfig,
    val importUrl: String?
)

private data class ParsedSourceConfig(
    val resolverType: ManagedAudioSourceResolverType,
    val sourceConfigJson: String,
    val displayBaseUrl: String
)

private fun parseManifest(
    rawJson: String,
    displayLabel: String?
): Result<ImportedAudioSourceManifest> {
    return runCatching {
        val json = JSONObject(rawJson)
        val runtimeType = json.optJSONObject("runtime")
            ?.optString("type")
            ?.trim()
            ?.ifEmpty { "native" }
            ?: "native"
        if (runtimeType != "native") {
            error("暂只支持 native runtime 音源")
        }
        val displayName = json.optString("name").trim()
            .ifEmpty { json.optString("displayName").trim() }
            .ifEmpty { error("音源清单缺少 name") }
        val parsedConfig = json.optJSONObject("resolver")
            ?.let { resolverJson ->
                parseSourceConfigJson(resolverJson.toString()).getOrElse { error ->
                    throw error
                }
            }
            ?: parseLegacyManifestConfig(json).getOrElse { error ->
                throw error
            }
        ImportedAudioSourceManifest(
            displayName = displayName,
            author = json.optString("author").trim().takeIf { it.isNotEmpty() },
            version = json.optString("version").trim().takeIf { it.isNotEmpty() },
            parsedConfig = parsedConfig,
            importUrl = displayLabel?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}

private fun parsePersistedSourceConfig(item: JSONObject): ParsedSourceConfig? {
    item.optString("source_config_json")
        .trim()
        .takeIf { it.isNotEmpty() }
        ?.let { rawConfig ->
            return parseSourceConfigJson(rawConfig).getOrNull()
        }
    val legacyBaseUrl = item.optString("base_url")
        .trim()
        .takeIf { it.isNotEmpty() }
        ?.let(::normalizeSourceBaseUrl)
        ?: return null
    if (!isValidSourceBaseUrl(legacyBaseUrl)) {
        return null
    }
    return ParsedSourceConfig(
        resolverType = ManagedAudioSourceResolverType.NETEASE_COMPATIBLE,
        sourceConfigJson = buildNeteaseCompatibleConfigJson(legacyBaseUrl),
        displayBaseUrl = legacyBaseUrl
    )
}

private fun normalizeStoredSourceConfigJson(rawJson: String): String? {
    return parseSourceConfigJson(rawJson).getOrNull()?.sourceConfigJson
}

private fun parseSourceConfigJson(rawJson: String): Result<ParsedSourceConfig> {
    return runCatching {
        val resolverJson = JSONObject(rawJson)
        val resolverType = resolverJson.optString("type").trim()
            .ifEmpty { error("音源配置缺少 type") }
        when (ManagedAudioSourceResolverType.fromWireValue(resolverType)) {
            ManagedAudioSourceResolverType.NETEASE_COMPATIBLE -> {
                val baseUrl = normalizeSourceBaseUrl(
                    resolverJson.optString("baseUrl").trim()
                        .ifEmpty { resolverJson.optString("url").trim() }
                        .ifEmpty { error("音源配置缺少 baseUrl") }
                )
                if (!isValidSourceBaseUrl(baseUrl)) {
                    error("音源配置中的 baseUrl 不是有效的 http/https 地址")
                }
                ParsedSourceConfig(
                    resolverType = ManagedAudioSourceResolverType.NETEASE_COMPATIBLE,
                    sourceConfigJson = buildNeteaseCompatibleConfigJson(baseUrl),
                    displayBaseUrl = baseUrl
                )
            }

            ManagedAudioSourceResolverType.HTTP_MAPPING -> {
                val requestJson = resolverJson.optJSONObject("request")
                    ?: error("http-mapping 音源配置缺少 request")
                val method = requestJson.optString("method").trim().uppercase()
                    .ifEmpty { error("http-mapping 音源配置缺少 request.method") }
                if (method !in setOf("GET", "POST")) {
                    error("http-mapping 仅支持 GET/POST")
                }
                if (method == "GET" && requestJson.has("jsonBody")) {
                    error("GET 请求不支持 jsonBody")
                }
                val requestUrl = requestJson.optString("url").trim()
                    .ifEmpty { error("http-mapping 音源配置缺少 request.url") }
                if (!isValidHttpUrl(requestUrl)) {
                    error("http-mapping request.url 必须是有效的 http/https 地址")
                }
                val responseJson = resolverJson.optJSONObject("response")
                    ?: error("http-mapping 音源配置缺少 response")
                val playbackUrlPath = responseJson.optString("playbackUrl").trim()
                if (playbackUrlPath.isEmpty()) {
                    error("http-mapping response.playbackUrl 不能为空")
                }
                ParsedSourceConfig(
                    resolverType = ManagedAudioSourceResolverType.HTTP_MAPPING,
                    sourceConfigJson = normalizeHttpMappingConfigJson(
                        resolverJson = resolverJson,
                        requestMethod = method,
                        requestUrl = requestUrl
                    ),
                    displayBaseUrl = requestUrl
                )
            }

            null -> error("暂不支持该音源类型")
        }
    }
}

private fun parseLegacyManifestConfig(json: JSONObject): Result<ParsedSourceConfig> {
    return runCatching {
        val sourceType = json.optString("type").trim()
        if (sourceType.isNotEmpty() && sourceType != ManagedAudioSourceResolverType.NETEASE_COMPATIBLE.wireValue) {
            error("暂只支持 netease-compatible 旧版音源")
        }
        val baseUrl = normalizeSourceBaseUrl(
            json.optString("baseUrl").trim()
                .ifEmpty { json.optString("url").trim() }
                .ifEmpty { error("音源清单缺少 baseUrl") }
        )
        if (!isValidSourceBaseUrl(baseUrl)) {
            error("音源清单中的 baseUrl 不是有效的 http/https 地址")
        }
        ParsedSourceConfig(
            resolverType = ManagedAudioSourceResolverType.NETEASE_COMPATIBLE,
            sourceConfigJson = buildNeteaseCompatibleConfigJson(baseUrl),
            displayBaseUrl = baseUrl
        )
    }
}

private fun buildNeteaseCompatibleConfigJson(baseUrl: String): String {
    return JSONObject().apply {
        put("type", ManagedAudioSourceResolverType.NETEASE_COMPATIBLE.wireValue)
        put("baseUrl", normalizeSourceBaseUrl(baseUrl))
    }.toString()
}

private fun normalizeHttpMappingConfigJson(
    resolverJson: JSONObject,
    requestMethod: String,
    requestUrl: String
): String {
    val normalizedRequest = JSONObject().apply {
        put("method", requestMethod)
        put("url", requestUrl.trim())
        resolverJson.optJSONObject("request")
            ?.optJSONObject("query")
            ?.takeIf { it.length() > 0 }
            ?.let { put("query", normalizeJsonObject(it)) }
        resolverJson.optJSONObject("request")
            ?.optJSONObject("headers")
            ?.takeIf { it.length() > 0 }
            ?.let { put("headers", normalizeJsonObject(it)) }
        resolverJson.optJSONObject("request")
            ?.opt("jsonBody")
            ?.let { jsonBody ->
                if (requestMethod == "POST") {
                    put("jsonBody", normalizeJsonValue(jsonBody))
                }
            }
    }
    return JSONObject().apply {
        put("type", ManagedAudioSourceResolverType.HTTP_MAPPING.wireValue)
        put("request", normalizedRequest)
        resolverJson.optJSONObject("stream")
            ?.takeIf { it.length() > 0 }
            ?.let { put("stream", normalizeJsonObject(it)) }
        put(
            "response",
            normalizeJsonObject(
                resolverJson.optJSONObject("response") ?: JSONObject()
            )
        )
        resolverJson.optJSONObject("qualityMap")
            ?.takeIf { it.length() > 0 }
            ?.let { put("qualityMap", normalizeJsonObject(it)) }
    }.toString()
}

private fun normalizeJsonObject(jsonObject: JSONObject): JSONObject {
    return JSONObject().apply {
        jsonObject.keys().asSequence().toList().sorted().forEach { key ->
            put(key, normalizeJsonValue(jsonObject.opt(key)))
        }
    }
}

private fun normalizeJsonArray(jsonArray: JSONArray): JSONArray {
    return JSONArray().apply {
        for (index in 0 until jsonArray.length()) {
            put(normalizeJsonValue(jsonArray.opt(index)))
        }
    }
}

private fun normalizeJsonValue(value: Any?): Any? {
    return when (value) {
        is JSONObject -> normalizeJsonObject(value)
        is JSONArray -> normalizeJsonArray(value)
        is String -> value.trim()
        null -> JSONObject.NULL
        else -> value
    }
}

private suspend fun fetchAudioSourceManifestText(manifestUrl: String): String {
    return withContext(Dispatchers.IO) {
        val connection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }
        try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                error("音源清单请求失败($statusCode)")
            }
            connection.inputStream.bufferedReader().use { reader ->
                reader.readText().trim()
            }.ifEmpty {
                error("音源清单内容为空")
            }
        } finally {
            connection.disconnect()
        }
    }
}

private fun normalizeManifestUrl(raw: String): String {
    return raw.trim()
}

private fun normalizeSourceBaseUrl(raw: String): String {
    return raw.trim().trimEnd('/')
}

private fun isValidSourceBaseUrl(raw: String): Boolean {
    if (raw.isBlank()) {
        return false
    }
    val uri = Uri.parse(raw)
    val scheme = uri.scheme?.lowercase().orEmpty()
    return scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}

private fun isValidHttpUrl(raw: String): Boolean {
    if (raw.isBlank()) {
        return false
    }
    val uri = Uri.parse(raw.trim())
    val scheme = uri.scheme?.lowercase().orEmpty()
    return scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}

private fun File.directorySizeBytes(): Long {
    if (!exists()) {
        return 0L
    }
    if (isFile) {
        return length()
    }
    return walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() }
}

private fun File.deleteChildrenRecursively() {
    if (!exists()) {
        return
    }
    listFiles()?.forEach { child ->
        child.deleteRecursively()
    }
}
