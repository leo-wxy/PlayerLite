package com.wxy.playerlite.feature.main

import android.app.Application
import com.wxy.playerlite.test.MainDispatcherRule
import com.wxy.playerlite.user.UserRepository
import com.wxy.playerlite.user.UserSessionInvalidException
import com.wxy.playerlite.user.model.LoginState
import com.wxy.playerlite.user.model.UserInfo
import com.wxy.playerlite.user.model.UserSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentSongsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_loggedIn_shouldLoadRecentSongs() = runTest {
        val repository = FakeRecentSongsUserCenterRepository().apply {
            recentSongResults += Result.success(
                listOf(
                    UserCenterCollectionItemUiModel(
                        id = "song-1",
                        title = "Song 1",
                        subtitle = "Artist 1",
                        imageUrl = null
                    )
                )
            )
        }
        val userRepository = FakeRecentSongsUserRepository(
            initialState = LoginState.LoggedIn(session())
        )

        val viewModel = RecentSongsViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()

        assertEquals(true, viewModel.uiStateFlow.value.isLoggedIn)
        val state = viewModel.uiStateFlow.value.contentState as RecentSongsContentState.Content
        assertEquals(1, state.items.size)
        assertEquals(listOf(100), repository.requestLimits)
    }

    @Test
    fun init_loggedOut_shouldStayIdleWithoutRequest() = runTest {
        val repository = FakeRecentSongsUserCenterRepository()
        val userRepository = FakeRecentSongsUserRepository(
            initialState = LoginState.LoggedOut
        )

        val viewModel = RecentSongsViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()

        assertEquals(false, viewModel.uiStateFlow.value.isLoggedIn)
        assertEquals(RecentSongsContentState.Idle, viewModel.uiStateFlow.value.contentState)
        assertEquals(0, repository.requestCount)
    }

    @Test
    fun sessionInvalid_shouldLogoutAndResetState() = runTest {
        val repository = FakeRecentSongsUserCenterRepository().apply {
            recentSongResults += Result.failure(UserSessionInvalidException("需要登录"))
        }
        val userRepository = FakeRecentSongsUserRepository(
            initialState = LoginState.LoggedIn(session())
        )

        val viewModel = RecentSongsViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()

        assertEquals(1, userRepository.logoutCount)
        assertEquals(LoginState.LoggedOut, userRepository.loginStateFlow.value)
        assertEquals(false, viewModel.uiStateFlow.value.isLoggedIn)
        assertEquals(RecentSongsContentState.Idle, viewModel.uiStateFlow.value.contentState)
    }
}

private class FakeRecentSongsUserCenterRepository : UserCenterRepository {
    val recentSongResults = ArrayDeque<Result<List<UserCenterCollectionItemUiModel>>>()

    var requestCount = 0
    val requestLimits = mutableListOf<Int>()

    override suspend fun fetchFavoriteArtists(): List<UserCenterCollectionItemUiModel> {
        error("Not needed in this test")
    }

    override suspend fun fetchFavoriteColumns(): List<UserCenterCollectionItemUiModel> {
        error("Not needed in this test")
    }

    override suspend fun fetchUserPlaylists(userId: Long): List<UserCenterCollectionItemUiModel> {
        error("Not needed in this test")
    }

    override suspend fun fetchLikedPlaylist(userId: Long): UserCenterCollectionItemUiModel? {
        error("Not needed in this test")
    }

    override suspend fun fetchRecentSongs(limit: Int): List<UserCenterCollectionItemUiModel> {
        requestCount += 1
        requestLimits += limit
        return recentSongResults.removeFirst().getOrThrow()
    }
}

private class FakeRecentSongsUserRepository(
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

private fun session(): UserSession {
    return UserSession(
        cookie = "MUSIC_U=token; __csrf=csrf-token;",
        csrfToken = "csrf-token",
        userInfo = UserInfo(
            userId = 77L,
            accountId = 88L,
            nickname = "Codex",
            avatarUrl = "https://example.com/avatar.jpg",
            vipType = 11,
            level = 9,
            signature = "hello",
            backgroundUrl = "https://example.com/bg.jpg",
            playlistCount = 9,
            followeds = 8,
            follows = 7,
            eventCount = 6,
            listenSongs = 321,
            accountIdentity = "13800138000"
        ),
        lastValidatedAtMs = 123L
    )
}

