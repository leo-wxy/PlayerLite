package com.wxy.playerlite.feature.local

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackGateway
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackRequest
import com.wxy.playerlite.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LocalSongsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun permissionGranted_withCachedSongs_shouldShowCacheWithoutAutoScan() = runTest {
        val repository = FakeLocalSongsRepository(
            cachedSongs = listOf(
                LocalSongEntry(
                    id = "local-1",
                    contentUri = "content://media/external/audio/media/1",
                    title = "晴天",
                    artist = "周杰伦",
                    album = "叶惠美",
                    durationMs = 269000L
                )
            )
        )
        val viewModel = LocalSongsViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            playbackGateway = FakeDetailPlaybackGateway()
        )

        viewModel.onPermissionStateChanged(granted = true)
        advanceUntilIdle()

        val state = viewModel.uiStateFlow.value
        assertEquals(1, state.songs.size)
        assertEquals("晴天", state.songs.single().title)
        assertTrue(state.hasCachedSongs)
        assertEquals(0, repository.scanRequests)
    }

    @Test
    fun scanRequested_shouldRefreshSongsAndDispatchPlayAllThroughGateway() = runTest {
        val repository = FakeLocalSongsRepository(
            scanResult = Result.success(
                listOf(
                    LocalSongEntry(
                        id = "local-1",
                        contentUri = "content://media/external/audio/media/1",
                        title = "晴天",
                        artist = "周杰伦",
                        album = "叶惠美",
                        durationMs = 269000L
                    ),
                    LocalSongEntry(
                        id = "local-2",
                        contentUri = "content://media/external/audio/media/2",
                        title = "七里香",
                        artist = "周杰伦",
                        album = "七里香",
                        durationMs = 301000L
                    )
                )
            )
        )
        val playbackGateway = FakeDetailPlaybackGateway()
        val viewModel = LocalSongsViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            playbackGateway = playbackGateway
        )

        viewModel.onPermissionStateChanged(granted = true)
        advanceUntilIdle()
        viewModel.onScanRequested()
        advanceUntilIdle()
        viewModel.playAll()

        val state = viewModel.uiStateFlow.value
        assertEquals(2, state.songs.size)
        assertEquals(2, repository.scanRequests)
        assertEquals(1, playbackGateway.requests.size)
        assertEquals(0, playbackGateway.requests.single().activeIndex)
        assertEquals(2, playbackGateway.requests.single().items.size)
    }

    @Test
    fun playSong_success_shouldDispatchGatewayRequestWithTargetIndexAndEmitOpenPlayer() = runTest {
        val repository = FakeLocalSongsRepository(
            cachedSongs = listOf(
                LocalSongEntry(
                    id = "local-1",
                    contentUri = "content://media/external/audio/media/1",
                    title = "晴天",
                    artist = "周杰伦",
                    album = "叶惠美",
                    durationMs = 269000L
                )
            )
        )
        val playbackGateway = FakeDetailPlaybackGateway(playResult = true)
        val viewModel = LocalSongsViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            playbackGateway = playbackGateway
        )

        viewModel.onPermissionStateChanged(granted = true)
        advanceUntilIdle()
        val events = mutableListOf<LocalSongsUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.take(1).collect(events::add)
        }
        runCurrent()
        viewModel.playSong(index = 0)
        advanceUntilIdle()

        assertEquals(listOf(LocalSongsUiEvent.OpenPlayer), events)
        assertEquals(1, playbackGateway.requests.size)
        assertEquals(0, playbackGateway.requests.single().activeIndex)
    }

    @Test
    fun playSong_failure_shouldKeepListStateRecordAttemptAndEmitMessage() = runTest {
        val repository = FakeLocalSongsRepository(
            cachedSongs = listOf(
                LocalSongEntry(
                    id = "local-1",
                    contentUri = "content://media/external/audio/media/1",
                    title = "晴天",
                    artist = "周杰伦",
                    album = "叶惠美",
                    durationMs = 269000L
                )
            )
        )
        val playbackGateway = FakeDetailPlaybackGateway(playResult = false)
        val viewModel = LocalSongsViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository,
            playbackGateway = playbackGateway
        )

        viewModel.onPermissionStateChanged(granted = true)
        advanceUntilIdle()
        val events = mutableListOf<LocalSongsUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.take(1).collect(events::add)
        }
        runCurrent()
        viewModel.playSong(index = 0)
        advanceUntilIdle()

        assertEquals(
            listOf(LocalSongsUiEvent.ShowMessage("播放启动失败，请稍后重试")),
            events
        )
        assertEquals("晴天", viewModel.uiStateFlow.value.songs.single().title)
        assertEquals(1, playbackGateway.requests.size)
    }

    private class FakeLocalSongsRepository(
        private val cachedSongs: List<LocalSongEntry> = emptyList(),
        private val scanResult: Result<List<LocalSongEntry>> = Result.success(emptyList())
    ) : LocalSongsRepository {
        var scanRequests: Int = 0

        override suspend fun readCachedSongs(): List<LocalSongEntry> {
            return cachedSongs
        }

        override suspend fun scanSongs(): Result<List<LocalSongEntry>> {
            scanRequests += 1
            return scanResult
        }
    }

    private class FakeDetailPlaybackGateway(
        private val playResult: Boolean = true
    ) : DetailPlaybackGateway {
        val requests = mutableListOf<DetailPlaybackRequest>()

        override fun play(request: DetailPlaybackRequest): Boolean {
            requests += request
            return playResult
        }
    }
}
