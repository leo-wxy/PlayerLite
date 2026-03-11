package com.wxy.playerlite.feature.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxy.playerlite.core.AppContainer
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
    constructor(application: Application) : this(
        application = application,
        repository = AppContainer.searchRepository(application.applicationContext)
    )

    private val _uiState = MutableStateFlow(SearchUiState())
    private var suggestJob: Job? = null

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
                suggestState = SearchSuggestUiState.Idle
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

    fun submitSearch(queryOverride: String = _uiState.value.query) {
        suggestJob?.cancel()
        val keyword = queryOverride.trim()
        if (keyword.isBlank()) {
            onQueryChanged("")
            return
        }
        _uiState.value = _uiState.value.copy(
            query = keyword,
            pageMode = SearchPageMode.RESULT,
            lastSubmittedQuery = keyword,
            resultState = SearchResultUiState.Loading
        )
        viewModelScope.launch {
            repository.recordSearchHistory(keyword)
            _uiState.value = _uiState.value.copy(
                historyKeywords = repository.readSearchHistory()
            )
            runCatching {
                repository.search(keyword)
            }.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    pageMode = SearchPageMode.RESULT,
                    resultState = if (items.isEmpty()) {
                        SearchResultUiState.Empty
                    } else {
                        SearchResultUiState.Content(items)
                    }
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    pageMode = SearchPageMode.RESULT,
                    resultState = SearchResultUiState.Error(
                        message = error.message ?: "搜索结果加载失败"
                    )
                )
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

    fun retryCurrentMode() {
        when (_uiState.value.pageMode) {
            SearchPageMode.HOT -> loadHotKeywords()
            SearchPageMode.SUGGEST -> onQueryChanged(_uiState.value.query)
            SearchPageMode.RESULT -> submitSearch(_uiState.value.lastSubmittedQuery)
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

    private companion object {
        const val DEFAULT_SUGGEST_DEBOUNCE_MS = 300L
    }
}
