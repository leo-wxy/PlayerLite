package com.wxy.playerlite.feature.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
            searchResults["小美满" to SearchResultType.SONG] = listOf(
                SearchResultUiModel.Song(
                    id = "1",
                    title = "小美满",
                    artistText = "周深",
                    albumTitle = "小美满",
                    coverUrl = "http://example.com/a.jpg",
                    routeTarget = SearchRouteTarget.Song(songId = "1")
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
        assertEquals(SearchResultType.SONG, viewModel.uiStateFlow.value.selectedResultType)
        assertEquals(listOf("小美满"), repository.historyKeywords)
        val resultState = viewModel.uiStateFlow.value.resultState as SearchResultUiState.Content
        assertEquals("小美满", (resultState.items.single() as SearchResultUiModel.Song).title)
    }

    @Test
    fun retrySearch_shouldRetryLastSubmittedQueryAfterFailure() = runTest {
        val repository = FakeSearchRepository().apply {
            searchFailures["周" to SearchResultType.SONG] = ArrayDeque<Throwable>().apply {
                add(IllegalStateException("boom"))
            }
            searchResults["周" to SearchResultType.SONG] = listOf(
                SearchResultUiModel.Song(
                    id = "2",
                    title = "周",
                    artistText = "Bethybai",
                    albumTitle = "电子道种",
                    coverUrl = null,
                    routeTarget = SearchRouteTarget.Song(songId = "2")
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
        assertEquals("周", (resultState.items.single() as SearchResultUiModel.Song).title)
    }

    @Test
    fun onHotKeywordClick_shouldSubmitSearchAndPromoteHistory() = runTest {
        val repository = FakeSearchRepository().apply {
            hotKeywords = listOf(
                SearchHotKeywordUiModel(keyword = "海屿你", score = 10, iconType = 4)
            )
            searchResults["海屿你" to SearchResultType.SONG] = listOf(
                SearchResultUiModel.Song(
                    id = "1",
                    title = "海屿你",
                    artistText = "歌手 A",
                    albumTitle = "专辑 B",
                    coverUrl = null,
                    routeTarget = SearchRouteTarget.Song(songId = "1")
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

    @Test
    fun onResultTypeSelected_shouldLoadCurrentQueryWithSelectedTypeWithoutDuplicatingHistory() = runTest {
        val repository = FakeSearchRepository().apply {
            searchResults["周杰伦" to SearchResultType.SONG] = listOf(
                SearchResultUiModel.Song(
                    id = "song-1",
                    title = "晴天",
                    artistText = "周杰伦",
                    albumTitle = "叶惠美",
                    coverUrl = null,
                    routeTarget = SearchRouteTarget.Song(songId = "song-1")
                )
            )
            searchResults["周杰伦" to SearchResultType.ALBUM] = listOf(
                SearchResultUiModel.Album(
                    id = "album-1",
                    title = "叶惠美",
                    artistText = "周杰伦",
                    songCount = 11,
                    coverUrl = null,
                    routeTarget = SearchRouteTarget.Album(albumId = "album-1")
                )
            )
        }

        val viewModel = SearchViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            suggestDebounceMs = 300L
        )
        advanceUntilIdle()

        viewModel.onQueryChanged("周杰伦")
        viewModel.submitSearch()
        advanceUntilIdle()
        viewModel.onResultTypeSelected(SearchResultType.ALBUM)
        advanceUntilIdle()

        assertEquals(SearchResultType.ALBUM, viewModel.uiStateFlow.value.selectedResultType)
        assertEquals(listOf("周杰伦"), repository.historyKeywords)
        assertEquals(
            listOf(
                "周杰伦" to SearchResultType.SONG,
                "周杰伦" to SearchResultType.ALBUM
            ),
            repository.searchRequests
        )
        val resultState = viewModel.uiStateFlow.value.resultState as SearchResultUiState.Content
        assertTrue(resultState.items.single() is SearchResultUiModel.Album)
        assertTrue(
            (viewModel.uiStateFlow.value.resultStatesByType[SearchResultType.SONG] as SearchResultUiState.Content)
                .items.single() is SearchResultUiModel.Song
        )
        assertTrue(
            (viewModel.uiStateFlow.value.resultStatesByType[SearchResultType.ALBUM] as SearchResultUiState.Content)
                .items.single() is SearchResultUiModel.Album
        )
    }

    @Test
    fun onResultTypeSelected_shouldKeepLoadedPageContentWhenSwitchingBack() = runTest {
        val repository = FakeSearchRepository().apply {
            searchResults["周杰伦" to SearchResultType.SONG] = listOf(
                SearchResultUiModel.Song(
                    id = "song-1",
                    title = "晴天",
                    artistText = "周杰伦",
                    albumTitle = "叶惠美",
                    coverUrl = null,
                    routeTarget = SearchRouteTarget.Song(songId = "song-1")
                )
            )
            searchResults["周杰伦" to SearchResultType.ALBUM] = listOf(
                SearchResultUiModel.Album(
                    id = "album-1",
                    title = "叶惠美",
                    artistText = "周杰伦",
                    songCount = 11,
                    coverUrl = null,
                    routeTarget = SearchRouteTarget.Album(albumId = "album-1")
                )
            )
            searchDelaysMs["周杰伦" to SearchResultType.SONG] = 1_000L
        }

        val viewModel = SearchViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            suggestDebounceMs = 300L
        )
        advanceUntilIdle()

        viewModel.onQueryChanged("周杰伦")
        viewModel.submitSearch()
        advanceUntilIdle()

        viewModel.onResultTypeSelected(SearchResultType.ALBUM)
        advanceUntilIdle()
        val loadedAlbumState = viewModel.uiStateFlow.value.resultState as SearchResultUiState.Content
        assertTrue(loadedAlbumState.items.single() is SearchResultUiModel.Album)

        viewModel.onResultTypeSelected(SearchResultType.SONG)

        assertEquals(SearchResultType.SONG, viewModel.uiStateFlow.value.selectedResultType)
        val visibleState = viewModel.uiStateFlow.value.resultState as SearchResultUiState.Content
        assertTrue(visibleState.items.single() is SearchResultUiModel.Song)
        assertEquals(
            listOf(
                "周杰伦" to SearchResultType.SONG,
                "周杰伦" to SearchResultType.ALBUM
            ),
            repository.searchRequests
        )

        advanceTimeBy(999L)
        val stillVisibleState = viewModel.uiStateFlow.value.resultState as SearchResultUiState.Content
        assertTrue(stillVisibleState.items.single() is SearchResultUiModel.Song)
        advanceTimeBy(1L)
        advanceUntilIdle()

        val resultState = viewModel.uiStateFlow.value.resultState as SearchResultUiState.Content
        assertTrue(resultState.items.single() is SearchResultUiModel.Song)
        assertEquals(
            listOf(
                "周杰伦" to SearchResultType.SONG,
                "周杰伦" to SearchResultType.ALBUM
            ),
            repository.searchRequests
        )
    }
}

private class FakeSearchRepository : SearchRepository {
    var hotKeywords: List<SearchHotKeywordUiModel> = emptyList()
    var historyKeywords: List<String> = emptyList()
    val suggestions: MutableMap<String, List<SearchSuggestionUiModel>> = linkedMapOf()
    val searchResults: MutableMap<Pair<String, SearchResultType>, List<SearchResultUiModel>> = linkedMapOf()
    val searchFailures: MutableMap<Pair<String, SearchResultType>, ArrayDeque<Throwable>> = linkedMapOf()
    val searchDelaysMs: MutableMap<Pair<String, SearchResultType>, Long> = linkedMapOf()
    val searchRequests: MutableList<Pair<String, SearchResultType>> = mutableListOf()

    override suspend fun fetchHotKeywords(): List<SearchHotKeywordUiModel> = hotKeywords

    override suspend fun fetchSuggestions(keyword: String): List<SearchSuggestionUiModel> {
        return suggestions[keyword].orEmpty()
    }

    override suspend fun search(keyword: String, type: SearchResultType): List<SearchResultUiModel> {
        searchRequests += keyword to type
        searchDelaysMs[keyword to type]?.let { delayMs ->
            delay(delayMs)
        }
        val failures = searchFailures[keyword to type]
        if (failures != null && failures.isNotEmpty()) {
            throw failures.removeFirst()
        }
        return searchResults[keyword to type].orEmpty()
    }

    override fun readSearchHistory(): List<String> = historyKeywords

    override suspend fun recordSearchHistory(keyword: String) {
        historyKeywords = listOf(keyword) + historyKeywords.filterNot { it == keyword }
    }
}
