package com.wxy.playerlite.feature.main

import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackPrewarmPreferences

internal enum class ManagedCacheKind(
    val displayName: String
) {
    PLAYBACK("在线播放缓存"),
    LYRICS("歌词缓存")
}

internal data class ManagedCacheEntry(
    val kind: ManagedCacheKind,
    val label: String,
    val bytes: Long
)

internal data class ManagedCacheSnapshot(
    val totalBytes: Long = 0L,
    val entries: List<ManagedCacheEntry> = defaultManagedCacheEntries()
)

internal interface SettingsCacheRepositoryContract {
    suspend fun readSnapshot(): ManagedCacheSnapshot

    suspend fun clearLyricsCache()
}

internal interface SettingsCacheControllerContract {
    suspend fun clearPlaybackCache(): Boolean
}

internal interface SettingsPlaybackPreferencesRepositoryContract {
    suspend fun readPreferredAudioQuality(): PlaybackAudioQuality

    suspend fun writePreferredAudioQuality(audioQuality: PlaybackAudioQuality)

    suspend fun readPlaybackBehaviorPreferences(): PlaybackBehaviorPreferences

    suspend fun writePlaybackBehaviorPreferences(preferences: PlaybackBehaviorPreferences)

    suspend fun readCachePolicyPreferences(): CachePolicyPreferences

    suspend fun writeCachePolicyPreferences(preferences: CachePolicyPreferences)

    suspend fun readPlaybackPrewarmPreferences(): PlaybackPrewarmPreferences

    suspend fun writePlaybackPrewarmPreferences(preferences: PlaybackPrewarmPreferences)

    suspend fun readPlaybackCacheLimitBytes(): Long

    suspend fun writePlaybackCacheLimitBytes(maxBytes: Long)

    suspend fun readActiveAudioSourceConfigJson(): String?

    suspend fun writeActiveAudioSourceConfigJson(configJson: String?)
}

internal interface SettingsPlaybackControllerContract {
    suspend fun setPreferredAudioQuality(audioQuality: PlaybackAudioQuality): Boolean

    suspend fun setWeakNetworkAutoRetryEnabled(enabled: Boolean): Boolean

    suspend fun setCachePolicyPreferences(preferences: CachePolicyPreferences): Boolean

    suspend fun setPlaybackPrewarmPreferences(preferences: PlaybackPrewarmPreferences): Boolean

    suspend fun setPlaybackCacheLimitBytes(maxBytes: Long): Boolean

    suspend fun setActiveAudioSourceConfigJson(configJson: String?): Boolean
}

internal data class PlaybackBehaviorPreferences(
    val restoreLastPlaybackOnStartup: Boolean = true,
    val resumeFromLastPosition: Boolean = true,
    val weakNetworkAutoRetry: Boolean = true
)

internal data class CachePolicyPreferences(
    val showCacheFailureNotifications: Boolean = true
)

internal enum class ManagedAudioSourceKind(
    val displayName: String
) {
    CUSTOM("自定义音源")
}

internal enum class ManagedAudioSourceResolverType(
    val wireValue: String,
    val displayName: String
) {
    NETEASE_COMPATIBLE("netease-compatible", "Netease 兼容"),
    HTTP_MAPPING("http-mapping", "HTTP 映射");

    companion object {
        fun fromWireValue(raw: String?): ManagedAudioSourceResolverType? {
            val normalized = raw?.trim().orEmpty()
            return entries.firstOrNull { it.wireValue == normalized }
        }
    }
}

internal data class ManagedAudioSource(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val kind: ManagedAudioSourceKind,
    val resolverType: ManagedAudioSourceResolverType =
        ManagedAudioSourceResolverType.NETEASE_COMPATIBLE,
    val sourceConfigJson: String? = null,
    val enabled: Boolean = true,
    val author: String? = null,
    val version: String? = null,
    val importUrl: String? = null,
    val isBuiltIn: Boolean = false,
    val isActive: Boolean = false,
    val initError: String? = null,
    val detailMessage: String? = null,
    val addedAtMs: Long
)

internal interface AudioSourceRepositoryContract {
    suspend fun readSources(): List<ManagedAudioSource>

    suspend fun addSource(
        displayName: String,
        baseUrl: String,
        kind: ManagedAudioSourceKind = ManagedAudioSourceKind.CUSTOM
    ): Result<ManagedAudioSource>

    suspend fun importSourceFromUrl(manifestUrl: String): Result<ManagedAudioSource> {
        return Result.failure(UnsupportedOperationException("Online source import unsupported"))
    }

    suspend fun importSourceFromLocalJson(
        rawJson: String,
        displayLabel: String? = null
    ): Result<ManagedAudioSource> {
        return Result.failure(UnsupportedOperationException("Local source import unsupported"))
    }

    suspend fun setActiveSource(sourceId: String): Result<List<ManagedAudioSource>> {
        return Result.failure(UnsupportedOperationException("Audio source activation unsupported"))
    }

    suspend fun removeSource(sourceId: String): Result<List<ManagedAudioSource>> {
        return Result.failure(UnsupportedOperationException("Audio source removal unsupported"))
    }
}

internal data class SettingsAccountUiState(
    val isLoggedIn: Boolean = false,
    val isBusy: Boolean = false,
    val title: String = "未登录",
    val summary: String = "本地播放不受影响，在线播放前需要登录",
    val avatarUrl: String? = null,
    val isLogoutConfirmVisible: Boolean = false
)

internal data class SettingsCacheUiState(
    val snapshot: ManagedCacheSnapshot? = ManagedCacheSnapshot(),
    val isRefreshing: Boolean = false,
    val isClearing: Boolean = false,
    val feedbackMessage: String? = null,
    val playbackCacheLimitBytes: Long = DEFAULT_PLAYBACK_CACHE_LIMIT_BYTES,
    val pendingPlaybackCacheLimitMb: String = (DEFAULT_PLAYBACK_CACHE_LIMIT_BYTES / BYTES_PER_MB).toString(),
    val isSavingPlaybackCacheLimit: Boolean = false,
    val playbackCacheLimitMessage: String? = null
)

internal data class SettingsPlaybackPreferencesUiState(
    val preferredAudioQuality: PlaybackAudioQuality = PlaybackAudioQuality.EXHIGH,
    val behaviorPreferences: PlaybackBehaviorPreferences = PlaybackBehaviorPreferences(),
    val cachePolicyPreferences: CachePolicyPreferences = CachePolicyPreferences(),
    val prewarmPreferences: PlaybackPrewarmPreferences = PlaybackPrewarmPreferences(),
    val isSavingPreferredAudioQuality: Boolean = false,
    val isPreferredAudioQualityDialogVisible: Boolean = false,
    val feedbackMessage: String? = null
)

internal enum class PlaybackPrewarmBudgetPreset(
    val displayName: String,
    val budgetDurationMs: Long,
    val budgetBytes: Long
) {
    LIGHT(
        displayName = "轻量",
        budgetDurationMs = 30_000L,
        budgetBytes = 4L * BYTES_PER_MB
    ),
    BALANCED(
        displayName = "均衡",
        budgetDurationMs = PlaybackPrewarmPreferences.DEFAULT_BUDGET_DURATION_MS,
        budgetBytes = PlaybackPrewarmPreferences.DEFAULT_BUDGET_BYTES
    ),
    EXTENDED(
        displayName = "扩展",
        budgetDurationMs = 120_000L,
        budgetBytes = 16L * BYTES_PER_MB
    );

    fun toPreferences(enabled: Boolean): PlaybackPrewarmPreferences {
        return PlaybackPrewarmPreferences(
            enabled = enabled,
            budgetDurationMs = budgetDurationMs,
            budgetBytes = budgetBytes
        ).sanitized()
    }

    companion object {
        fun fromPreferences(
            preferences: PlaybackPrewarmPreferences
        ): PlaybackPrewarmBudgetPreset {
            return entries.firstOrNull {
                it.budgetDurationMs == preferences.budgetDurationMs &&
                    it.budgetBytes == preferences.budgetBytes
            } ?: BALANCED
        }
    }
}

internal data class SettingsSourcesUiState(
    val items: List<ManagedAudioSource> = emptyList(),
    val pendingName: String = "",
    val pendingBaseUrl: String = "",
    val pendingKind: ManagedAudioSourceKind = ManagedAudioSourceKind.CUSTOM,
    val isSubmitting: Boolean = false,
    val validationMessage: String? = null,
    val pendingImportUrl: String = "",
    val isImporting: Boolean = false,
    val importFeedbackMessage: String? = null
)

internal data class SettingsUiState(
    val accountState: SettingsAccountUiState = SettingsAccountUiState(),
    val cacheState: SettingsCacheUiState = SettingsCacheUiState(),
    val playbackPreferencesState: SettingsPlaybackPreferencesUiState =
        SettingsPlaybackPreferencesUiState(),
    val sourcesState: SettingsSourcesUiState = SettingsSourcesUiState()
)

internal const val DEFAULT_PLAYBACK_CACHE_LIMIT_BYTES = 500L * 1024L * 1024L
internal const val BYTES_PER_MB = 1024L * 1024L

private fun defaultManagedCacheEntries(): List<ManagedCacheEntry> {
    return ManagedCacheKind.values().map { kind ->
        ManagedCacheEntry(
            kind = kind,
            label = kind.displayName,
            bytes = 0L
        )
    }
}
