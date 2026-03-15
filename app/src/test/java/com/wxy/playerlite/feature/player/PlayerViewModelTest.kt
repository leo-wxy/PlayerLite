package com.wxy.playerlite.feature.player

import android.app.Application
import android.content.Context
import androidx.media3.common.C
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.feature.main.MainDispatcherRule
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.feature.player.model.PlayerLyricUiState
import com.wxy.playerlite.feature.player.runtime.PlayerRuntime
import com.wxy.playerlite.feature.player.runtime.toQueuePlayableItem
import com.wxy.playerlite.playback.client.RemotePlaybackSnapshot
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlayableItem
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.user.UserRepository
import com.wxy.playerlite.user.model.LoginState
import com.wxy.playerlite.user.model.UserSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application: Application = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        application.getSharedPreferences("playlist_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun skipToNextTrack_shouldSyncRestoredQueueBeforeSendingFirstSkipWhenRemoteQueueIsMissing() = runTest {
        val runtime = PlayerRuntime(application)
        runtime.applyExternalQueueSelection(
            items = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首"),
                onlineItem(index = 1, songId = "track-2", title = "第二首")
            ),
            activeIndex = 0
        )
        val bridge = FakePlayerControlBridge(currentSnapshot = null)
        val viewModel = PlayerViewModel(
            application = application,
            runtime = runtime,
            userRepository = FakeUserRepository(),
            songWikiRepository = FakeSongWikiRepository(),
            serviceBridge = bridge,
            initializeSessionRestore = false,
            remoteSyncIntervalMs = 60_000L,
            uiProgressIntervalMs = 60_000L
        )
        try {
            runCurrent()
            bridge.clearActions()

            viewModel.skipToNextTrack()

            assertEquals(
                listOf(
                    "ensurePlaybackServiceStartedForPlayback",
                    "connectIfNeeded",
                    "syncQueue(size=2,active=0,play=false,start=${C.TIME_UNSET})",
                    "ensurePlaybackServiceStartedForPlayback",
                    "connectIfNeeded",
                    "seekToNextMediaItem"
                ),
                bridge.actions
            )
        } finally {
            clearViewModel(viewModel)
            runCurrent()
        }
    }

    @Test
    fun skipToNextTrack_shouldNotResyncQueueWhenRemoteCurrentMediaAlreadyExists() = runTest {
        val runtime = PlayerRuntime(application)
        runtime.applyExternalQueueSelection(
            items = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首"),
                onlineItem(index = 1, songId = "track-2", title = "第二首")
            ),
            activeIndex = 0
        )
        val bridge = FakePlayerControlBridge(
            currentSnapshot = RemotePlaybackSnapshot(
                playbackState = 3,
                playWhenReady = true,
                isPlaying = true,
                isSeekSupported = true,
                currentPositionMs = 5_000L,
                durationMs = 200_000L,
                playbackSpeed = 1.0f,
                playbackMode = PlaybackMode.LIST_LOOP,
                statusText = "Playing",
                currentPlayable = PlayableItemSnapshot(
                    id = "playlist:test:0:track-1",
                    songId = "track-1",
                    title = "第一首",
                    artistText = "测试歌手",
                    albumTitle = "测试专辑",
                    coverUrl = "https://example.com/track-1.jpg",
                    durationMs = 200_000L,
                    playbackUri = "https://example.com/track-1.mp3"
                ),
                currentMediaId = "playlist:test:0:track-1",
                playbackOutputInfo = null,
                audioMeta = null
            )
        )
        val viewModel = PlayerViewModel(
            application = application,
            runtime = runtime,
            userRepository = FakeUserRepository(),
            songWikiRepository = FakeSongWikiRepository(),
            serviceBridge = bridge,
            initializeSessionRestore = false,
            remoteSyncIntervalMs = 60_000L,
            uiProgressIntervalMs = 60_000L
        )
        try {
            runCurrent()
            bridge.clearActions()

            viewModel.skipToNextTrack()

            assertEquals(
                listOf(
                    "ensurePlaybackServiceStartedForPlayback",
                    "connectIfNeeded",
                    "seekToNextMediaItem"
                ),
                bridge.actions
            )
        } finally {
            clearViewModel(viewModel)
            runCurrent()
        }
    }

    @Test
    fun skipToNextTrack_shouldKeepAutoplayWhenUiIsBufferingAndRemoteQueueIsMissing() = runTest {
        val runtime = PlayerRuntime(application)
        runtime.applyExternalQueueSelection(
            items = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首"),
                onlineItem(index = 1, songId = "track-2", title = "第二首")
            ),
            activeIndex = 0
        )
        val uiStateField = PlayerRuntime::class.java.getDeclaredField("_uiState")
        uiStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val uiStateFlow = uiStateField.get(runtime) as MutableStateFlow<com.wxy.playerlite.feature.player.model.PlayerUiState>
        uiStateFlow.value = uiStateFlow.value.copy(
            playbackState = AUDIO_TRACK_PLAYSTATE_STOPPED,
            isPreparing = true
        )
        val bridge = FakePlayerControlBridge(currentSnapshot = null)
        val viewModel = PlayerViewModel(
            application = application,
            runtime = runtime,
            userRepository = FakeUserRepository(),
            songWikiRepository = FakeSongWikiRepository(),
            serviceBridge = bridge,
            initializeSessionRestore = false,
            remoteSyncIntervalMs = 60_000L,
            uiProgressIntervalMs = 60_000L
        )
        try {
            runCurrent()
            bridge.clearActions()

            viewModel.skipToNextTrack()

            assertEquals(
                listOf(
                    "ensurePlaybackServiceStartedForPlayback",
                    "connectIfNeeded",
                    "syncQueue(size=2,active=0,play=true,start=${C.TIME_UNSET})",
                    "ensurePlaybackServiceStartedForPlayback",
                    "connectIfNeeded",
                    "seekToNextMediaItem"
                ),
                bridge.actions
            )
        } finally {
            clearViewModel(viewModel)
            runCurrent()
        }
    }

    @Test
    fun init_shouldOnlyPrewarmRemoteConnectionWithoutStartingPlaybackService() = runTest {
        val runtime = PlayerRuntime(application)
        val bridge = FakePlayerControlBridge(currentSnapshot = null)

        val viewModel = PlayerViewModel(
            application = application,
            runtime = runtime,
            userRepository = FakeUserRepository(),
            songWikiRepository = FakeSongWikiRepository(),
            serviceBridge = bridge,
            initializeSessionRestore = false,
            remoteSyncIntervalMs = 60_000L,
            uiProgressIntervalMs = 60_000L
        )
        try {
            runCurrent()

            assertEquals(
                listOf("prewarmConnection"),
                bridge.actions
            )
        } finally {
            clearViewModel(viewModel)
            runCurrent()
        }
    }

    @Test
    fun playSelectedAudio_shouldStartPlaybackServiceBeforeSyncingQueue() = runTest {
        val runtime = PlayerRuntime(application)
        runtime.applyExternalQueueSelection(
            items = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首"),
                onlineItem(index = 1, songId = "track-2", title = "第二首")
            ),
            activeIndex = 1
        )
        val bridge = FakePlayerControlBridge(currentSnapshot = null)
        val viewModel = PlayerViewModel(
            application = application,
            runtime = runtime,
            userRepository = FakeUserRepository(),
            songWikiRepository = FakeSongWikiRepository(),
            serviceBridge = bridge,
            initializeSessionRestore = false,
            remoteSyncIntervalMs = 60_000L,
            uiProgressIntervalMs = 60_000L
        )
        try {
            runCurrent()
            bridge.clearActions()

            viewModel.playSelectedAudio()

            assertEquals(
                listOf(
                    "ensurePlaybackServiceStartedForPlayback",
                    "connectIfNeeded",
                    "syncQueue(size=2,active=1,play=true,start=${C.TIME_UNSET})"
                ),
                bridge.actions
            )
        } finally {
            clearViewModel(viewModel)
            runCurrent()
        }
    }

    @Test
    fun selectPlaylistItem_shouldStartPlaybackImmediatelyEvenWhenCurrentStateIsStopped() = runTest {
        val runtime = PlayerRuntime(application)
        runtime.applyExternalQueueSelection(
            items = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首"),
                onlineItem(index = 1, songId = "track-2", title = "第二首")
            ),
            activeIndex = 0
        )
        val bridge = FakePlayerControlBridge(currentSnapshot = null)
        val viewModel = PlayerViewModel(
            application = application,
            runtime = runtime,
            userRepository = FakeUserRepository(),
            songWikiRepository = FakeSongWikiRepository(),
            serviceBridge = bridge,
            initializeSessionRestore = false,
            remoteSyncIntervalMs = 60_000L,
            uiProgressIntervalMs = 60_000L
        )
        try {
            runCurrent()
            bridge.clearActions()

            viewModel.selectPlaylistItem(1)

            assertEquals(
                listOf(
                    "ensurePlaybackServiceStartedForPlayback",
                    "connectIfNeeded",
                    "syncQueue(size=2,active=1,play=true,start=${C.TIME_UNSET})"
                ),
                bridge.actions
            )
        } finally {
            clearViewModel(viewModel)
            runCurrent()
        }
    }

    @Test
    fun playbackPreparing_shouldExposeCachedLyricsWithoutRemoteFetch() = runTest {
        val runtime = PlayerRuntime(application)
        runtime.applyExternalQueueSelection(
            items = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首")
            ),
            activeIndex = 0
        )
        val bridge = FakePlayerControlBridge(
            currentSnapshot = preparingSnapshot(songId = "track-1", title = "第一首")
        )
        val lyricRepository = FakeLyricRepository(
            cachedBySongId = mapOf("track-1" to demoLyrics("track-1"))
        )
        val viewModel = PlayerViewModel(
            application = application,
            runtime = runtime,
            userRepository = FakeUserRepository(),
            songWikiRepository = FakeSongWikiRepository(),
            lyricRepository = lyricRepository,
            serviceBridge = bridge,
            initializeSessionRestore = false,
            remoteSyncIntervalMs = 60_000L,
            uiProgressIntervalMs = 60_000L,
            lyricRequestDelayMs = 400L
        )
        try {
            runCurrent()

            val lyricUiState = viewModel.uiStateFlow.value.lyricUiState
            require(lyricUiState is PlayerLyricUiState.Content)
            assertEquals("第一句", lyricUiState.lyrics.lines.first().text)
            assertEquals(emptyList<String>(), lyricRepository.remoteFetchCalls)
        } finally {
            clearViewModel(viewModel)
            runCurrent()
        }
    }

    @Test
    fun init_shouldKeepLyricsPlaceholderUntilPlaybackPreparing() = runTest {
        val runtime = PlayerRuntime(application)
        runtime.applyExternalQueueSelection(
            items = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首")
            ),
            activeIndex = 0
        )
        val lyricRepository = FakeLyricRepository(
            remoteBySongId = mapOf("track-1" to demoLyrics("track-1"))
        )
        val viewModel = PlayerViewModel(
            application = application,
            runtime = runtime,
            userRepository = FakeUserRepository(),
            songWikiRepository = FakeSongWikiRepository(),
            lyricRepository = lyricRepository,
            serviceBridge = FakePlayerControlBridge(currentSnapshot = null),
            initializeSessionRestore = false,
            remoteSyncIntervalMs = 60_000L,
            uiProgressIntervalMs = 60_000L,
            lyricRequestDelayMs = 400L
        )
        try {
            runCurrent()
            assertEquals(emptyList<String>(), lyricRepository.remoteFetchCalls)

            advanceTimeBy(400L)
            runCurrent()
            assertEquals(emptyList<String>(), lyricRepository.remoteFetchCalls)
            assertTrue(viewModel.uiStateFlow.value.lyricUiState is PlayerLyricUiState.Placeholder)
        } finally {
            clearViewModel(viewModel)
            runCurrent()
        }
    }

    @Test
    fun playbackPreparing_shouldFetchRemoteLyricsImmediately() = runTest {
        val runtime = PlayerRuntime(application)
        runtime.applyExternalQueueSelection(
            items = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首")
            ),
            activeIndex = 0
        )
        val bridge = FakePlayerControlBridge(
            currentSnapshot = preparingSnapshot(songId = "track-1", title = "第一首")
        )
        val lyricRepository = FakeLyricRepository(
            remoteBySongId = mapOf("track-1" to demoLyrics("track-1"))
        )
        val viewModel = PlayerViewModel(
            application = application,
            runtime = runtime,
            userRepository = FakeUserRepository(),
            songWikiRepository = FakeSongWikiRepository(),
            lyricRepository = lyricRepository,
            serviceBridge = bridge,
            initializeSessionRestore = false,
            remoteSyncIntervalMs = 60_000L,
            uiProgressIntervalMs = 60_000L,
            lyricRequestDelayMs = 400L
        )
        try {
            runCurrent()
            assertEquals(listOf("track-1"), lyricRepository.remoteFetchCalls)
        } finally {
            clearViewModel(viewModel)
            runCurrent()
        }
    }

    @Test
    fun switchingSongWithoutPreparing_shouldNotFetchLyricsForEitherSong() = runTest {
        val runtime = PlayerRuntime(application)
        runtime.applyExternalQueueSelection(
            items = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首"),
                onlineItem(index = 1, songId = "track-2", title = "第二首")
            ),
            activeIndex = 0
        )
        val lyricRepository = FakeLyricRepository(
            remoteBySongId = mapOf(
                "track-1" to demoLyrics("track-1"),
                "track-2" to demoLyrics("track-2")
            )
        )
        val viewModel = PlayerViewModel(
            application = application,
            runtime = runtime,
            userRepository = FakeUserRepository(),
            songWikiRepository = FakeSongWikiRepository(),
            lyricRepository = lyricRepository,
            serviceBridge = FakePlayerControlBridge(currentSnapshot = null),
            initializeSessionRestore = false,
            remoteSyncIntervalMs = 60_000L,
            uiProgressIntervalMs = 60_000L,
            lyricRequestDelayMs = 400L
        )
        try {
            runCurrent()

            viewModel.selectPlaylistItem(1)
            runCurrent()
            advanceTimeBy(400L)
            runCurrent()

            assertEquals(emptyList<String>(), lyricRepository.remoteFetchCalls)
        } finally {
            clearViewModel(viewModel)
            runCurrent()
        }
    }

    @Test
    fun onRetryLyrics_shouldRefetchAfterPreparingReturnedEmpty() = runTest {
        val runtime = PlayerRuntime(application)
        runtime.applyExternalQueueSelection(
            items = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首")
            ),
            activeIndex = 0
        )
        val bridge = FakePlayerControlBridge(
            currentSnapshot = preparingSnapshot(songId = "track-1", title = "第一首")
        )
        var fetchCount = 0
        val lyricRepository = FakeLyricRepository(
            fetchBehavior = { songId ->
                fetchCount += 1
                when (fetchCount) {
                    1 -> null
                    else -> demoLyrics(songId)
                }
            }
        )
        val viewModel = PlayerViewModel(
            application = application,
            runtime = runtime,
            userRepository = FakeUserRepository(),
            songWikiRepository = FakeSongWikiRepository(),
            lyricRepository = lyricRepository,
            serviceBridge = bridge,
            initializeSessionRestore = false,
            remoteSyncIntervalMs = 60_000L,
            uiProgressIntervalMs = 60_000L,
            lyricRequestDelayMs = 400L
        )
        try {
            runCurrent()

            assertTrue(viewModel.uiStateFlow.value.lyricUiState is PlayerLyricUiState.Empty)
            assertEquals(listOf("track-1"), lyricRepository.remoteFetchCalls)

            viewModel.onRetryLyrics()
            runCurrent()

            val lyricUiState = viewModel.uiStateFlow.value.lyricUiState
            require(lyricUiState is PlayerLyricUiState.Content)
            assertEquals("第一句", lyricUiState.lyrics.lines.first().text)
            assertEquals(listOf("track-1", "track-1"), lyricRepository.remoteFetchCalls)
        } finally {
            clearViewModel(viewModel)
            runCurrent()
        }
    }

    @Test
    fun playbackPreparing_shouldProjectCurrentLyricToDisplayMetadataForSystemSurfaces() = runTest {
        val runtime = PlayerRuntime(application)
        runtime.applyExternalQueueSelection(
            items = listOf(
                onlineItem(index = 0, songId = "track-1", title = "第一首")
            ),
            activeIndex = 0
        )
        val bridge = FakePlayerControlBridge(
            currentSnapshot = preparingSnapshot(
                songId = "track-1",
                title = "第一首",
                currentPositionMs = 3_500L
            )
        )
        val lyricRepository = FakeLyricRepository(
            cachedBySongId = mapOf("track-1" to demoLyrics("track-1"))
        )
        val viewModel = PlayerViewModel(
            application = application,
            runtime = runtime,
            userRepository = FakeUserRepository(),
            songWikiRepository = FakeSongWikiRepository(),
            lyricRepository = lyricRepository,
            serviceBridge = bridge,
            initializeSessionRestore = false,
            remoteSyncIntervalMs = 60_000L,
            uiProgressIntervalMs = 60_000L
        )
        try {
            runCurrent()
            runCurrent()

            assertTrue(
                bridge.actions.contains(
                    "setDisplayMetadata(title=第二句,subtitle=第一首 - 测试歌手)"
                )
            )
        } finally {
            clearViewModel(viewModel)
            runCurrent()
        }
    }

    private fun onlineItem(
        index: Int,
        songId: String,
        title: String
    ): PlaylistItem {
        return PlaylistItem(
            id = "playlist:test:$index:$songId",
            displayName = title,
            songId = songId,
            title = title,
            artistText = "测试歌手",
            albumTitle = "测试专辑",
            coverUrl = "https://example.com/$songId.jpg",
            durationMs = 200_000L,
            itemType = PlaylistItemType.ONLINE,
            contextType = "playlist",
            contextId = "playlist-test",
            contextTitle = "测试歌单"
        )
    }

    private fun clearViewModel(viewModel: PlayerViewModel) {
        val method = PlayerViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
    }
}

private class FakePlayerControlBridge(
    private val currentSnapshot: RemotePlaybackSnapshot?
) : PlayerControlBridge {
    val actions = mutableListOf<String>()

    fun clearActions() {
        actions.clear()
    }

    override fun prewarmConnection() {
        actions += "prewarmConnection"
    }

    override fun ensurePlaybackServiceStartedForPlayback() {
        actions += "ensurePlaybackServiceStartedForPlayback"
    }

    override fun connectIfNeeded() {
        actions += "connectIfNeeded"
    }

    override fun syncQueue(
        queue: List<PlayableItem>,
        activeIndex: Int,
        playWhenReady: Boolean,
        startPositionMs: Long
    ): Boolean {
        actions += "syncQueue(size=${queue.size},active=$activeIndex,play=$playWhenReady,start=$startPositionMs)"
        return true
    }

    override fun play(): Boolean = true

    override fun pause(): Boolean = true

    override fun seekTo(positionMs: Long): Boolean = true

    override fun seekToNextMediaItem(): Boolean {
        actions += "seekToNextMediaItem"
        return true
    }

    override fun seekToPreviousMediaItem(): Boolean {
        actions += "seekToPreviousMediaItem"
        return true
    }

    override fun stop(): Boolean = true

    override fun clearCache(): Boolean = true

    override fun setPlaybackSpeed(speed: Float, onResult: ((Boolean) -> Unit)?): Boolean = true

    override fun setPlaybackMode(playbackMode: PlaybackMode, onResult: ((Boolean) -> Unit)?): Boolean = true

    override fun setDisplayMetadata(title: String?, subtitle: String?): Boolean {
        actions += "setDisplayMetadata(title=${title.orEmpty()},subtitle=${subtitle.orEmpty()})"
        return true
    }

    override fun currentSnapshot(): RemotePlaybackSnapshot? = currentSnapshot

    override fun release() = Unit
}

private class FakeUserRepository : UserRepository {
    override val loginStateFlow: StateFlow<LoginState> = MutableStateFlow(LoginState.LoggedOut)

    override fun currentSession(): UserSession? = null

    override suspend fun restorePersistedSession() = Unit

    override suspend fun loginWithPhone(
        phone: String,
        password: String,
        countryCode: String
    ): UserSession {
        error("not used in test")
    }

    override suspend fun loginWithEmail(email: String, password: String): UserSession {
        error("not used in test")
    }

    override suspend fun refreshUserInfo(): UserSession? = null

    override suspend fun logout() = Unit
}

private class FakeSongWikiRepository : SongWikiRepository {
    override suspend fun fetchSongWiki(songId: String) = null
}

private class FakeLyricRepository(
    private val cachedBySongId: Map<String, ParsedLyrics> = emptyMap(),
    private val remoteBySongId: Map<String, ParsedLyrics> = emptyMap(),
    private val fetchBehavior: ((String) -> ParsedLyrics?)? = null
) : LyricRepository {
    val remoteFetchCalls = mutableListOf<String>()

    override suspend fun readCachedLyrics(songId: String): ParsedLyrics? = cachedBySongId[songId]

    override suspend fun fetchLyrics(songId: String): ParsedLyrics? {
        remoteFetchCalls += songId
        return fetchBehavior?.invoke(songId) ?: remoteBySongId[songId]
    }
}

private fun demoLyrics(songId: String): ParsedLyrics {
    return ParsedLyrics(
        songId = songId,
        lines = listOf(
            LyricLine(timestampMs = 1_000L, text = "第一句"),
            LyricLine(timestampMs = 3_000L, text = "第二句")
        ),
        rawText = "[00:01.00]第一句\n[00:03.00]第二句"
    )
}

private fun preparingSnapshot(
    songId: String,
    title: String,
    currentPositionMs: Long = 0L
): RemotePlaybackSnapshot {
    return RemotePlaybackSnapshot(
        playbackState = 2,
        playWhenReady = true,
        isPlaying = false,
        isSeekSupported = true,
        currentPositionMs = currentPositionMs,
        durationMs = 200_000L,
        playbackSpeed = 1.0f,
        playbackMode = PlaybackMode.LIST_LOOP,
        statusText = "Preparing",
        currentPlayable = PlayableItemSnapshot(
            id = "playlist:test:0:$songId",
            songId = songId,
            title = title,
            artistText = "测试歌手",
            albumTitle = "测试专辑",
            coverUrl = "https://example.com/$songId.jpg",
            durationMs = 200_000L,
            playbackUri = "https://example.com/$songId.mp3"
        ),
        currentMediaId = "playlist:test:0:$songId",
        playbackOutputInfo = null,
        audioMeta = null
    )
}
