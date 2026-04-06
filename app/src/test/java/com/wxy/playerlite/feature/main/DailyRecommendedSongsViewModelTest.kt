package com.wxy.playerlite.feature.main

import android.app.Application
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackGateway
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackRequest
import com.wxy.playerlite.test.MainDispatcherRule
import com.wxy.playerlite.user.UserRepository
import com.wxy.playerlite.user.UserSessionInvalidException
import com.wxy.playerlite.user.model.LoginState
import com.wxy.playerlite.user.model.UserInfo
import com.wxy.playerlite.user.model.UserSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyRecommendedSongsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_loggedOut_shouldStayIdleWithoutRequest() = runTest {
        val repository = FakeDailyRecommendedSongsRepository()
        val userRepository = FakeDailyRecommendedSongsUserRepository(
            initialState = LoginState.LoggedOut
        )

        val viewModel = DailyRecommendedSongsViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()

        assertEquals(false, viewModel.uiStateFlow.value.isLoggedIn)
        assertEquals(DailyRecommendedSongsContentState.Idle, viewModel.uiStateFlow.value.contentState)
        assertEquals(0, repository.requestCount)
    }

    @Test
    fun init_loggedIn_shouldLoadDailyRecommendedSongs() = runTest {
        val repository = FakeDailyRecommendedSongsRepository().apply {
            results.addLast(
                Result.success(
                listOf(
                    DailyRecommendedSongUiModel(
                        id = "song-1",
                        songId = "song-1",
                        title = "Song 1",
                        artistText = "Artist 1",
                        albumTitle = "Album 1",
                        coverUrl = null,
                        durationMs = 1000L,
                        recommendReason = "超80%人播放"
                    )
                )
            )
            )
        }
        val userRepository = FakeDailyRecommendedSongsUserRepository(
            initialState = LoginState.LoggedIn(session())
        )

        val viewModel = DailyRecommendedSongsViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()

        assertEquals(true, viewModel.uiStateFlow.value.isLoggedIn)
        val content = viewModel.uiStateFlow.value.contentState as DailyRecommendedSongsContentState.Content
        assertEquals(1, content.items.size)
        assertEquals("超80%人播放", content.items.single().recommendReason)
        assertEquals(1, repository.requestCount)
    }

    @Test
    fun retry_shouldReloadAfterFailure() = runTest {
        val repository = FakeDailyRecommendedSongsRepository().apply {
            results.addLast(Result.failure(IllegalStateException("boom")))
            results.addLast(
                Result.success(
                listOf(
                    DailyRecommendedSongUiModel(
                        id = "song-2",
                        songId = "song-2",
                        title = "Song 2",
                        artistText = "Artist 2",
                        albumTitle = "Album 2",
                        coverUrl = null,
                        durationMs = 2000L,
                        recommendReason = null
                    )
                )
            )
            )
        }
        val userRepository = FakeDailyRecommendedSongsUserRepository(
            initialState = LoginState.LoggedIn(session())
        )

        val viewModel = DailyRecommendedSongsViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()
        assertEquals(
            "boom",
            (viewModel.uiStateFlow.value.contentState as DailyRecommendedSongsContentState.Error).message
        )

        viewModel.retry()
        advanceUntilIdle()

        val content = viewModel.uiStateFlow.value.contentState as DailyRecommendedSongsContentState.Content
        assertEquals("song-2", content.items.single().id)
        assertEquals(2, repository.requestCount)
    }

    @Test
    fun sessionInvalid_shouldLogoutAndResetState() = runTest {
        val repository = FakeDailyRecommendedSongsRepository().apply {
            results.addLast(Result.failure(UserSessionInvalidException("需要登录")))
        }
        val userRepository = FakeDailyRecommendedSongsUserRepository(
            initialState = LoginState.LoggedIn(session())
        )

        val viewModel = DailyRecommendedSongsViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()

        assertEquals(1, userRepository.logoutCount)
        assertEquals(false, viewModel.uiStateFlow.value.isLoggedIn)
        assertEquals(DailyRecommendedSongsContentState.Idle, viewModel.uiStateFlow.value.contentState)
    }

    @Test
    fun playAt_success_shouldDispatchGatewayRequestAndEmitOpenPlayer() = runTest {
        val playbackGateway = FakeDailyRecommendedSongsPlaybackGateway(playResult = true)
        val repository = FakeDailyRecommendedSongsRepository().apply {
            results.addLast(
                Result.success(
                    listOf(
                        DailyRecommendedSongUiModel(
                            id = "song-1",
                            songId = "song-1",
                            title = "Song 1",
                            artistText = "Artist 1",
                            albumTitle = "Album 1",
                            coverUrl = null,
                            durationMs = 1000L,
                            recommendReason = "超80%人播放"
                        ),
                        DailyRecommendedSongUiModel(
                            id = "song-2",
                            songId = "song-2",
                            title = "Song 2",
                            artistText = "Artist 2",
                            albumTitle = "Album 2",
                            coverUrl = null,
                            durationMs = 2000L,
                            recommendReason = null
                        )
                    )
                )
            )
        }
        val userRepository = FakeDailyRecommendedSongsUserRepository(
            initialState = LoginState.LoggedIn(session())
        )
        val viewModel = DailyRecommendedSongsViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository,
            playbackGateway = playbackGateway
        )
        advanceUntilIdle()

        val events = mutableListOf<DailyRecommendedSongsUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.take(1).collect(events::add)
        }
        runCurrent()
        viewModel.playAt(index = 1)
        advanceUntilIdle()

        assertEquals(listOf(DailyRecommendedSongsUiEvent.OpenPlayer), events)
        assertEquals(1, playbackGateway.requests.size)
        assertEquals(1, playbackGateway.requests.single().activeIndex)
        assertEquals(2, playbackGateway.requests.single().items.size)
        assertEquals("song-2", playbackGateway.requests.single().items[1].songId)
    }

    @Test
    fun playAll_failure_shouldEmitMessageAndKeepContentState() = runTest {
        val playbackGateway = FakeDailyRecommendedSongsPlaybackGateway(playResult = false)
        val repository = FakeDailyRecommendedSongsRepository().apply {
            results.addLast(
                Result.success(
                    listOf(
                        DailyRecommendedSongUiModel(
                            id = "song-1",
                            songId = "song-1",
                            title = "Song 1",
                            artistText = "Artist 1",
                            albumTitle = "Album 1",
                            coverUrl = null,
                            durationMs = 1000L,
                            recommendReason = "超80%人播放"
                        )
                    )
                )
            )
        }
        val userRepository = FakeDailyRecommendedSongsUserRepository(
            initialState = LoginState.LoggedIn(session())
        )
        val viewModel = DailyRecommendedSongsViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository,
            playbackGateway = playbackGateway
        )
        advanceUntilIdle()

        val events = mutableListOf<DailyRecommendedSongsUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.take(1).collect(events::add)
        }
        runCurrent()
        viewModel.playAll()
        advanceUntilIdle()

        assertEquals(
            listOf(DailyRecommendedSongsUiEvent.ShowMessage("播放启动失败，请稍后重试")),
            events
        )
        assertEquals(1, playbackGateway.requests.size)
        val content = viewModel.uiStateFlow.value.contentState as DailyRecommendedSongsContentState.Content
        assertEquals("song-1", content.items.single().songId)
    }

    @Test
    fun retry_whileLoading_shouldNotStartSecondRequest() = runTest {
        val repository = BlockingDailyRecommendedSongsRepository()
        val userRepository = FakeDailyRecommendedSongsUserRepository(
            initialState = LoginState.LoggedIn(session())
        )
        val viewModel = DailyRecommendedSongsViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )

        runCurrent()
        assertEquals(1, repository.requestCount)
        assertEquals(
            DailyRecommendedSongsContentState.Loading,
            viewModel.uiStateFlow.value.contentState
        )

        viewModel.retry()
        runCurrent()

        assertEquals(1, repository.requestCount)

        repository.completion.complete(
            listOf(
                DailyRecommendedSongUiModel(
                    id = "song-1",
                    songId = "song-1",
                    title = "Song 1",
                    artistText = "Artist 1",
                    albumTitle = "Album 1",
                    coverUrl = null,
                    durationMs = 1000L,
                    recommendReason = null
                )
            )
        )
        advanceUntilIdle()

        val content = viewModel.uiStateFlow.value.contentState as DailyRecommendedSongsContentState.Content
        assertEquals(1, content.items.size)
    }
}

private class FakeDailyRecommendedSongsRepository : DailyRecommendedSongsRepository {
    val results = ArrayDeque<Result<List<DailyRecommendedSongUiModel>>>()
    var requestCount = 0

    override suspend fun fetchDailyRecommendedSongs(): List<DailyRecommendedSongUiModel> {
        requestCount += 1
        return results.removeFirst().getOrThrow()
    }
}

private class FakeDailyRecommendedSongsUserRepository(
    initialState: LoginState
) : UserRepository {
    private val state = MutableStateFlow(initialState)
    var logoutCount = 0

    override val loginStateFlow: StateFlow<LoginState> = state

    override fun currentSession(): UserSession? {
        return (state.value as? LoginState.LoggedIn)?.session
    }

    override suspend fun restorePersistedSession() = Unit

    override suspend fun loginWithPhone(
        phone: String,
        password: String,
        countryCode: String
    ): UserSession {
        error("Not needed in this test")
    }

    override suspend fun loginWithEmail(
        email: String,
        password: String
    ): UserSession {
        error("Not needed in this test")
    }

    override suspend fun refreshUserInfo(): UserSession? = currentSession()

    override suspend fun logout() {
        logoutCount += 1
        state.value = LoginState.LoggedOut
    }
}

private class FakeDailyRecommendedSongsPlaybackGateway(
    private val playResult: Boolean = true
) : DetailPlaybackGateway {
    val requests = mutableListOf<DetailPlaybackRequest>()

    override fun play(request: DetailPlaybackRequest): Boolean {
        requests += request
        return playResult
    }
}

private class BlockingDailyRecommendedSongsRepository : DailyRecommendedSongsRepository {
    val completion = CompletableDeferred<List<DailyRecommendedSongUiModel>>()
    var requestCount = 0

    override suspend fun fetchDailyRecommendedSongs(): List<DailyRecommendedSongUiModel> {
        requestCount += 1
        return completion.await()
    }
}

private fun session(): UserSession {
    return UserSession(
        cookie = "MUSIC_U=token; __csrf=csrf-token;",
        csrfToken = "csrf-token",
        userInfo = UserInfo(
            userId = 1L,
            accountId = 2L,
            nickname = "Codex",
            avatarUrl = "",
            vipType = 0,
            level = 1,
            signature = null,
            backgroundUrl = null,
            playlistCount = 0,
            followeds = 0,
            follows = 0,
            eventCount = 0,
            listenSongs = 0,
            accountIdentity = "13800138000"
        ),
        lastValidatedAtMs = 123L
    )
}
