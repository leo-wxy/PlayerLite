package com.wxy.playerlite.feature.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.feature.user.model.toUserSessionUiState
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.user.UserRepository
import com.wxy.playerlite.user.model.LoginState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SettingsViewModel(
    application: Application,
    private val userRepository: UserRepository,
    private val cacheRepository: SettingsCacheRepositoryContract,
    private val cacheController: SettingsCacheControllerContract,
    private val audioSourceRepository: AudioSourceRepositoryContract,
    private val playbackPreferencesRepository: SettingsPlaybackPreferencesRepositoryContract =
        SettingsPlaybackPreferencesRepository(application.applicationContext),
    private val playbackController: SettingsPlaybackControllerContract =
        SettingsPlaybackController(
            context = application.applicationContext,
            onControllerError = { errorMessage ->
                Log.w(TAG, errorMessage)
            }
        )
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        userRepository = AppContainer.userRepository(application.applicationContext),
        cacheRepository = SettingsCacheRepository(application.applicationContext),
        cacheController = SettingsCacheController(
            context = application.applicationContext,
            onControllerError = { errorMessage ->
                Log.w(TAG, errorMessage)
            }
        ),
        audioSourceRepository = AudioSourceRepository(application.applicationContext)
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiStateFlow: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var latestLoginState: LoginState = userRepository.loginStateFlow.value
    private var accountBusy = false

    init {
        publishAccountState()
        viewModelScope.launch {
            userRepository.loginStateFlow.collect { loginState ->
                latestLoginState = loginState
                publishAccountState()
            }
        }
        refreshCache()
        loadPlaybackPreferences()
        loadAudioSources()
    }

    fun refreshCache() {
        if (_uiState.value.cacheState.isRefreshing) {
            return
        }
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    cacheState = current.cacheState.copy(
                        isRefreshing = true,
                        feedbackMessage = null
                    )
                )
            }
            val currentSnapshot = _uiState.value.cacheState.snapshot
            val snapshot = runCatching {
                cacheRepository.readSnapshot()
            }.getOrElse {
                _uiState.update { current ->
                    current.copy(
                        cacheState = current.cacheState.copy(
                            isRefreshing = false,
                            feedbackMessage = "缓存统计失败，请重试"
                        )
                    )
                }
                return@launch
            }
            _uiState.update { current ->
                current.copy(
                    cacheState = current.cacheState.copy(
                        snapshot = snapshot ?: currentSnapshot,
                        isRefreshing = false,
                        feedbackMessage = null
                    )
                )
            }
        }
    }

    fun clearManagedCache() {
        if (_uiState.value.cacheState.isClearing) {
            return
        }
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    cacheState = current.cacheState.copy(
                        isClearing = true,
                        feedbackMessage = null
                    )
                )
            }

            val clearAccepted = runCatching {
                cacheController.clearPlaybackCache()
            }.getOrDefault(false)
            val clearSucceeded = if (clearAccepted) {
                runCatching {
                    cacheRepository.clearLyricsCache()
                    true
                }.getOrDefault(false)
            } else {
                false
            }
            val refreshedSnapshot = if (clearSucceeded) {
                awaitFreshCacheSnapshotAfterClear()
            } else {
                runCatching {
                    cacheRepository.readSnapshot()
                }.getOrElse {
                    _uiState.value.cacheState.snapshot ?: ManagedCacheSnapshot()
                }
            }
            _uiState.update { current ->
                current.copy(
                    cacheState = current.cacheState.copy(
                        snapshot = refreshedSnapshot,
                        isRefreshing = false,
                        isClearing = false,
                        feedbackMessage = if (clearSucceeded) {
                            "缓存已清理"
                        } else {
                            "清理缓存失败，请重试"
                        }
                    )
                )
            }
        }
    }

    fun updatePendingSourceName(value: String) {
        _uiState.update { current ->
            current.copy(
                sourcesState = current.sourcesState.copy(
                    pendingName = value,
                    validationMessage = null
                )
            )
        }
    }

    private suspend fun awaitFreshCacheSnapshotAfterClear(): ManagedCacheSnapshot {
        var latestSnapshot = runCatching {
            cacheRepository.readSnapshot()
        }.getOrElse {
            return _uiState.value.cacheState.snapshot ?: ManagedCacheSnapshot()
        }
        repeat(CACHE_CLEAR_REFRESH_RETRY_COUNT) {
            if (latestSnapshot.playbackCacheBytes() <= 0L) {
                return latestSnapshot
            }
            delay(CACHE_CLEAR_REFRESH_RETRY_DELAY_MS)
            latestSnapshot = runCatching {
                cacheRepository.readSnapshot()
            }.getOrElse { latestSnapshot }
        }
        return latestSnapshot
    }

    fun updatePendingSourceBaseUrl(value: String) {
        _uiState.update { current ->
            current.copy(
                sourcesState = current.sourcesState.copy(
                    pendingBaseUrl = value,
                    validationMessage = null
                )
            )
        }
    }

    fun updatePreferredAudioQuality(audioQuality: PlaybackAudioQuality) {
        if (_uiState.value.playbackPreferencesState.isSavingPreferredAudioQuality) {
            return
        }
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    playbackPreferencesState = current.playbackPreferencesState.copy(
                        isSavingPreferredAudioQuality = true,
                        isPreferredAudioQualityDialogVisible = false,
                        feedbackMessage = null
                    )
                )
            }
            playbackPreferencesRepository.writePreferredAudioQuality(audioQuality)
            val appliedImmediately = runCatching {
                playbackController.setPreferredAudioQuality(audioQuality)
            }.getOrDefault(false)
            _uiState.update { current ->
                current.copy(
                    playbackPreferencesState = current.playbackPreferencesState.copy(
                        preferredAudioQuality = audioQuality,
                        isSavingPreferredAudioQuality = false,
                        feedbackMessage = if (appliedImmediately) {
                            "默认音质已更新"
                        } else {
                            "默认音质已保存，播放进程重连后生效"
                        }
                    )
                )
            }
        }
    }

    fun showPreferredAudioQualityDialog() {
        _uiState.update { current ->
            current.copy(
                playbackPreferencesState = current.playbackPreferencesState.copy(
                    isPreferredAudioQualityDialogVisible = true,
                    feedbackMessage = null
                )
            )
        }
    }

    fun dismissPreferredAudioQualityDialog() {
        _uiState.update { current ->
            current.copy(
                playbackPreferencesState = current.playbackPreferencesState.copy(
                    isPreferredAudioQualityDialogVisible = false
                )
            )
        }
    }

    fun showAudioSourceValidationMessage(message: String) {
        _uiState.update { current ->
            current.copy(
                sourcesState = current.sourcesState.copy(
                    isImporting = false,
                    isSubmitting = false,
                    importFeedbackMessage = null,
                    validationMessage = message
                )
            )
        }
    }

    fun updatePendingPlaybackCacheLimitMb(value: String) {
        _uiState.update { current ->
            current.copy(
                cacheState = current.cacheState.copy(
                    pendingPlaybackCacheLimitMb = value.filter(Char::isDigit),
                    playbackCacheLimitMessage = null
                )
            )
        }
    }

    fun savePlaybackCacheLimit() {
        if (_uiState.value.cacheState.isSavingPlaybackCacheLimit) {
            return
        }
        viewModelScope.launch {
            val pendingValue = _uiState.value.cacheState.pendingPlaybackCacheLimitMb.trim()
            val pendingLimitMb = pendingValue.toLongOrNull()
            if (pendingLimitMb == null || pendingLimitMb <= 0L) {
                _uiState.update { current ->
                    current.copy(
                        cacheState = current.cacheState.copy(
                            playbackCacheLimitMessage = "缓存上限必须是大于 0 的整数 MB"
                        )
                    )
                }
                return@launch
            }
            val limitBytes = pendingLimitMb * BYTES_PER_MB
            _uiState.update { current ->
                current.copy(
                    cacheState = current.cacheState.copy(
                        isSavingPlaybackCacheLimit = true,
                        playbackCacheLimitMessage = null
                    )
                )
            }
            playbackPreferencesRepository.writePlaybackCacheLimitBytes(limitBytes)
            val appliedImmediately = runCatching {
                playbackController.setPlaybackCacheLimitBytes(limitBytes)
            }.getOrDefault(false)
            _uiState.update { current ->
                current.copy(
                    cacheState = current.cacheState.copy(
                        playbackCacheLimitBytes = limitBytes,
                        pendingPlaybackCacheLimitMb = pendingLimitMb.toString(),
                        isSavingPlaybackCacheLimit = false,
                        playbackCacheLimitMessage = if (appliedImmediately) {
                            "歌曲缓存上限已更新"
                        } else {
                            "歌曲缓存上限已保存，播放进程重连后生效"
                        }
                    )
                )
            }
        }
    }

    fun addAudioSource() {
        if (_uiState.value.sourcesState.isSubmitting) {
            return
        }
        viewModelScope.launch {
            val currentState = _uiState.value.sourcesState
            _uiState.update { current ->
                current.copy(
                    sourcesState = current.sourcesState.copy(
                        isSubmitting = true,
                        validationMessage = null
                    )
                )
            }
            val result = runCatching {
                audioSourceRepository.addSource(
                    displayName = currentState.pendingName,
                    baseUrl = currentState.pendingBaseUrl,
                    kind = currentState.pendingKind
                )
            }.getOrElse { throwable ->
                Result.failure(throwable)
            }

            if (result.isFailure) {
                _uiState.update { current ->
                    current.copy(
                        sourcesState = current.sourcesState.copy(
                            isSubmitting = false,
                            validationMessage = result.exceptionOrNull()?.message ?: "新增音源失败"
                        )
                    )
                }
                return@launch
            }

            val items = runCatching {
                audioSourceRepository.readSources()
            }.getOrDefault(_uiState.value.sourcesState.items + result.getOrThrow())
            _uiState.update { current ->
                current.copy(
                    sourcesState = current.sourcesState.copy(
                        items = items,
                        pendingName = "",
                        pendingBaseUrl = "",
                        isSubmitting = false,
                        validationMessage = null
                    )
                )
            }
        }
    }

    fun updatePendingImportUrl(value: String) {
        _uiState.update { current ->
            current.copy(
                sourcesState = current.sourcesState.copy(
                    pendingImportUrl = value,
                    importFeedbackMessage = null,
                    validationMessage = null
                )
            )
        }
    }

    fun importAudioSourceFromUrl() {
        if (_uiState.value.sourcesState.isImporting) {
            return
        }
        viewModelScope.launch {
            val importUrl = _uiState.value.sourcesState.pendingImportUrl
            _uiState.update { current ->
                current.copy(
                    sourcesState = current.sourcesState.copy(
                        isImporting = true,
                        importFeedbackMessage = null,
                        validationMessage = null
                    )
                )
            }
            val imported = audioSourceRepository.importSourceFromUrl(importUrl)
            if (imported.isFailure) {
                _uiState.update { current ->
                    current.copy(
                        sourcesState = current.sourcesState.copy(
                            isImporting = false,
                            validationMessage = imported.exceptionOrNull()?.message ?: "音源导入失败"
                        )
                    )
                }
                return@launch
            }
            val items = runCatching {
                audioSourceRepository.readSources()
            }.getOrDefault(_uiState.value.sourcesState.items + imported.getOrThrow())
            _uiState.update { current ->
                current.copy(
                    sourcesState = current.sourcesState.copy(
                        items = items,
                        pendingImportUrl = "",
                        isImporting = false,
                        importFeedbackMessage = "音源已导入",
                        validationMessage = null
                    )
                )
            }
        }
    }

    fun importAudioSourceFromLocalJson(
        rawJson: String,
        displayLabel: String? = null
    ) {
        if (_uiState.value.sourcesState.isImporting) {
            return
        }
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    sourcesState = current.sourcesState.copy(
                        isImporting = true,
                        importFeedbackMessage = null,
                        validationMessage = null
                    )
                )
            }
            val imported = audioSourceRepository.importSourceFromLocalJson(rawJson, displayLabel)
            if (imported.isFailure) {
                _uiState.update { current ->
                    current.copy(
                        sourcesState = current.sourcesState.copy(
                            isImporting = false,
                            validationMessage = imported.exceptionOrNull()?.message ?: "音源导入失败"
                        )
                    )
                }
                return@launch
            }
            val items = runCatching {
                audioSourceRepository.readSources()
            }.getOrDefault(_uiState.value.sourcesState.items + imported.getOrThrow())
            _uiState.update { current ->
                current.copy(
                    sourcesState = current.sourcesState.copy(
                        items = items,
                        isImporting = false,
                        importFeedbackMessage = "音源已导入",
                        validationMessage = null
                    )
                )
            }
        }
    }

    fun setActiveAudioSource(sourceId: String) {
        viewModelScope.launch {
            val result = audioSourceRepository.setActiveSource(sourceId)
            if (result.isFailure) {
                _uiState.update { current ->
                    current.copy(
                        sourcesState = current.sourcesState.copy(
                            validationMessage = result.exceptionOrNull()?.message ?: "切换音源失败"
                        )
                    )
                }
                return@launch
            }
            val items = result.getOrThrow()
            val activeSource = items.firstOrNull { it.isActive }
            val activeSourceConfigJson = activeSource
                ?.takeUnless { it.isBuiltIn }
                ?.sourceConfigJson
            playbackPreferencesRepository.writeActiveAudioSourceConfigJson(activeSourceConfigJson)
            runCatching {
                playbackController.setActiveAudioSourceConfigJson(activeSourceConfigJson)
            }
            _uiState.update { current ->
                current.copy(
                    sourcesState = current.sourcesState.copy(
                        items = items,
                        importFeedbackMessage = "当前音源已切换",
                        validationMessage = null
                    )
                )
            }
        }
    }

    fun removeAudioSource(sourceId: String) {
        viewModelScope.launch {
            val result = audioSourceRepository.removeSource(sourceId)
            if (result.isFailure) {
                _uiState.update { current ->
                    current.copy(
                        sourcesState = current.sourcesState.copy(
                            validationMessage = result.exceptionOrNull()?.message ?: "删除音源失败"
                        )
                    )
                }
                return@launch
            }
            val items = result.getOrThrow()
            val activeSource = items.firstOrNull { it.isActive }
            val activeSourceConfigJson = activeSource
                ?.takeUnless { it.isBuiltIn }
                ?.sourceConfigJson
            playbackPreferencesRepository.writeActiveAudioSourceConfigJson(activeSourceConfigJson)
            runCatching {
                playbackController.setActiveAudioSourceConfigJson(activeSourceConfigJson)
            }
            _uiState.update { current ->
                current.copy(
                    sourcesState = current.sourcesState.copy(
                        items = items,
                        importFeedbackMessage = "音源已删除",
                        validationMessage = null
                    )
                )
            }
        }
    }

    fun loadAudioSources() {
        viewModelScope.launch {
            val items = runCatching {
                audioSourceRepository.readSources()
            }.getOrElse { error ->
                _uiState.update { current ->
                    current.copy(
                        sourcesState = current.sourcesState.copy(
                            validationMessage = error.message ?: "音源加载失败"
                        )
                    )
                }
                return@launch
            }
            _uiState.update { current ->
                current.copy(
                    sourcesState = current.sourcesState.copy(
                        items = items
                    )
                )
            }
        }
    }

    fun showLogoutConfirmation() {
        _uiState.update { current ->
            current.copy(
                accountState = current.accountState.copy(
                    isLogoutConfirmVisible = current.accountState.isLoggedIn
                )
            )
        }
    }

    private fun loadPlaybackPreferences() {
        viewModelScope.launch {
            val preferredAudioQuality = runCatching {
                playbackPreferencesRepository.readPreferredAudioQuality()
            }.getOrDefault(PlaybackAudioQuality.EXHIGH)
            val playbackCacheLimitBytes = runCatching {
                playbackPreferencesRepository.readPlaybackCacheLimitBytes()
            }.getOrDefault(DEFAULT_PLAYBACK_CACHE_LIMIT_BYTES)
            _uiState.update { current ->
                current.copy(
                    playbackPreferencesState = current.playbackPreferencesState.copy(
                        preferredAudioQuality = preferredAudioQuality
                    ),
                    cacheState = current.cacheState.copy(
                        playbackCacheLimitBytes = playbackCacheLimitBytes,
                        pendingPlaybackCacheLimitMb =
                            (playbackCacheLimitBytes / BYTES_PER_MB).toString()
                    )
                )
            }
        }
    }

    fun dismissLogoutConfirmation() {
        _uiState.update { current ->
            current.copy(
                accountState = current.accountState.copy(
                    isLogoutConfirmVisible = false
                )
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            setAccountBusy(true)
            runCatching {
                userRepository.logout()
            }
            setAccountBusy(false)
        }
    }

    private fun setAccountBusy(isBusy: Boolean) {
        accountBusy = isBusy
        publishAccountState()
    }

    private fun publishAccountState() {
        val sessionState = latestLoginState.toUserSessionUiState(isBusy = accountBusy)
        _uiState.update { current ->
            current.copy(
                accountState = SettingsAccountUiState(
                    isLoggedIn = sessionState.isLoggedIn,
                    isBusy = sessionState.isBusy,
                    title = sessionState.title,
                    summary = sessionState.summary,
                    avatarUrl = sessionState.avatarUrl,
                    isLogoutConfirmVisible = current.accountState.isLogoutConfirmVisible &&
                        sessionState.isLoggedIn
                )
            )
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
        const val CACHE_CLEAR_REFRESH_RETRY_COUNT = 20
        const val CACHE_CLEAR_REFRESH_RETRY_DELAY_MS = 50L
    }
}

private fun ManagedCacheSnapshot.playbackCacheBytes(): Long {
    return entries.firstOrNull { it.kind == ManagedCacheKind.PLAYBACK }?.bytes ?: 0L
}
