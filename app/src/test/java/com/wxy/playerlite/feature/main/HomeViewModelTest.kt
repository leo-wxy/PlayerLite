package com.wxy.playerlite.feature.main

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun refresh_shouldKeepPreviousSectionsWhenLatestRequestFails() = runTest {
        val repository = FakeHomeDiscoveryRepository(
            results = ArrayDeque<HomeOverviewResult>().apply {
                add(
                    HomeOverviewResult.Success(
                        HomeOverviewContent(
                            sections = listOf(
                                HomeSectionUiModel(
                                    code = "HOMEPAGE_BLOCK_PLAYLIST_RCMD",
                                    title = "推荐歌单",
                                    layout = HomeSectionLayout.HORIZONTAL_LIST,
                                    items = listOf(
                                        HomeSectionItemUiModel(
                                            id = "playlist-1",
                                            title = "歌单 A",
                                            subtitle = "晚安歌单",
                                            imageUrl = null
                                        )
                                    )
                                )
                            ),
                            searchKeywords = listOf("默认热搜")
                        )
                    )
                )
                add(HomeOverviewResult.Failure(IllegalStateException("boom")))
            }
        )
        val viewModel = HomeViewModel(
            application = Application(),
            repository = repository,
            keywordRotationIntervalMs = 0L
        )
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("推荐歌单", viewModel.uiStateFlow.value.sections.single().title)
        assertEquals("boom", viewModel.uiStateFlow.value.errorMessage)
        assertFalse(viewModel.uiStateFlow.value.isLoading)
    }

    @Test
    fun advanceSearchKeyword_shouldRotateToNextKeyword() = runTest {
        val repository = FakeHomeDiscoveryRepository(
            results = ArrayDeque<HomeOverviewResult>().apply {
                add(
                    HomeOverviewResult.Success(
                        HomeOverviewContent(
                            sections = emptyList(),
                            searchKeywords = listOf("默认热搜", "热搜A")
                        )
                    )
                )
            }
        )
        val viewModel = HomeViewModel(
            application = Application(),
            repository = repository,
            keywordRotationIntervalMs = 0L
        )
        advanceUntilIdle()

        viewModel.advanceSearchKeyword()

        assertEquals("热搜A", viewModel.uiStateFlow.value.currentSearchKeyword)
        assertTrue(viewModel.uiStateFlow.value.searchKeywords.size > 1)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeHomeDiscoveryRepository(
    private val results: ArrayDeque<HomeOverviewResult>
) : HomeDiscoveryRepository {
    override suspend fun fetchHomeOverview(): HomeOverviewContent {
        return when (val result = results.removeFirst()) {
            is HomeOverviewResult.Success -> result.content
            is HomeOverviewResult.Failure -> throw result.error
        }
    }
}

private sealed interface HomeOverviewResult {
    data class Success(val content: HomeOverviewContent) : HomeOverviewResult
    data class Failure(val error: Throwable) : HomeOverviewResult
}
