package com.wxy.playerlite.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeViewModel(
    application: Application,
    private val repository: HomeDiscoveryRepository,
    private val keywordRotationIntervalMs: Long
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        repository = application.applicationContext.requireHomeHostDependencies().repository,
        keywordRotationIntervalMs = DEFAULT_ROTATION_INTERVAL_MS
    )

    private val _uiState = MutableStateFlow(HomeOverviewUiState())
    private val rotationJob: Job?

    val uiStateFlow: StateFlow<HomeOverviewUiState> = _uiState.asStateFlow()

    init {
        refresh()
        rotationJob = if (keywordRotationIntervalMs > 0L) {
            viewModelScope.launch {
                while (isActive) {
                    delay(keywordRotationIntervalMs)
                    advanceSearchKeyword()
                }
            }
        } else {
            null
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val previous = _uiState.value
            _uiState.value = previous.copy(
                isLoading = true,
                errorMessage = null
            )
            runCatching {
                repository.fetchHomeOverview()
            }.onSuccess { content ->
                _uiState.value = HomeOverviewUiState(
                    isLoading = false,
                    sections = content.sections,
                    searchKeywords = content.searchKeywords.ifEmpty {
                        HomeDefaults.fallbackSearchKeywords
                    },
                    currentSearchKeywordIndex = 0,
                    errorMessage = null
                )
            }.onFailure { error ->
                _uiState.value = previous.copy(
                    isLoading = false,
                    sections = if (previous.sections.isNotEmpty()) previous.sections else emptyList(),
                    searchKeywords = previous.searchKeywords.ifEmpty {
                        HomeDefaults.fallbackSearchKeywords
                    },
                    errorMessage = error.message ?: "首页加载失败"
                )
            }
        }
    }

    override fun onCleared() {
        rotationJob?.cancel()
        super.onCleared()
    }

    internal fun advanceSearchKeyword() {
        val current = _uiState.value
        if (current.searchKeywords.size <= 1) {
            return
        }
        _uiState.value = current.copy(
            currentSearchKeywordIndex = (current.currentSearchKeywordIndex + 1) % current.searchKeywords.size
        )
    }

    private companion object {
        const val DEFAULT_ROTATION_INTERVAL_MS = 3_000L
    }
}
