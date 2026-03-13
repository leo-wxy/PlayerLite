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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserCenterViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun tabEntries_shouldExposePlaylistsFirst() {
        assertEquals(
            listOf(UserCenterTab.PLAYLISTS, UserCenterTab.ARTISTS, UserCenterTab.COLUMNS),
            UserCenterTab.entries.toList()
        )
    }

    @Test
    fun init_loggedIn_shouldLoadDefaultPlaylistsTab() = runTest {
        val repository = FakeUserCenterRepository().apply {
            playlistResults += Result.success(
                listOf(
                    UserCenterCollectionItemUiModel(
                        id = "playlist-1",
                        title = "我喜欢的音乐",
                        subtitle = "Codex",
                        imageUrl = "http://example.com/playlist.jpg"
                    )
                )
            )
        }
        val userRepository = FakeUserRepository(
            initialState = LoginState.LoggedIn(session())
        )

        val viewModel = UserCenterViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()

        assertEquals(UserCenterTab.PLAYLISTS, viewModel.uiStateFlow.value.selectedTab)
        assertTrue(viewModel.uiStateFlow.value.playlistsState is UserCenterTabContentState.Content)
        assertEquals(1, repository.playlistRequestCount)
        assertEquals(listOf(77L), repository.playlistRequestUserIds)
    }

    @Test
    fun onTabSelected_shouldLoadTargetTabOnceAndReuseCachedContent() = runTest {
        val repository = FakeUserCenterRepository().apply {
            artistResults += Result.success(listOf(item("artist-1", "yama")))
            playlistResults += Result.success(listOf(item("playlist-1", "喜欢的音乐")))
        }
        val userRepository = FakeUserRepository(
            initialState = LoginState.LoggedIn(session())
        )
        val viewModel = UserCenterViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()

        viewModel.onTabSelected(UserCenterTab.PLAYLISTS)
        advanceUntilIdle()
        viewModel.onTabSelected(UserCenterTab.ARTISTS)
        advanceUntilIdle()
        viewModel.onTabSelected(UserCenterTab.PLAYLISTS)
        advanceUntilIdle()

        assertEquals(1, repository.artistRequestCount)
        assertEquals(1, repository.playlistRequestCount)
        assertEquals(listOf(77L), repository.playlistRequestUserIds)
        assertTrue(viewModel.uiStateFlow.value.playlistsState is UserCenterTabContentState.Content)
    }

    @Test
    fun sessionInvalid_shouldLogoutAndResetTabStates() = runTest {
        val repository = FakeUserCenterRepository().apply {
            playlistResults += Result.failure(UserSessionInvalidException("需要登录"))
        }
        val userRepository = FakeUserRepository(
            initialState = LoginState.LoggedIn(session())
        )

        val viewModel = UserCenterViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()

        assertEquals(1, userRepository.logoutCount)
        assertEquals(LoginState.LoggedOut, userRepository.loginStateFlow.value)
        assertTrue(viewModel.uiStateFlow.value.playlistsState is UserCenterTabContentState.Idle)
        assertTrue(viewModel.uiStateFlow.value.artistsState is UserCenterTabContentState.Idle)
    }

    @Test
    fun ordinaryFailure_shouldStayOnCurrentTabAndExposeLocalError() = runTest {
        val repository = FakeUserCenterRepository().apply {
            playlistResults += Result.failure(IllegalStateException("用户歌单加载失败"))
        }
        val userRepository = FakeUserRepository(
            initialState = LoginState.LoggedIn(session())
        )

        val viewModel = UserCenterViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()

        assertEquals(0, userRepository.logoutCount)
        assertEquals(
            "用户歌单加载失败",
            (viewModel.uiStateFlow.value.playlistsState as UserCenterTabContentState.Error).message
        )
    }
}

private class FakeUserCenterRepository : UserCenterRepository {
    val artistResults = ArrayDeque<Result<List<UserCenterCollectionItemUiModel>>>()
    val columnResults = ArrayDeque<Result<List<UserCenterCollectionItemUiModel>>>()
    val playlistResults = ArrayDeque<Result<List<UserCenterCollectionItemUiModel>>>()

    var artistRequestCount = 0
    var columnRequestCount = 0
    var playlistRequestCount = 0
    val playlistRequestUserIds = mutableListOf<Long>()

    override suspend fun fetchFavoriteArtists(): List<UserCenterCollectionItemUiModel> {
        artistRequestCount += 1
        return artistResults.removeFirst().getOrThrow()
    }

    override suspend fun fetchFavoriteColumns(): List<UserCenterCollectionItemUiModel> {
        columnRequestCount += 1
        return columnResults.removeFirst().getOrThrow()
    }

    override suspend fun fetchUserPlaylists(userId: Long): List<UserCenterCollectionItemUiModel> {
        playlistRequestCount += 1
        playlistRequestUserIds += userId
        return playlistResults.removeFirst().getOrThrow()
    }
}

private class FakeUserRepository(
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

private fun item(id: String, title: String): UserCenterCollectionItemUiModel {
    return UserCenterCollectionItemUiModel(
        id = id,
        title = title,
        subtitle = "subtitle",
        imageUrl = null
    )
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
