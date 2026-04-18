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
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentSongsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_loggedIn_shouldLoadLocalTabByDefault() = runTest {
        val repository = FakeRecentSongsUserCenterRepository().apply {
            localResults.add(
                Result.success(
                    listOf(
                        RecentLocalPlaybackItemUiModel(
                            recordKey = "online:song-1",
                            sourceType = com.wxy.playerlite.core.playlist.PlaylistItemType.ONLINE,
                            songId = "song-1",
                            playbackUri = "content://local/song-1",
                            title = "Song 1",
                            artistText = "Artist 1",
                            imageUrl = null,
                            albumTitle = "Album 1"
                        )
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
        assertEquals(RecentPlaybackTab.LOCAL, viewModel.uiStateFlow.value.selectedTab)
        val state = viewModel.uiStateFlow.value.contentState as RecentPlaybackContentState.LocalContent
        assertEquals(1, state.items.size)
        assertEquals(listOf(100), repository.localRequestLimits)
        assertTrue(repository.songRequestLimits.isEmpty())
        assertTrue(repository.videoRequestLimits.isEmpty())
    }

    @Test
    fun init_loggedOut_shouldStillLoadLocalTab() = runTest {
        val repository = FakeRecentSongsUserCenterRepository().apply {
            localResults.add(
                Result.success(
                    listOf(
                        RecentLocalPlaybackItemUiModel(
                            recordKey = "online:song-1",
                            sourceType = com.wxy.playerlite.core.playlist.PlaylistItemType.ONLINE,
                            songId = "song-1",
                            playbackUri = "",
                            title = "Song 1",
                            artistText = "Artist 1",
                            imageUrl = null
                        )
                    )
                )
            )
        }

        val viewModel = RecentSongsViewModel(
            application = Application(),
            repository = repository,
            userRepository = FakeRecentSongsUserRepository(
                initialState = LoginState.LoggedOut
            )
        )
        advanceUntilIdle()

        assertEquals(false, viewModel.uiStateFlow.value.isLoggedIn)
        assertEquals(RecentPlaybackTab.LOCAL, viewModel.uiStateFlow.value.selectedTab)
        val state = viewModel.uiStateFlow.value.contentState as RecentPlaybackContentState.LocalContent
        assertEquals(1, state.items.size)
        assertEquals(listOf(100), repository.localRequestLimits)
        assertTrue(repository.songRequestLimits.isEmpty())
    }

    @Test
    fun selectTab_shouldLoadOnDemandAndKeepCachedContent() = runTest {
        val repository = FakeRecentSongsUserCenterRepository().apply {
            localResults.add(
                Result.success(
                    listOf(
                        RecentLocalPlaybackItemUiModel(
                            recordKey = "online:song-1",
                            sourceType = com.wxy.playerlite.core.playlist.PlaylistItemType.ONLINE,
                            songId = "song-1",
                            playbackUri = "content://local/song-1",
                            title = "Song 1",
                            artistText = "Artist 1",
                            imageUrl = null
                        )
                    )
                )
            )
            albumResults.add(
                Result.success(
                    listOf(
                        RecentPlaybackListItemUiModel(
                            id = "album-1",
                            title = "Album 1",
                            subtitle = "Artist 1",
                            imageUrl = null,
                            meta = "12 首",
                            badge = "专辑"
                        )
                    )
                )
            )
        }
        val viewModel = RecentSongsViewModel(
            application = Application(),
            repository = repository,
            userRepository = FakeRecentSongsUserRepository(
                initialState = LoginState.LoggedIn(session())
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(100), repository.localRequestLimits)
        viewModel.selectTab(RecentPlaybackTab.ALBUMS)
        advanceUntilIdle()
        assertEquals(listOf(100), repository.albumRequestLimits)
        assertEquals(
            RecentPlaybackContentState.GenericContent(
                listOf(
                    RecentPlaybackListItemUiModel(
                        id = "album-1",
                        title = "Album 1",
                        subtitle = "Artist 1",
                        imageUrl = null,
                        meta = "12 首",
                        badge = "专辑"
                    )
                )
            ),
            viewModel.uiStateFlow.value.contentState
        )

        viewModel.selectTab(RecentPlaybackTab.LOCAL)
        advanceUntilIdle()
        assertEquals(1, repository.localRequestLimits.size)

        viewModel.selectTab(RecentPlaybackTab.ALBUMS)
        advanceUntilIdle()
        assertEquals(1, repository.albumRequestLimits.size)
    }

    @Test
    fun retry_shouldReloadCurrentTabOnly() = runTest {
        val repository = FakeRecentSongsUserCenterRepository().apply {
            localResults.add(Result.success(emptyList()))
            videoResults.add(Result.success(emptyList()))
            videoResults.add(Result.success(emptyList()))
        }
        val viewModel = RecentSongsViewModel(
            application = Application(),
            repository = repository,
            userRepository = FakeRecentSongsUserRepository(
                initialState = LoginState.LoggedIn(session())
            )
        )
        advanceUntilIdle()

        viewModel.selectTab(RecentPlaybackTab.VIDEOS)
        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(1, repository.localRequestLimits.size)
        assertEquals(2, repository.videoRequestLimits.size)
    }

    @Test
    fun retry_shouldIgnoreStaleResultFromPreviousRequest() = runTest {
        val oldAlbum = RecentPlaybackListItemUiModel(
            id = "album-old",
            title = "旧结果",
            subtitle = "旧歌手",
            imageUrl = null,
            meta = "旧结果",
            badge = "专辑"
        )
        val freshAlbum = RecentPlaybackListItemUiModel(
            id = "album-new",
            title = "新结果",
            subtitle = "新歌手",
            imageUrl = null,
            meta = "新结果",
            badge = "专辑"
        )
        val repository = FakeRecentSongsUserCenterRepository().apply {
            localResults.add(Result.success(emptyList()))
            albumResults.add(Result.success(listOf(oldAlbum)))
            albumResults.add(Result.success(listOf(freshAlbum)))
            albumDelaysMs.add(1_000L)
            albumDelaysMs.add(10L)
        }
        val viewModel = RecentSongsViewModel(
            application = Application(),
            repository = repository,
            userRepository = FakeRecentSongsUserRepository(
                initialState = LoginState.LoggedIn(session())
            )
        )
        advanceUntilIdle()

        viewModel.selectTab(RecentPlaybackTab.ALBUMS)
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()
        viewModel.retry()
        runCurrent()
        advanceTimeBy(20L)
        runCurrent()

        assertEquals(
            RecentPlaybackContentState.GenericContent(listOf(freshAlbum)),
            viewModel.uiStateFlow.value.contentState
        )
        assertEquals(listOf(100, 100), repository.albumRequestLimits)

        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(
            RecentPlaybackContentState.GenericContent(listOf(freshAlbum)),
            viewModel.uiStateFlow.value.contentState
        )
    }

    @Test
    fun sessionInvalid_shouldLogoutAndResetState() = runTest {
        val repository = FakeRecentSongsUserCenterRepository().apply {
            localResults.add(Result.failure(UserSessionInvalidException("需要登录")))
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
        assertEquals(RecentPlaybackTab.LOCAL, viewModel.uiStateFlow.value.selectedTab)
        assertTrue(
            viewModel.uiStateFlow.value.contentState is RecentPlaybackContentState.Loading ||
                viewModel.uiStateFlow.value.contentState is RecentPlaybackContentState.Empty ||
                viewModel.uiStateFlow.value.contentState is RecentPlaybackContentState.LocalContent
        )
    }
}

private class FakeRecentSongsUserCenterRepository : UserCenterRepository {
    val localResults = ArrayDeque<Result<List<RecentLocalPlaybackItemUiModel>>>()
    val songResults = ArrayDeque<Result<List<RecentSongItemUiModel>>>()
    val videoResults = ArrayDeque<Result<List<RecentPlaybackListItemUiModel>>>()
    val voiceResults = ArrayDeque<Result<List<RecentPlaybackListItemUiModel>>>()
    val playlistResults = ArrayDeque<Result<List<RecentPlaybackListItemUiModel>>>()
    val albumResults = ArrayDeque<Result<List<RecentPlaybackListItemUiModel>>>()
    val djResults = ArrayDeque<Result<List<RecentPlaybackListItemUiModel>>>()

    val localRequestLimits = mutableListOf<Int>()
    val songRequestLimits = mutableListOf<Int>()
    val videoRequestLimits = mutableListOf<Int>()
    val voiceRequestLimits = mutableListOf<Int>()
    val playlistRequestLimits = mutableListOf<Int>()
    val albumRequestLimits = mutableListOf<Int>()
    val djRequestLimits = mutableListOf<Int>()
    val albumDelaysMs = ArrayDeque<Long>()

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

    override suspend fun fetchLocalRecentPlaybackItems(limit: Int): List<RecentLocalPlaybackItemUiModel> {
        localRequestLimits += limit
        return localResults.removeFirst().getOrThrow()
    }

    override suspend fun fetchRecentSongItems(limit: Int): List<RecentSongItemUiModel> {
        songRequestLimits += limit
        return songResults.removeFirst().getOrThrow()
    }

    override suspend fun fetchRecentVideos(limit: Int): List<RecentPlaybackListItemUiModel> {
        videoRequestLimits += limit
        return videoResults.removeFirst().getOrThrow()
    }

    override suspend fun fetchRecentVoices(limit: Int): List<RecentPlaybackListItemUiModel> {
        voiceRequestLimits += limit
        return voiceResults.removeFirstOrNull()?.getOrThrow() ?: emptyList()
    }

    override suspend fun fetchRecentPlaylists(limit: Int): List<RecentPlaybackListItemUiModel> {
        playlistRequestLimits += limit
        return playlistResults.removeFirstOrNull()?.getOrThrow() ?: emptyList()
    }

    override suspend fun fetchRecentAlbums(limit: Int): List<RecentPlaybackListItemUiModel> {
        albumRequestLimits += limit
        val result = albumResults.removeFirst().getOrThrow()
        albumDelaysMs.removeFirstOrNull()?.let { delay(it) }
        return result
    }

    override suspend fun fetchRecentDjRadios(limit: Int): List<RecentPlaybackListItemUiModel> {
        djRequestLimits += limit
        return djResults.removeFirstOrNull()?.getOrThrow() ?: emptyList()
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
