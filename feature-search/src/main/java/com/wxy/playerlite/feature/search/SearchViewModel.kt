package com.wxy.playerlite.feature.search

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class SearchViewModel(
    application: Application,
    private val repository: SearchRepository,
    private val suggestDebounceMs: Long = DEFAULT_SUGGEST_DEBOUNCE_MS
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SearchUiState())
    private var suggestJob: Job? = null
    private val resultJobsByType = linkedMapOf<SearchResultType, Job>()

    val uiStateFlow: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = _uiState.value.copy(
            historyKeywords = repository.readSearchHistory()
        )
        loadHotKeywords()
    }

    fun onQueryChanged(query: String) {
        suggestJob?.cancel()
        _uiState.value = _uiState.value.copy(query = query)
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                pageMode = SearchPageMode.HOT,
                suggestState = SearchSuggestUiState.Idle,
                resultStatesByType = emptyMap(),
                lastSubmittedQuery = ""
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            pageMode = SearchPageMode.SUGGEST,
            suggestState = SearchSuggestUiState.Loading
        )
        suggestJob = viewModelScope.launch {
            delay(suggestDebounceMs)
            val keyword = _uiState.value.query.trim()
            if (keyword.isBlank()) {
                return@launch
            }
            runCatching {
                repository.fetchSuggestions(keyword)
            }.onSuccess { items ->
                if (_uiState.value.query.trim() == keyword) {
                    _uiState.value = _uiState.value.copy(
                        pageMode = SearchPageMode.SUGGEST,
                        suggestState = SearchSuggestUiState.Content(items)
                    )
                }
            }.onFailure { error ->
                if (_uiState.value.query.trim() == keyword) {
                    _uiState.value = _uiState.value.copy(
                        pageMode = SearchPageMode.SUGGEST,
                        suggestState = SearchSuggestUiState.Error(
                            message = error.message ?: "搜索建议加载失败"
                        )
                    )
                }
            }
        }
    }

    fun submitSearch(
        queryOverride: String = _uiState.value.query,
        resultType: SearchResultType = _uiState.value.selectedResultType,
        recordHistory: Boolean = true,
        forceRefresh: Boolean = false
    ) {
        suggestJob?.cancel()
        val keyword = queryOverride.trim()
        if (keyword.isBlank()) {
            onQueryChanged("")
            return
        }
        val currentState = _uiState.value
        val isNewQuery = keyword != currentState.lastSubmittedQuery
        if (isNewQuery) {
            cancelAllResultJobs()
        }
        val baseResultStates = if (isNewQuery) {
            emptyMap()
        } else {
            currentState.resultStatesByType
        }
        val existingPageState = baseResultStates[resultType]
        _uiState.value = currentState.copy(
            query = keyword,
            pageMode = SearchPageMode.RESULT,
            lastSubmittedQuery = keyword,
            selectedResultType = resultType,
            resultStatesByType = baseResultStates
        )

        if (recordHistory) {
            viewModelScope.launch {
                repository.recordSearchHistory(keyword)
                _uiState.value = _uiState.value.copy(
                    historyKeywords = repository.readSearchHistory()
                )
            }
        }

        if (!forceRefresh && existingPageState != null && existingPageState !is SearchResultUiState.Loading) {
            return
        }

        if (!forceRefresh && resultJobsByType[resultType]?.isActive == true) {
            return
        }

        val loadingStates = baseResultStates + (resultType to SearchResultUiState.Loading)
        _uiState.value = _uiState.value.copy(
            resultStatesByType = loadingStates
        )
        resultJobsByType[resultType]?.cancel()
        val job = viewModelScope.launch {
            runCatching {
                repository.search(keyword, resultType)
            }.onSuccess { items ->
                if (_uiState.value.lastSubmittedQuery != keyword) {
                    return@onSuccess
                }
                val resultState = if (items.isEmpty()) {
                    SearchResultUiState.Empty
                } else {
                    SearchResultUiState.Content(items)
                }
                _uiState.value = _uiState.value.copy(
                    pageMode = SearchPageMode.RESULT,
                    resultStatesByType = _uiState.value.resultStatesByType + (resultType to resultState)
                )
            }.onFailure { error ->
                if (_uiState.value.lastSubmittedQuery != keyword) {
                    return@onFailure
                }
                val errorState = SearchResultUiState.Error(
                    message = error.message ?: "搜索结果加载失败"
                )
                _uiState.value = _uiState.value.copy(
                    pageMode = SearchPageMode.RESULT,
                    resultStatesByType = _uiState.value.resultStatesByType + (resultType to errorState)
                )
            }
        }
        resultJobsByType[resultType] = job
        job.invokeOnCompletion {
            if (resultJobsByType[resultType] === job) {
                resultJobsByType.remove(resultType)
            }
        }
    }

    fun onSuggestionClick(item: SearchSuggestionUiModel) {
        _uiState.value = _uiState.value.copy(query = item.keyword)
        submitSearch(item.keyword)
    }

    fun onHotKeywordClick(item: SearchHotKeywordUiModel) {
        _uiState.value = _uiState.value.copy(query = item.keyword)
        submitSearch(item.keyword)
    }

    fun onResultTypeSelected(type: SearchResultType) {
        if (_uiState.value.selectedResultType == type) {
            return
        }
        val lastSubmittedQuery = _uiState.value.lastSubmittedQuery
        if (lastSubmittedQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(selectedResultType = type)
            return
        }
        submitSearch(
            queryOverride = lastSubmittedQuery,
            resultType = type,
            recordHistory = false,
            forceRefresh = false
        )
    }

    fun retryCurrentMode() {
        when (_uiState.value.pageMode) {
            SearchPageMode.HOT -> loadHotKeywords()
            SearchPageMode.SUGGEST -> onQueryChanged(_uiState.value.query)
            SearchPageMode.RESULT -> submitSearch(
                queryOverride = _uiState.value.lastSubmittedQuery,
                resultType = _uiState.value.selectedResultType,
                recordHistory = false,
                forceRefresh = true
            )
        }
    }

    private fun loadHotKeywords() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                pageMode = SearchPageMode.HOT,
                hotState = SearchHotUiState.Loading
            )
            runCatching {
                repository.fetchHotKeywords()
            }.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    hotState = SearchHotUiState.Content(items)
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    hotState = SearchHotUiState.Error(
                        message = error.message ?: "热搜加载失败"
                    )
                )
            }
        }
    }

    private fun cancelAllResultJobs() {
        resultJobsByType.values.forEach { job ->
            job.cancel()
        }
        resultJobsByType.clear()
    }

    companion object {
        private const val DEFAULT_SUGGEST_DEBOUNCE_MS = 300L

        fun factory(
            application: Application,
            repository: SearchRepository,
            suggestDebounceMs: Long = DEFAULT_SUGGEST_DEBOUNCE_MS
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    check(modelClass.isAssignableFrom(SearchViewModel::class.java))
                    @Suppress("UNCHECKED_CAST")
                    return SearchViewModel(
                        application = application,
                        repository = repository,
                        suggestDebounceMs = suggestDebounceMs
                    ) as T
                }
            }
        }
    }
}
