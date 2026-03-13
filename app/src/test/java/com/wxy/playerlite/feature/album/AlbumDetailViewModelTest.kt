package com.wxy.playerlite.feature.album

import com.wxy.playerlite.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_shouldPublishContentAndDynamic() = runTest {
        val repository = FakeAlbumDetailRepository(
            contentResults = ArrayDeque(
                listOf(
                    Result.success(
                        AlbumDetailContent(
                            albumId = "32311",
                            title = "神的游戏",
                            artistText = "张悬",
                            description = "专辑简介",
                            coverUrl = "http://example.com/album.jpg",
                            company = "索尼音乐",
                            publishTimeText = "2012-08-10",
                            trackCount = 9,
                            tracks = listOf(
                                AlbumTrackRow(
                                    trackId = "326696",
                                    title = "疯狂的阳光",
                                    artistText = "张悬",
                                    albumTitle = "神的游戏",
                                    coverUrl = "http://example.com/track.jpg",
                                    durationMs = 235146L
                                )
                            )
                        )
                    )
                )
            ),
            dynamicResult = Result.success(
                AlbumDynamicInfo(
                    commentCount = 1990,
                    shareCount = 8542,
                    subscribedCount = 66888
                )
            )
        )

        val viewModel = AlbumDetailViewModel(
            albumId = "32311",
            repository = repository
        )
        advanceUntilIdle()

        val state = viewModel.uiStateFlow.value
        assertTrue(state.contentState is AlbumContentUiState.Content)
        assertTrue(state.dynamicState is AlbumDynamicUiState.Content)
        assertEquals(
            "神的游戏",
            (state.contentState as AlbumContentUiState.Content).content.title
        )
        assertEquals(
            1990,
            (state.dynamicState as AlbumDynamicUiState.Content).content.commentCount
        )
    }

    @Test
    fun retry_shouldKeepContentWhenDynamicFails() = runTest {
        val repository = FakeAlbumDetailRepository(
            contentResults = ArrayDeque(
                listOf(
                    Result.success(
                        AlbumDetailContent(
                            albumId = "32311",
                            title = "神的游戏",
                            artistText = "张悬",
                            description = "专辑简介",
                            coverUrl = null,
                            company = "索尼音乐",
                            publishTimeText = "2012-08-10",
                            trackCount = 9,
                            tracks = emptyList()
                        )
                    )
                )
            ),
            dynamicResult = Result.failure(IllegalStateException("专辑动态信息加载失败"))
        )

        val viewModel = AlbumDetailViewModel(
            albumId = "32311",
            repository = repository
        )
        advanceUntilIdle()

        val state = viewModel.uiStateFlow.value
        assertTrue(state.contentState is AlbumContentUiState.Content)
        assertEquals(
            "专辑动态信息加载失败",
            (state.dynamicState as AlbumDynamicUiState.Error).message
        )
    }

    @Test
    fun loadMoreTracks_shouldAppendNextPageAndMarkEndReached() = runTest {
        val repository = FakeAlbumDetailRepository(
            contentResults = ArrayDeque(
                listOf(
                    Result.success(
                        AlbumDetailContent(
                            albumId = "32311",
                            title = "神的游戏",
                            artistText = "张悬",
                            description = "专辑简介",
                            coverUrl = null,
                            company = "索尼音乐",
                            publishTimeText = "2012-08-10",
                            trackCount = 2,
                            tracks = listOf(
                                AlbumTrackRow(
                                    trackId = "track-1",
                                    title = "疯狂的阳光",
                                    artistText = "张悬",
                                    albumTitle = "神的游戏",
                                    coverUrl = null,
                                    durationMs = 235146L
                                )
                            )
                        )
                    ),
                    Result.success(
                        AlbumDetailContent(
                            albumId = "32311",
                            title = "神的游戏",
                            artistText = "张悬",
                            description = "专辑简介",
                            coverUrl = null,
                            company = "索尼音乐",
                            publishTimeText = "2012-08-10",
                            trackCount = 2,
                            tracks = listOf(
                                AlbumTrackRow(
                                    trackId = "track-2",
                                    title = "两者",
                                    artistText = "张悬",
                                    albumTitle = "神的游戏",
                                    coverUrl = null,
                                    durationMs = 200000L
                                )
                            )
                        )
                    )
                )
            ),
            dynamicResult = Result.success(
                AlbumDynamicInfo(
                    commentCount = 1990,
                    shareCount = 8542,
                    subscribedCount = 66888
                )
            )
        )

        val viewModel = AlbumDetailViewModel(
            albumId = "32311",
            repository = repository,
            pageSize = 1
        )
        advanceUntilIdle()

        viewModel.loadMoreTracks()
        advanceUntilIdle()

        val contentState = viewModel.uiStateFlow.value.contentState as AlbumContentUiState.Content
        assertEquals(listOf(0 to 1, 1 to 1), repository.pageRequests)
        assertEquals(2, contentState.content.tracks.size)
        assertTrue(contentState.endReached)
    }

    @Test
    fun loadMoreTracks_failureShouldPreserveLoadedItemsAndExposeRetryState() = runTest {
        val repository = FakeAlbumDetailRepository(
            contentResults = ArrayDeque(
                listOf(
                    Result.success(
                        AlbumDetailContent(
                            albumId = "32311",
                            title = "神的游戏",
                            artistText = "张悬",
                            description = "专辑简介",
                            coverUrl = null,
                            company = "索尼音乐",
                            publishTimeText = "2012-08-10",
                            trackCount = 3,
                            tracks = listOf(
                                AlbumTrackRow(
                                    trackId = "track-1",
                                    title = "疯狂的阳光",
                                    artistText = "张悬",
                                    albumTitle = "神的游戏",
                                    coverUrl = null,
                                    durationMs = 235146L
                                )
                            )
                        )
                    ),
                    Result.failure(IllegalStateException("下一页加载失败"))
                )
            ),
            dynamicResult = Result.success(
                AlbumDynamicInfo(
                    commentCount = 1990,
                    shareCount = 8542,
                    subscribedCount = 66888
                )
            )
        )

        val viewModel = AlbumDetailViewModel(
            albumId = "32311",
            repository = repository,
            pageSize = 1
        )
        advanceUntilIdle()

        viewModel.loadMoreTracks()
        advanceUntilIdle()

        val contentState = viewModel.uiStateFlow.value.contentState as AlbumContentUiState.Content
        assertEquals(1, contentState.content.tracks.size)
        assertEquals("下一页加载失败", contentState.loadMoreErrorMessage)
        assertTrue(!contentState.endReached)
    }
}

private class FakeAlbumDetailRepository(
    private val contentResults: ArrayDeque<Result<AlbumDetailContent>>,
    private val dynamicResult: Result<AlbumDynamicInfo>
) : AlbumDetailRepository {
    val pageRequests = mutableListOf<Pair<Int, Int>>()

    override suspend fun fetchAlbumContent(
        albumId: String,
        offset: Int,
        limit: Int
    ): AlbumDetailContent {
        pageRequests += offset to limit
        return contentResults.removeFirst().getOrThrow()
    }

    override suspend fun fetchAlbumDynamic(albumId: String): AlbumDynamicInfo {
        return dynamicResult.getOrThrow()
    }
}
