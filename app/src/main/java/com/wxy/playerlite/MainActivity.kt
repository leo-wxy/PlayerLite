package com.wxy.playerlite

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxy.playerlite.feature.player.PlayerViewModel
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.main.HomeContent
import com.wxy.playerlite.feature.main.ContentEntryAction
import com.wxy.playerlite.feature.main.HomeSurfaceMode
import com.wxy.playerlite.feature.main.HomeOverviewScreen
import com.wxy.playerlite.feature.main.HomeViewModel
import com.wxy.playerlite.feature.main.MainBottomBar
import com.wxy.playerlite.feature.main.MainShellLayoutSpec
import com.wxy.playerlite.feature.main.MainShellState
import com.wxy.playerlite.feature.main.MainTab
import com.wxy.playerlite.feature.main.PlayerExpandedScreen
import com.wxy.playerlite.feature.main.PlayerExpandedTopActionButton
import com.wxy.playerlite.feature.main.UserCenterViewModel
import com.wxy.playerlite.feature.main.resolveContentEntryLaunch
import com.wxy.playerlite.feature.local.LocalSongsActivity
import com.wxy.playerlite.feature.search.SearchActivity
import com.wxy.playerlite.feature.artist.ArtistDetailActivity
import com.wxy.playerlite.feature.main.UserCenterScreen
import com.wxy.playerlite.feature.player.ui.PlayerSongWikiButton
import com.wxy.playerlite.feature.player.ui.PlayerScreen
import com.wxy.playerlite.feature.player.ui.components.PlaylistBottomSheet
import com.wxy.playerlite.feature.user.InitialLoginLaunchGate
import com.wxy.playerlite.feature.user.LoginActivity
import com.wxy.playerlite.playback.model.PlaybackLaunchRequest
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val userCenterViewModel: UserCenterViewModel by viewModels()
    private var shouldOpenPlayerFromLocalSongs by mutableStateOf(false)
    private var pendingOpenPlayerLaunchRequests by mutableStateOf(0)

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        viewModel.onAudioPicked(uri)
    }

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Unit
    }

    private val localSongsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        shouldOpenPlayerFromLocalSongs = LocalSongsActivity.shouldOpenPlayerFromResult(
            resultCode = result.resultCode,
            data = result.data
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (shouldOpenPlayerFromIntent(intent)) {
            pendingOpenPlayerLaunchRequests += 1
        }
        enableEdgeToEdge()

        setContent {
            val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
            val userState = viewModel.userSessionUiStateFlow.collectAsStateWithLifecycle().value
            val homeState = homeViewModel.uiStateFlow.collectAsStateWithLifecycle().value
            val userCenterState = userCenterViewModel.uiStateFlow.collectAsStateWithLifecycle().value
            val isSessionReady = !userState.isBusy
            var initialLoginGateHandled by rememberSaveable { mutableStateOf(false) }
            var shellState by rememberSaveable { mutableStateOf(MainShellState()) }
            LaunchedEffect(isSessionReady, userState.isLoggedIn, initialLoginGateHandled) {
                if (
                    InitialLoginLaunchGate.shouldLaunch(
                        isSessionReady = isSessionReady,
                        isLoggedIn = userState.isLoggedIn,
                        hasHandledInitialGate = initialLoginGateHandled
                    )
                ) {
                    initialLoginGateHandled = true
                    loginLauncher.launch(LoginActivity.createIntent(this@MainActivity))
                }
            }
            LaunchedEffect(shouldOpenPlayerFromLocalSongs, pendingOpenPlayerLaunchRequests) {
                if (shouldOpenPlayerFromLocalSongs || pendingOpenPlayerLaunchRequests > 0) {
                    shellState = shellState.openPlayer()
                    shouldOpenPlayerFromLocalSongs = false
                    pendingOpenPlayerLaunchRequests = 0
                }
            }
            BackHandler(
                enabled = shellState.selectedTab == MainTab.HOME &&
                    shellState.homeSurfaceMode == HomeSurfaceMode.PLAYER_EXPANDED
            ) {
                shellState = shellState.collapsePlayer()
            }
            PlayerLiteTheme {
                if (
                    !InitialLoginLaunchGate.shouldShowMainContent(
                        isSessionReady = isSessionReady,
                        isLoggedIn = userState.isLoggedIn,
                        hasHandledInitialGate = initialLoginGateHandled
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            AnimatedVisibility(
                                visible = shellState.shouldShowBottomBar,
                                enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
                                    slideInVertically(
                                        animationSpec = tween(durationMillis = 220),
                                        initialOffsetY = { fullHeight -> fullHeight / 2 }
                                    ),
                                exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
                                    slideOutVertically(
                                        animationSpec = tween(durationMillis = 180),
                                        targetOffsetY = { fullHeight -> fullHeight / 2 }
                                    )
                            ) {
                                MainBottomBar(
                                    selectedTab = shellState.selectedTab,
                                    onTabSelected = { tab ->
                                        shellState = shellState.selectTab(tab)
                                    }
                                )
                            }
                        }
                    ) { innerPadding ->
                        val topInset = innerPadding.calculateTopPadding()
                        when (shellState.selectedTab) {
                            MainTab.HOME -> {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    HomeContent(
                                        homeSurfaceMode = shellState.homeSurfaceMode,
                                        overviewContent = {
                                            HomeOverviewScreen(
                                                playerState = state,
                                                overviewState = homeState,
                                                onSearchClick = {
                                                    startActivity(
                                                        SearchActivity.createIntent(this@MainActivity)
                                                    )
                                                },
                                                onRetry = homeViewModel::refresh,
                                                onItemClick = ::handleContentEntryAction,
                                                onOpenPlayer = {
                                                    shellState = shellState.openPlayer()
                                                },
                                                onTogglePlayback = {
                                                    if (state.playbackState == AUDIO_TRACK_PLAYSTATE_PLAYING) {
                                                        viewModel.pausePlayback()
                                                    } else if (state.playbackState == AUDIO_TRACK_PLAYSTATE_PAUSED) {
                                                        viewModel.resumePlayback()
                                                    } else {
                                                        viewModel.playSelectedAudio()
                                                    }
                                                },
                                                onOpenPlaylist = viewModel::onTogglePlaylistSheet,
                                                onSkipPrevious = viewModel::skipToPreviousTrack,
                                                onSkipNext = viewModel::skipToNextTrack,
                                                modifier = Modifier.padding(
                                                    MainShellLayoutSpec.homeContentPadding(
                                                        mode = HomeSurfaceMode.OVERVIEW,
                                                        topInset = topInset
                                                    )
                                                )
                                            )
                                        },
                                        expandedContent = {
                                            PlayerExpandedScreen(
                                                onBack = {
                                                    shellState = shellState.collapsePlayer()
                                                },
                                                modifier = Modifier.fillMaxSize(),
                                                showTopChrome = false
                                            ) {
                                                PlayerScreen(
                                                    fileName = state.currentTrackTitle,
                                                    artistText = state.currentTrackArtist,
                                                    status = state.statusText,
                                                    hasSelection = state.hasSelection,
                                                    playlistItems = state.playlistItems,
                                                    activePlaylistIndex = state.activePlaylistIndex,
                                                    showPlaylistSheet = false,
                                                    showSongWikiSheet = state.showSongWikiSheet,
                                                    songWikiUiState = state.songWikiUiState,
                                                    isPreparing = state.isPreparing,
                                                    playbackState = state.playbackState,
                                                    isSeekSupported = state.isSeekSupported,
                                                    playbackMode = state.playbackMode,
                                                    showOriginalOrderInShuffle = state.showOriginalOrderInShuffle,
                                                    canReorderPlaylist = state.canReorderPlaylist,
                                                    seekValueMs = state.displayedSeekMs,
                                                    currentDurationText = viewModel.formatDuration(state.displayedSeekMs),
                                                    durationMs = state.durationMs,
                                                    totalDurationText = viewModel.formatDuration(state.durationMs),
                                                    currentSongId = state.currentSongId,
                                                    currentArtistId = state.currentArtistId,
                                                    currentCoverUrl = state.currentCoverUrl,
                                                    showSongWikiInlineButton = true,
                                                    enableEnterMotion = false,
                                                    modifier = Modifier.fillMaxSize(),
                                                    onPickAudio = {
                                                        pickAudioLauncher.launch(arrayOf("audio/*"))
                                                    },
                                                    onTogglePlaylistSheet = viewModel::onTogglePlaylistSheet,
                                                    onDismissPlaylistSheet = viewModel::onDismissPlaylistSheet,
                                                    onShowSongWiki = viewModel::onShowSongWiki,
                                                    onDismissSongWiki = viewModel::onDismissSongWiki,
                                                    onRetrySongWiki = viewModel::onRetrySongWiki,
                                                    onSelectPlaylistItem = { index ->
                                                        viewModel.selectPlaylistItem(index)
                                                    },
                                                    onRemovePlaylistItem = { index ->
                                                        viewModel.removePlaylistItem(index)
                                                    },
                                                    onMovePlaylistItem = viewModel::movePlaylistItem,
                                                    onPlay = viewModel::playSelectedAudio,
                                                    onPrevious = viewModel::skipToPreviousTrack,
                                                    onNext = viewModel::skipToNextTrack,
                                                    onPause = viewModel::pausePlayback,
                                                    onResume = viewModel::resumePlayback,
                                                    onCyclePlaybackMode = viewModel::cyclePlaybackMode,
                                                    onShowOriginalOrderInShuffleChange = viewModel::setShowOriginalOrderInShuffle,
                                                    onSeekValueChange = viewModel::onSeekValueChange,
                                                    onSeekFinished = viewModel::onSeekFinished,
                                                    onBackClick = {
                                                        shellState = shellState.collapsePlayer()
                                                    },
                                                    onShareClick = viewModel::onShareCurrentTrack,
                                                    onArtistClick = {
                                                        state.currentArtistId
                                                            ?.takeIf { it.isNotBlank() }
                                                            ?.let { artistId ->
                                                                startActivity(
                                                                    ArtistDetailActivity.createIntent(
                                                                        context = this@MainActivity,
                                                                        artistId = artistId
                                                                    )
                                                                )
                                                            }
                                                    },
                                                    onFavoriteClick = viewModel::onFavoriteCurrentTrack,
                                                    onMoreClick = viewModel::onShowPlayerMoreActions
                                                )
                                            }
                                        }
                                    )

                                    PlaylistBottomSheet(
                                        visible = state.showPlaylistSheet,
                                        items = state.playlistItems,
                                        activeIndex = state.activePlaylistIndex,
                                        playbackMode = state.playbackMode,
                                        showOriginalOrderInShuffle = state.showOriginalOrderInShuffle,
                                        canReorder = state.canReorderPlaylist,
                                        onDismiss = viewModel::onDismissPlaylistSheet,
                                        onShowOriginalOrderInShuffleChange = viewModel::setShowOriginalOrderInShuffle,
                                        onSelect = { index -> viewModel.selectPlaylistItem(index) },
                                        onRemove = { index -> viewModel.removePlaylistItem(index) },
                                        onMove = viewModel::movePlaylistItem,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            MainTab.USER_CENTER -> {
                                UserCenterScreen(
                                    userState = userState,
                                    contentState = userCenterState,
                                    onTabSelected = userCenterViewModel::onTabSelected,
                                    onRetryCurrentTab = userCenterViewModel::retryCurrentTab,
                                    onContentClick = ::handleContentEntryAction,
                                    onOpenLocalSongs = {
                                        localSongsLauncher.launch(
                                            LocalSongsActivity.createIntent(this)
                                        )
                                    },
                                    onLoginClick = {
                                        startActivity(LoginActivity.createIntent(this))
                                    },
                                    onLogoutClick = viewModel::logout,
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (shouldOpenPlayerFromIntent(intent)) {
            pendingOpenPlayerLaunchRequests += 1
        }
    }

    override fun onStop() {
        viewModel.onHostStop()
        super.onStop()
    }

    private fun handleContentEntryAction(action: ContentEntryAction) {
        val launch = resolveContentEntryLaunch(
            context = this,
            action = action
        )
        val intent = launch.intent
        if (intent == null) {
            launch.failureMessage?.let(::showContentEntryMessage)
            return
        }
        val canLaunch = intent.component != null || intent.resolveActivity(packageManager) != null
        if (!canLaunch) {
            showContentEntryMessage(launch.failureMessage ?: "当前内容暂时无法打开")
            return
        }
        runCatching {
            startActivity(intent)
        }.onFailure {
            showContentEntryMessage(launch.failureMessage ?: "当前内容暂时无法打开")
        }
    }

    private fun showContentEntryMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun createIntent(
            context: Context,
            openPlayer: Boolean = false
        ): Intent {
            return PlaybackLaunchRequest.createMainActivityIntent(
                context = context,
                openPlayer = openPlayer
            )
        }

        fun shouldOpenPlayerFromIntent(intent: Intent?): Boolean {
            return PlaybackLaunchRequest.shouldOpenPlayer(intent)
        }
    }
}
