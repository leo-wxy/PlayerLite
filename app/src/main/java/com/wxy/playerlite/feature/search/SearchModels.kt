package com.wxy.playerlite.feature.search

internal enum class SearchPageMode {
    HOT,
    SUGGEST,
    RESULT
}

internal data class SearchUiState(
    val query: String = "",
    val pageMode: SearchPageMode = SearchPageMode.HOT,
    val historyKeywords: List<String> = emptyList(),
    val hotState: SearchHotUiState = SearchHotUiState.Loading,
    val suggestState: SearchSuggestUiState = SearchSuggestUiState.Idle,
    val resultState: SearchResultUiState = SearchResultUiState.Idle,
    val lastSubmittedQuery: String = ""
)

internal sealed interface SearchHotUiState {
    data object Loading : SearchHotUiState

    data class Content(
        val items: List<SearchHotKeywordUiModel>
    ) : SearchHotUiState

    data class Error(
        val message: String
    ) : SearchHotUiState
}

internal sealed interface SearchSuggestUiState {
    data object Idle : SearchSuggestUiState
    data object Loading : SearchSuggestUiState

    data class Content(
        val items: List<SearchSuggestionUiModel>
    ) : SearchSuggestUiState

    data class Error(
        val message: String
    ) : SearchSuggestUiState
}

internal sealed interface SearchResultUiState {
    data object Idle : SearchResultUiState
    data object Loading : SearchResultUiState
    data object Empty : SearchResultUiState

    data class Content(
        val items: List<SearchResultItemUiModel>
    ) : SearchResultUiState

    data class Error(
        val message: String
    ) : SearchResultUiState
}

internal data class SearchHotKeywordUiModel(
    val keyword: String,
    val score: Int = 0,
    val iconType: Int = 0,
    val iconUrl: String? = null,
    val content: String = ""
)

internal data class SearchSuggestionUiModel(
    val keyword: String
)

internal data class SearchResultItemUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String?
)
