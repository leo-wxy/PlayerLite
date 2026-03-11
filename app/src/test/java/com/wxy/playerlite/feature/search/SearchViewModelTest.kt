package com.wxy.playerlite.feature.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_shouldLoadHotKeywordsIntoHotMode() = runTest {
        val repository = FakeSearchRepository().apply {
            historyKeywords = listOf("周深", "小美满")
            hotKeywords = listOf(
                SearchHotKeywordUiModel(keyword = "热搜 A", score = 1, iconType = 0),
                SearchHotKeywordUiModel(keyword = "热搜 B", score = 2, iconType = 0)
            )
        }

        val viewModel = SearchViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            suggestDebounceMs = 300L
        )
        advanceUntilIdle()

        assertEquals(SearchPageMode.HOT, viewModel.uiStateFlow.value.pageMode)
        assertEquals(listOf("周深", "小美满"), viewModel.uiStateFlow.value.historyKeywords)
        val hotState = viewModel.uiStateFlow.value.hotState as SearchHotUiState.Content
        assertEquals(listOf("热搜 A", "热搜 B"), hotState.items.map { it.keyword })
    }

    @Test
    fun onQueryChanged_shouldLoadSuggestionsAfterDebounce() = runTest {
        val repository = FakeSearchRepository().apply {
            suggestions["周"] = listOf(
                SearchSuggestionUiModel(keyword = "周"),
                SearchSuggestionUiModel(keyword = "周深")
            )
        }

        val viewModel = SearchViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            suggestDebounceMs = 300L
        )
        advanceUntilIdle()

        viewModel.onQueryChanged("周")
        advanceTimeBy(299L)
        assertEquals(SearchSuggestUiState.Loading, viewModel.uiStateFlow.value.suggestState)

        advanceTimeBy(1L)
        advanceUntilIdle()

        assertEquals(SearchPageMode.SUGGEST, viewModel.uiStateFlow.value.pageMode)
        val suggestState = viewModel.uiStateFlow.value.suggestState as SearchSuggestUiState.Content
        assertEquals(listOf("周", "周深"), suggestState.items.map { it.keyword })
    }

    @Test
    fun clearQuery_shouldReturnToHotMode() = runTest {
        val repository = FakeSearchRepository().apply {
            historyKeywords = listOf("周深")
            hotKeywords = listOf(SearchHotKeywordUiModel(keyword = "热搜 A", score = 1, iconType = 0))
            suggestions["周"] = listOf(SearchSuggestionUiModel(keyword = "周深"))
        }

        val viewModel = SearchViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            suggestDebounceMs = 300L
        )
        advanceUntilIdle()

        viewModel.onQueryChanged("周")
        advanceTimeBy(300L)
        advanceUntilIdle()
        viewModel.onQueryChanged("")
        advanceUntilIdle()

        assertEquals(SearchPageMode.HOT, viewModel.uiStateFlow.value.pageMode)
        assertEquals("", viewModel.uiStateFlow.value.query)
        assertEquals(listOf("周深"), viewModel.uiStateFlow.value.historyKeywords)
        assertTrue(viewModel.uiStateFlow.value.suggestState is SearchSuggestUiState.Idle)
    }

    @Test
    fun submitSearch_shouldExposeResultItemsAndRememberSubmittedQuery() = runTest {
        val repository = FakeSearchRepository().apply {
            searchResults["小美满"] = listOf(
                SearchResultItemUiModel(
                    id = "1",
                    title = "小美满",
                    subtitle = "周深 · 小美满",
                    coverUrl = "http://example.com/a.jpg"
                )
            )
        }

        val viewModel = SearchViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            suggestDebounceMs = 300L
        )
        advanceUntilIdle()

        viewModel.onQueryChanged("小美满")
        viewModel.submitSearch()
        advanceUntilIdle()

        assertEquals(SearchPageMode.RESULT, viewModel.uiStateFlow.value.pageMode)
        assertEquals("小美满", viewModel.uiStateFlow.value.lastSubmittedQuery)
        assertEquals(listOf("小美满"), repository.historyKeywords)
        val resultState = viewModel.uiStateFlow.value.resultState as SearchResultUiState.Content
        assertEquals("小美满", resultState.items.single().title)
    }

    @Test
    fun retrySearch_shouldRetryLastSubmittedQueryAfterFailure() = runTest {
        val repository = FakeSearchRepository().apply {
            searchFailures["周"] = ArrayDeque<Throwable>().apply {
                add(IllegalStateException("boom"))
            }
            searchResults["周"] = listOf(
                SearchResultItemUiModel(
                    id = "2",
                    title = "周",
                    subtitle = "Bethybai · 电子道种",
                    coverUrl = null
                )
            )
        }

        val viewModel = SearchViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            suggestDebounceMs = 300L
        )
        advanceUntilIdle()

        viewModel.onQueryChanged("周")
        viewModel.submitSearch()
        advanceUntilIdle()
        assertTrue(viewModel.uiStateFlow.value.resultState is SearchResultUiState.Error)

        viewModel.retryCurrentMode()
        advanceUntilIdle()

        val resultState = viewModel.uiStateFlow.value.resultState as SearchResultUiState.Content
        assertEquals("周", resultState.items.single().title)
    }

    @Test
    fun onHotKeywordClick_shouldSubmitSearchAndPromoteHistory() = runTest {
        val repository = FakeSearchRepository().apply {
            hotKeywords = listOf(
                SearchHotKeywordUiModel(keyword = "海屿你", score = 10, iconType = 4)
            )
            searchResults["海屿你"] = listOf(
                SearchResultItemUiModel(
                    id = "1",
                    title = "海屿你",
                    subtitle = "歌手 A · 专辑 B",
                    coverUrl = null
                )
            )
        }

        val viewModel = SearchViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            suggestDebounceMs = 300L
        )
        advanceUntilIdle()

        viewModel.onHotKeywordClick(repository.hotKeywords.single())
        advanceUntilIdle()

        assertEquals(SearchPageMode.RESULT, viewModel.uiStateFlow.value.pageMode)
        assertEquals(listOf("海屿你"), repository.historyKeywords)
    }
}

private class FakeSearchRepository : SearchRepository {
    var hotKeywords: List<SearchHotKeywordUiModel> = emptyList()
    var historyKeywords: List<String> = emptyList()
    val suggestions: MutableMap<String, List<SearchSuggestionUiModel>> = linkedMapOf()
    val searchResults: MutableMap<String, List<SearchResultItemUiModel>> = linkedMapOf()
    val searchFailures: MutableMap<String, ArrayDeque<Throwable>> = linkedMapOf()

    override suspend fun fetchHotKeywords(): List<SearchHotKeywordUiModel> = hotKeywords

    override suspend fun fetchSuggestions(keyword: String): List<SearchSuggestionUiModel> {
        return suggestions[keyword].orEmpty()
    }

    override suspend fun search(keyword: String): List<SearchResultItemUiModel> {
        val failures = searchFailures[keyword]
        if (failures != null && failures.isNotEmpty()) {
            throw failures.removeFirst()
        }
        return searchResults[keyword].orEmpty()
    }

    override fun readSearchHistory(): List<String> = historyKeywords

    override suspend fun recordSearchHistory(keyword: String) {
        historyKeywords = listOf(keyword) + historyKeywords.filterNot { it == keyword }
    }
}
