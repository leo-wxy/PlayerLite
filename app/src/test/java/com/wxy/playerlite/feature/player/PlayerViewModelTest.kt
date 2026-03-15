package com.wxy.playerlite.feature.player

import android.app.Application
import android.content.Context
import androidx.media3.common.C
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.feature.main.MainDispatcherRule
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
