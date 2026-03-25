package com.wxy.playerlite.feature.webplaylistimport

import android.app.Application
import com.wxy.playerlite.test.MainDispatcherRule
import com.wxy.playerlite.user.UserRepository
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
class WebPlaylistImportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun submitUrl_whenLoggedOut_shouldEnterLoginRequiredStage() = runTest {
        val viewModel = WebPlaylistImportViewModel(
            application = Application(),
            repository = FakeWebPlaylistImportRepository(),
            userRepository = FakeImportUserRepository(LoginState.LoggedOut)
        )

        viewModel.onUrlChanged("https://music.163.com/#/playlist?id=17729789137")
        viewModel.submitUrl()
        advanceUntilIdle()

        assertEquals(
            "https://music.163.com/#/playlist?id=17729789137",
            viewModel.uiStateFlow.value.inputUrl
        )
        assertTrue(viewModel.uiStateFlow.value.stage is WebPlaylistImportStage.LoginRequired)
    }

    @Test
    fun submitUrl_whenLoggedIn_shouldLoadSnapshotIntoPreview() = runTest {
        val snapshot = ImportedPlaylistSnapshot(
            source = ImportedPlaylistSource.NETEASE,
            playlistId = "17729789137",
            sourceUrl = "https://music.163.com/#/playlist?id=17729789137",
            title = "深夜 R&B",
            creatorName = "Buradarrr",
            description = "夜间歌单",
            coverUrl = "https://example.com/cover.jpg",
            tracks = listOf(
                ImportedPlaylistTrack(
                    sourceTrackId = "1000",
                    title = "Song 0",
                    artistNames = listOf("Artist 0"),
                    albumTitle = "Album 0",
                    durationMs = 180_000L,
                    resolution = ImportedTrackResolution.Direct(
                        song = ResolvedImportedSong(
                            songId = "1000",
                            title = "Song 0",
                            artistText = "Artist 0",
                            albumTitle = "Album 0",
                            durationMs = 180_000L
                        )
                    )
                )
            )
        )
        val viewModel = WebPlaylistImportViewModel(
            application = Application(),
            repository = FakeWebPlaylistImportRepository(snapshot),
            userRepository = FakeImportUserRepository(LoginState.LoggedIn(importSession()))
        )

        viewModel.onUrlChanged(snapshot.sourceUrl)
        viewModel.submitUrl()
        advanceUntilIdle()

        val stage = viewModel.uiStateFlow.value.stage
        assertTrue(stage is WebPlaylistImportStage.Preview)
        assertEquals(snapshot.title, (stage as WebPlaylistImportStage.Preview).snapshot.title)
        assertEquals(1, stage.snapshot.summary.directCount)
    }

    @Test
    fun submitUrl_whenBlank_shouldExposeInputErrorWithoutLeavingInputStage() = runTest {
        val viewModel = WebPlaylistImportViewModel(
            application = Application(),
            repository = FakeWebPlaylistImportRepository(),
            userRepository = FakeImportUserRepository(LoginState.LoggedIn(importSession()))
        )

        viewModel.onUrlChanged("   ")
        viewModel.submitUrl()
        advanceUntilIdle()

        assertEquals("请输入歌单网页链接", viewModel.uiStateFlow.value.inputErrorMessage)
        assertEquals(WebPlaylistImportStage.Input, viewModel.uiStateFlow.value.stage)
    }
}

private class FakeWebPlaylistImportRepository(
    private val snapshot: ImportedPlaylistSnapshot? = null
) : WebPlaylistImportRepository {
    var requestCount: Int = 0
        private set

    override suspend fun fetchPlaylistSnapshot(rawUrl: String): ImportedPlaylistSnapshot {
        requestCount += 1
        return snapshot ?: error("No snapshot configured")
    }
}

private class FakeImportUserRepository(
    initialState: LoginState
) : UserRepository {
    private val state = MutableStateFlow(initialState)

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
        state.value = LoginState.LoggedOut
    }
}

private fun importSession(): UserSession {
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

