package com.wxy.playerlite

import android.net.Uri
import android.os.Bundle
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxy.playerlite.feature.player.PlayerViewModel
import com.wxy.playerlite.feature.main.HomeContent
import com.wxy.playerlite.feature.main.HomeSurfaceMode
import com.wxy.playerlite.feature.main.HomeOverviewScreen
import com.wxy.playerlite.feature.main.HomeViewModel
import com.wxy.playerlite.feature.main.MainBottomBar
import com.wxy.playerlite.feature.main.MainShellLayoutSpec
import com.wxy.playerlite.feature.main.MainShellState
import com.wxy.playerlite.feature.main.MainTab
import com.wxy.playerlite.feature.main.PlayerExpandedScreen
import com.wxy.playerlite.feature.main.UserCenterScreen
import com.wxy.playerlite.feature.player.ui.PlayerScreen
import com.wxy.playerlite.feature.user.InitialLoginLaunchGate
import com.wxy.playerlite.feature.user.LoginActivity
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
            val userState = viewModel.userSessionUiStateFlow.collectAsStateWithLifecycle().value
            val homeState = homeViewModel.uiStateFlow.collectAsStateWithLifecycle().value
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
                                HomeContent(
                                    homeSurfaceMode = shellState.homeSurfaceMode,
                                    overviewContent = {
                                        HomeOverviewScreen(
                                            playerState = state,
                                            overviewState = homeState,
                                            onSearchClick = homeViewModel::onSearchClick,
                                            onRetry = homeViewModel::refresh,
                                            onOpenPlayer = {
                                                shellState = shellState.openPlayer()
                                            },
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
                                            modifier = Modifier.padding(
                                                MainShellLayoutSpec.homeContentPadding(
                                                    mode = HomeSurfaceMode.PLAYER_EXPANDED,
                                                    topInset = topInset
                                                )
                                            )
                                        ) {
                                            PlayerScreen(
                                                fileName = state.selectedFileName,
                                                status = state.statusText,
                                                audioMeta = state.audioMeta,
                                                playbackOutputInfo = state.playbackOutputInfo,
                                                hasSelection = state.hasSelection,
                                                playlistItems = state.playlistItems,
                                                activePlaylistIndex = state.activePlaylistIndex,
                                                showPlaylistSheet = state.showPlaylistSheet,
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
                                                isLoggedIn = userState.isLoggedIn,
                                                avatarUrl = userState.avatarUrl,
                                                enableEnterMotion = false,
                                                modifier = Modifier.fillMaxSize(),
                                                onPickAudio = {
                                                    pickAudioLauncher.launch(arrayOf("audio/*"))
                                                },
                                                onRunUiTestEntry = viewModel::runUiTestEntry,
                                                onClearCache = viewModel::clearCache,
                                                onOpenLogin = {
                                                    shellState = shellState.selectTab(MainTab.USER_CENTER)
                                                },
                                                onTogglePlaylistSheet = viewModel::onTogglePlaylistSheet,
                                                onDismissPlaylistSheet = viewModel::onDismissPlaylistSheet,
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
                                                onSeekFinished = viewModel::onSeekFinished
                                            )
                                        }
                                    }
                                )
                            }

                            MainTab.USER_CENTER -> {
                                UserCenterScreen(
                                    userState = userState,
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

    override fun onStop() {
        viewModel.onHostStop()
        super.onStop()
    }
}
