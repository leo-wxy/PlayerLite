package com.wxy.playerlite.feature.webplaylistimport

import android.app.Application
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackGateway
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackRequest
import com.wxy.playerlite.test.MainDispatcherRule
import com.wxy.playerlite.user.UserRepository
import com.wxy.playerlite.user.model.LoginState
import com.wxy.playerlite.user.model.UserInfo
import com.wxy.playerlite.user.model.UserSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
            userRepository = FakeImportUserRepository(LoginState.LoggedOut),
            playbackGateway = FakeImportPlaybackGateway()
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
            userRepository = FakeImportUserRepository(LoginState.LoggedIn(importSession())),
            playbackGateway = FakeImportPlaybackGateway()
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
            userRepository = FakeImportUserRepository(LoginState.LoggedIn(importSession())),
            playbackGateway = FakeImportPlaybackGateway()
        )

        viewModel.onUrlChanged("   ")
        viewModel.submitUrl()
        advanceUntilIdle()

        assertEquals("请输入歌单网页链接", viewModel.uiStateFlow.value.inputErrorMessage)
        assertEquals(WebPlaylistImportStage.Input, viewModel.uiStateFlow.value.stage)
    }

    @Test
    fun confirmImport_whenPreviewHasImportableTracks_shouldDispatchGatewayRequestAndEmitOpenPlayer() = runTest {
        val snapshot = importPreviewSnapshot(
            tracks = listOf(
                buildTrack(
                    sourceTrackId = "raw-0",
                    title = "未命中歌曲",
                    artist = "未知歌手",
                    resolution = ImportedTrackResolution.Unmatched
                ),
                buildTrack(
                    sourceTrackId = "raw-1",
                    title = "晴天",
                    artist = "周杰伦",
                    resolution = ImportedTrackResolution.Matched(
                        song = resolvedSong(songId = "song-1", title = "晴天", artist = "周杰伦")
                    )
                ),
                buildTrack(
                    sourceTrackId = "raw-2",
                    title = "七里香",
                    artist = "周杰伦",
                    resolution = ImportedTrackResolution.Direct(
                        song = resolvedSong(songId = "song-2", title = "七里香", artist = "周杰伦")
                    )
                )
            )
        )
        val playbackGateway = FakeImportPlaybackGateway(playResult = true)
        val viewModel = WebPlaylistImportViewModel(
            application = Application(),
            repository = FakeWebPlaylistImportRepository(snapshot),
            userRepository = FakeImportUserRepository(LoginState.LoggedIn(importSession())),
            playbackGateway = playbackGateway
        )

        viewModel.onUrlChanged(snapshot.sourceUrl)
        viewModel.submitUrl()
        advanceUntilIdle()

        val events = mutableListOf<WebPlaylistImportUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.take(1).collect(events::add)
        }
        runCurrent()
        viewModel.confirmImport()
        advanceUntilIdle()

        assertEquals(listOf(WebPlaylistImportUiEvent.OpenPlayer), events)
        assertEquals(1, playbackGateway.requests.size)
        assertEquals(0, playbackGateway.requests.single().activeIndex)
        assertEquals(
            listOf("song-1", "song-2"),
            playbackGateway.requests.single().items.mapNotNull { it.songId }
        )
    }

    @Test
    fun confirmImport_whenPreviewHasNoImportableTracks_shouldNotCallGatewayAndShouldEmitMessage() = runTest {
        val snapshot = importPreviewSnapshot(
            tracks = listOf(
                buildTrack(
                    sourceTrackId = "raw-0",
                    title = "未命中歌曲",
                    artist = "未知歌手",
                    resolution = ImportedTrackResolution.Unmatched
                )
            )
        )
        val playbackGateway = FakeImportPlaybackGateway(playResult = true)
        val viewModel = WebPlaylistImportViewModel(
            application = Application(),
            repository = FakeWebPlaylistImportRepository(snapshot),
            userRepository = FakeImportUserRepository(LoginState.LoggedIn(importSession())),
            playbackGateway = playbackGateway
        )

        viewModel.onUrlChanged(snapshot.sourceUrl)
        viewModel.submitUrl()
        advanceUntilIdle()

        val events = mutableListOf<WebPlaylistImportUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.take(1).collect(events::add)
        }
        runCurrent()
        viewModel.confirmImport()
        advanceUntilIdle()

        assertEquals(
            listOf(WebPlaylistImportUiEvent.ShowMessage("当前没有可导入歌曲")),
            events
        )
        assertEquals(0, playbackGateway.requests.size)
        assertTrue(viewModel.uiStateFlow.value.stage is WebPlaylistImportStage.Preview)
    }

    @Test
    fun confirmImport_whenGatewayFails_shouldStayOnPreviewAndEmitMessage() = runTest {
        val snapshot = importPreviewSnapshot(
            tracks = listOf(
                buildTrack(
                    sourceTrackId = "raw-1",
                    title = "晴天",
                    artist = "周杰伦",
                    resolution = ImportedTrackResolution.Matched(
                        song = resolvedSong(songId = "song-1", title = "晴天", artist = "周杰伦")
                    )
                )
            )
        )
        val playbackGateway = FakeImportPlaybackGateway(playResult = false)
        val viewModel = WebPlaylistImportViewModel(
            application = Application(),
            repository = FakeWebPlaylistImportRepository(snapshot),
            userRepository = FakeImportUserRepository(LoginState.LoggedIn(importSession())),
            playbackGateway = playbackGateway
        )

        viewModel.onUrlChanged(snapshot.sourceUrl)
        viewModel.submitUrl()
        advanceUntilIdle()

        val events = mutableListOf<WebPlaylistImportUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.take(1).collect(events::add)
        }
        runCurrent()
        viewModel.confirmImport()
        advanceUntilIdle()

        assertEquals(
            listOf(WebPlaylistImportUiEvent.ShowMessage("导入失败，请稍后重试")),
            events
        )
        val stage = viewModel.uiStateFlow.value.stage
        assertTrue(stage is WebPlaylistImportStage.Preview)
        assertEquals(false, (stage as WebPlaylistImportStage.Preview).isImporting)
    }

    @Test
    fun submitUrl_shouldUpdatePreviewAsRepositoryEmitsProgressively() = runTest {
        val initialSnapshot = importPreviewSnapshot(
            tracks = listOf(
                buildTrack(
                    sourceTrackId = "raw-1",
                    title = "晴天",
                    artist = "周杰伦",
                    resolution = ImportedTrackResolution.Pending
                )
            ),
            matchingProgress = ImportedPlaylistMatchingProgress(
                completedCount = 0,
                totalCount = 1
            )
        )
        val finalSnapshot = importPreviewSnapshot(
            tracks = listOf(
                buildTrack(
                    sourceTrackId = "raw-1",
                    title = "晴天",
                    artist = "周杰伦",
                    resolution = ImportedTrackResolution.Matched(
                        song = resolvedSong(songId = "song-1", title = "晴天", artist = "周杰伦")
                    )
                )
            ),
            matchingProgress = ImportedPlaylistMatchingProgress.completed(1)
        )
        val viewModel = WebPlaylistImportViewModel(
            application = Application(),
            repository = FakeWebPlaylistImportRepository(
                snapshots = listOf(initialSnapshot, finalSnapshot),
                emitDelayMs = 1_000L
            ),
            userRepository = FakeImportUserRepository(LoginState.LoggedIn(importSession())),
            playbackGateway = FakeImportPlaybackGateway()
        )

        viewModel.onUrlChanged(initialSnapshot.sourceUrl)
        viewModel.submitUrl()
        runCurrent()

        val firstStage = viewModel.uiStateFlow.value.stage
        assertTrue(firstStage is WebPlaylistImportStage.Preview)
        assertEquals(
            ImportedTrackResolution.Pending,
            (firstStage as WebPlaylistImportStage.Preview).snapshot.tracks.single().resolution
        )

        advanceTimeBy(1_000L)
        advanceUntilIdle()

        val finalStage = viewModel.uiStateFlow.value.stage
        assertTrue(finalStage is WebPlaylistImportStage.Preview)
        assertTrue(
            (finalStage as WebPlaylistImportStage.Preview).snapshot.tracks.single().resolution is
                ImportedTrackResolution.Matched
        )
        assertEquals("1 / 1", finalStage.snapshot.matchingProgress.progressText)
    }
}

private class FakeWebPlaylistImportRepository(
    private val snapshot: ImportedPlaylistSnapshot? = null,
    private val snapshots: List<ImportedPlaylistSnapshot> = snapshot?.let(::listOf).orEmpty(),
    private val emitDelayMs: Long = 0L
) : WebPlaylistImportRepository {
    var requestCount: Int = 0
        private set

    override fun streamPlaylistSnapshots(rawUrl: String): Flow<ImportedPlaylistSnapshot> = flow {
        requestCount += 1
        val emissions = snapshots.ifEmpty {
            listOfNotNull(snapshot)
        }
        require(emissions.isNotEmpty()) { "No snapshot configured" }
        emissions.forEachIndexed { index, next ->
            if (index > 0 && emitDelayMs > 0L) {
                delay(emitDelayMs)
            }
            emit(next)
        }
    }
}

private class FakeImportPlaybackGateway(
    private val playResult: Boolean = true
) : DetailPlaybackGateway {
    val requests = mutableListOf<DetailPlaybackRequest>()

    override fun play(request: DetailPlaybackRequest): Boolean {
        requests += request
        return playResult
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

private fun importPreviewSnapshot(
    tracks: List<ImportedPlaylistTrack>,
    matchingProgress: ImportedPlaylistMatchingProgress =
        ImportedPlaylistMatchingProgress.completed(tracks.size)
): ImportedPlaylistSnapshot {
    return ImportedPlaylistSnapshot(
        source = ImportedPlaylistSource.QQ_MUSIC,
        playlistId = "4204621746",
        sourceUrl = "https://y.qq.com/n/ryqq_v2/playlist/4204621746",
        title = "测试歌单",
        creatorName = "Codex",
        description = "desc",
        coverUrl = "https://example.com/cover.jpg",
        tracks = tracks,
        matchingProgress = matchingProgress
    )
}

private fun buildTrack(
    sourceTrackId: String,
    title: String,
    artist: String,
    resolution: ImportedTrackResolution
): ImportedPlaylistTrack {
    return ImportedPlaylistTrack(
        sourceTrackId = sourceTrackId,
        title = title,
        artistNames = listOf(artist),
        albumTitle = "测试专辑",
        durationMs = 180_000L,
        resolution = resolution
    )
}

private fun resolvedSong(
    songId: String,
    title: String,
    artist: String
): ResolvedImportedSong {
    return ResolvedImportedSong(
        songId = songId,
        title = title,
        artistText = artist,
        albumTitle = "测试专辑",
        durationMs = 180_000L
    )
}
