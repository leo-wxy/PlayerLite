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
class LikedContentViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_loggedOut_shouldStayIdleWithoutRequest() = runTest {
        val repository = FakeLikedContentUserCenterRepository()
        val userRepository = FakeLikedContentUserRepository(
            initialState = LoginState.LoggedOut
        )

        val viewModel = LikedContentViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()

        assertEquals(false, viewModel.uiStateFlow.value.isLoggedIn)
        assertEquals(LikedTabContentState.Idle, viewModel.uiStateFlow.value.currentState)
        assertEquals(0, repository.playlistRequestCount)
        assertEquals(0, repository.artistRequestCount)
        assertEquals(0, repository.mvRequestCount)
        assertEquals(0, repository.columnRequestCount)
    }

    @Test
    fun init_loggedIn_andSwitchTabs_shouldLoadCurrentTabOnly() = runTest {
        val repository = FakeLikedContentUserCenterRepository().apply {
            playlistResults += Result.success(listOf(item("playlist-1", "Playlist 1")))
            artistResults += Result.success(listOf(item("artist-1", "Artist 1")))
            mvResults += Result.success(listOf(item("mv-1", "MV 1")))
            columnResults += Result.success(listOf(item("column-1", "Column 1")))
        }
        val userRepository = FakeLikedContentUserRepository(
            initialState = LoginState.LoggedIn(likedSession())
        )

        val viewModel = LikedContentViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()

        assertEquals(true, viewModel.uiStateFlow.value.isLoggedIn)
        assertEquals(1, repository.playlistRequestCount)
        assertEquals(0, repository.artistRequestCount)
        assertEquals(0, repository.mvRequestCount)
        assertEquals(0, repository.columnRequestCount)

        viewModel.selectTab(LikedContentTab.ARTISTS)
        advanceUntilIdle()
        assertEquals(1, repository.artistRequestCount)
        assertEquals(LikedContentTab.ARTISTS, viewModel.uiStateFlow.value.selectedTab)

        viewModel.selectTab(LikedContentTab.MVS)
        advanceUntilIdle()
        assertEquals(1, repository.mvRequestCount)
        assertEquals(LikedContentTab.MVS, viewModel.uiStateFlow.value.selectedTab)

        viewModel.selectTab(LikedContentTab.COLUMNS)
        advanceUntilIdle()
        assertEquals(1, repository.columnRequestCount)
        assertEquals(LikedContentTab.COLUMNS, viewModel.uiStateFlow.value.selectedTab)
    }

    @Test
    fun retry_shouldReloadFailedCurrentTab() = runTest {
        val repository = FakeLikedContentUserCenterRepository().apply {
            playlistResults += Result.failure(IllegalStateException("收藏歌单加载失败"))
            playlistResults += Result.success(listOf(item("playlist-1", "Playlist 1")))
        }
        val userRepository = FakeLikedContentUserRepository(
            initialState = LoginState.LoggedIn(likedSession())
        )

        val viewModel = LikedContentViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()

        assertEquals(
            "收藏歌单加载失败",
            (viewModel.uiStateFlow.value.currentState as LikedTabContentState.Error).message
        )

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, repository.playlistRequestCount)
        val state = viewModel.uiStateFlow.value.currentState as LikedTabContentState.Content
        assertEquals(1, state.items.size)
    }

    @Test
    fun sessionInvalid_shouldLogoutAndResetState() = runTest {
        val repository = FakeLikedContentUserCenterRepository().apply {
            playlistResults += Result.failure(UserSessionInvalidException("需要登录"))
        }
        val userRepository = FakeLikedContentUserRepository(
            initialState = LoginState.LoggedIn(likedSession())
        )

        val viewModel = LikedContentViewModel(
            application = Application(),
            repository = repository,
            userRepository = userRepository
        )
        advanceUntilIdle()

        assertEquals(1, userRepository.logoutCount)
        assertEquals(LoginState.LoggedOut, userRepository.loginStateFlow.value)
        assertEquals(false, viewModel.uiStateFlow.value.isLoggedIn)
        assertEquals(LikedTabContentState.Idle, viewModel.uiStateFlow.value.currentState)
    }
}

private class FakeLikedContentUserCenterRepository : UserCenterRepository {
    val playlistResults = ArrayDeque<Result<List<UserCenterCollectionItemUiModel>>>()
    val artistResults = ArrayDeque<Result<List<UserCenterCollectionItemUiModel>>>()
    val mvResults = ArrayDeque<Result<List<UserCenterCollectionItemUiModel>>>()
    val columnResults = ArrayDeque<Result<List<UserCenterCollectionItemUiModel>>>()

    var playlistRequestCount = 0
    var artistRequestCount = 0
    var mvRequestCount = 0
    var columnRequestCount = 0

    override suspend fun fetchCollectedPlaylists(userId: Long): List<UserCenterCollectionItemUiModel> {
        playlistRequestCount += 1
        return playlistResults.removeFirst().getOrThrow()
    }

    override suspend fun fetchFavoriteArtists(): List<UserCenterCollectionItemUiModel> {
        artistRequestCount += 1
        return artistResults.removeFirst().getOrThrow()
    }

    override suspend fun fetchFavoriteColumns(): List<UserCenterCollectionItemUiModel> {
        columnRequestCount += 1
        return columnResults.removeFirst().getOrThrow()
    }

    override suspend fun fetchFavoriteMvs(): List<UserCenterCollectionItemUiModel> {
        mvRequestCount += 1
        return mvResults.removeFirst().getOrThrow()
    }

    override suspend fun fetchUserPlaylists(userId: Long): List<UserCenterCollectionItemUiModel> {
        error("Not needed in this test")
    }

    override suspend fun fetchLikedPlaylist(userId: Long): UserCenterCollectionItemUiModel? {
        error("Not needed in this test")
    }

    override suspend fun fetchRecentSongs(limit: Int): List<UserCenterCollectionItemUiModel> {
        error("Not needed in this test")
    }
}

private class FakeLikedContentUserRepository(
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

private fun likedSession(): UserSession {
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
