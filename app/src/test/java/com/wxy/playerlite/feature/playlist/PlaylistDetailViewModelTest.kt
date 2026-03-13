package com.wxy.playerlite.feature.playlist

import com.wxy.playerlite.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_shouldPublishHeaderAndTracks() = runTest {
        val repository = FakePlaylistDetailRepository(
            headerResult = Result.success(
                PlaylistHeaderContent(
                    playlistId = "3778678",
                    title = "热歌榜",
                    creatorName = "网易云音乐",
                    description = "云音乐热歌榜",
                    coverUrl = "http://example.com/playlist.jpg",
                    trackCount = 200,
                    playCount = 13755319296L,
                    subscribedCount = 12882840L
                )
            ),
            trackResults = ArrayDeque(
                listOf(
                    Result.success(
                        listOf(
                            PlaylistTrackRow(
                                trackId = "1973665667",
                                title = "海屿你",
                                artistText = "马也_Crabbit",
                                albumTitle = "海屿你",
                                coverUrl = "http://example.com/song.jpg",
                                durationMs = 295940L
                            )
                        )
                    )
                )
            )
        )

        val viewModel = PlaylistDetailViewModel(
            playlistId = "3778678",
            repository = repository
        )
        advanceUntilIdle()

        val state = viewModel.uiStateFlow.value
        assertTrue(state.headerState is PlaylistHeaderUiState.Content)
        assertTrue(state.tracksState is PlaylistTracksUiState.Content)
        assertEquals(
            "热歌榜",
            (state.headerState as PlaylistHeaderUiState.Content).content.title
        )
        assertEquals(
            "海屿你",
            (state.tracksState as PlaylistTracksUiState.Content).items.single().title
        )
    }

    @Test
    fun retry_shouldKeepHeaderWhenTracksFail() = runTest {
        val repository = FakePlaylistDetailRepository(
            headerResult = Result.success(
                PlaylistHeaderContent(
                    playlistId = "3778678",
                    title = "热歌榜",
                    creatorName = "网易云音乐",
                    description = "云音乐热歌榜",
                    coverUrl = null,
                    trackCount = 200,
                    playCount = 13755319296L,
                    subscribedCount = 12882840L
                )
            ),
            trackResults = ArrayDeque(
                listOf(
                    Result.failure(IllegalStateException("歌曲列表加载失败"))
                )
            )
        )

        val viewModel = PlaylistDetailViewModel(
            playlistId = "3778678",
            repository = repository
        )
        advanceUntilIdle()

        val state = viewModel.uiStateFlow.value
        assertTrue(state.headerState is PlaylistHeaderUiState.Content)
        assertEquals(
            "歌曲列表加载失败",
            (state.tracksState as PlaylistTracksUiState.Error).message
        )
    }

    @Test
    fun loadMoreTracks_shouldAppendNextPageAndMarkEndReached() = runTest {
        val repository = FakePlaylistDetailRepository(
            headerResult = Result.success(
                PlaylistHeaderContent(
                    playlistId = "3778678",
                    title = "热歌榜",
                    creatorName = "网易云音乐",
                    description = "云音乐热歌榜",
                    coverUrl = null,
                    trackCount = 2,
                    playCount = 13755319296L,
                    subscribedCount = 12882840L
                )
            ),
            trackResults = ArrayDeque(
                listOf(
                    Result.success(
                        listOf(
                            PlaylistTrackRow(
                                trackId = "track-1",
                                title = "海屿你",
                                artistText = "马也_Crabbit",
                                albumTitle = "海屿你",
                                coverUrl = null,
                                durationMs = 295940L
                            )
                        )
                    ),
                    Result.success(
                        listOf(
                            PlaylistTrackRow(
                                trackId = "track-2",
                                title = "有你",
                                artistText = "歌手 B",
                                albumTitle = "单曲",
                                coverUrl = null,
                                durationMs = 200000L
                            )
                        )
                    )
                )
            )
        )

        val viewModel = PlaylistDetailViewModel(
            playlistId = "3778678",
            repository = repository,
            pageSize = 1
        )
        advanceUntilIdle()

        viewModel.loadMoreTracks()
        advanceUntilIdle()

        val tracksState = viewModel.uiStateFlow.value.tracksState as PlaylistTracksUiState.Content
        assertEquals(listOf(0 to 1, 1 to 1), repository.pageRequests)
        assertEquals(2, tracksState.items.size)
        assertTrue(tracksState.endReached)
    }

    @Test
    fun loadMoreTracks_failureShouldPreserveLoadedItemsAndExposeRetryState() = runTest {
        val repository = FakePlaylistDetailRepository(
            headerResult = Result.success(
                PlaylistHeaderContent(
                    playlistId = "3778678",
                    title = "热歌榜",
                    creatorName = "网易云音乐",
                    description = "云音乐热歌榜",
                    coverUrl = null,
                    trackCount = 3,
                    playCount = 13755319296L,
                    subscribedCount = 12882840L
                )
            ),
            trackResults = ArrayDeque(
                listOf(
                    Result.success(
                        listOf(
                            PlaylistTrackRow(
                                trackId = "track-1",
                                title = "海屿你",
                                artistText = "马也_Crabbit",
                                albumTitle = "海屿你",
                                coverUrl = null,
                                durationMs = 295940L
                            )
                        )
                    ),
                    Result.failure(IllegalStateException("下一页加载失败"))
                )
            )
        )

        val viewModel = PlaylistDetailViewModel(
            playlistId = "3778678",
            repository = repository,
            pageSize = 1
        )
        advanceUntilIdle()

        viewModel.loadMoreTracks()
        advanceUntilIdle()

        val tracksState = viewModel.uiStateFlow.value.tracksState as PlaylistTracksUiState.Content
        assertEquals(1, tracksState.items.size)
        assertEquals("下一页加载失败", tracksState.loadMoreErrorMessage)
        assertTrue(!tracksState.endReached)
    }
}

private class FakePlaylistDetailRepository(
    private val headerResult: Result<PlaylistHeaderContent>,
    private val trackResults: ArrayDeque<Result<List<PlaylistTrackRow>>>
) : PlaylistDetailRepository {
    val pageRequests = mutableListOf<Pair<Int, Int>>()

    override suspend fun fetchPlaylistHeader(playlistId: String): PlaylistHeaderContent {
        return headerResult.getOrThrow()
    }

    override suspend fun fetchPlaylistTracks(
        playlistId: String,
        offset: Int,
        limit: Int
    ): List<PlaylistTrackRow> {
        pageRequests += offset to limit
        return trackResults.removeFirst().getOrThrow()
    }
}
