package com.wxy.playerlite

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxy.playerlite.feature.local.LocalSongsActivity
import com.wxy.playerlite.feature.main.ContentEntryAction
import com.wxy.playerlite.feature.main.HomeOverviewScreen
import com.wxy.playerlite.feature.main.HomeChromeLayoutSpec
import com.wxy.playerlite.feature.main.HomeViewModel
import com.wxy.playerlite.feature.main.LikedContentActivity
import com.wxy.playerlite.feature.main.MainShellMiniPlayerChrome
import com.wxy.playerlite.feature.main.MainShellScaffold
import com.wxy.playerlite.feature.main.MainShellState
import com.wxy.playerlite.feature.main.MainTab
import com.wxy.playerlite.feature.main.RecentSongsActivity
import com.wxy.playerlite.feature.main.SettingsActivity
import com.wxy.playerlite.feature.main.UserCenterScreen
import com.wxy.playerlite.feature.main.UserCenterViewModel
import com.wxy.playerlite.feature.main.resolveContentEntryLaunch
import com.wxy.playerlite.feature.player.PlayerActivity
import com.wxy.playerlite.feature.player.PlayerEntry
import com.wxy.playerlite.feature.player.PlayerViewModel
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.search.SearchActivity
import com.wxy.playerlite.feature.user.InitialLoginLaunchGate
import com.wxy.playerlite.feature.user.LoginActivity
import com.wxy.playerlite.feature.webplaylistimport.WebPlaylistImportActivity
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val userCenterViewModel: UserCenterViewModel by viewModels()
    private var shellState by mutableStateOf(MainShellState())

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Unit
    }

    private val localSongsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (
            LocalSongsActivity.shouldOpenPlayerFromResult(
                resultCode = result.resultCode,
                data = result.data
            )
        ) {
            startActivity(
                PlayerActivity.createIntent(
                    context = this,
                    startPlayback = true
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreShellState(savedInstanceState)
        enableEdgeToEdge()
        redirectLegacyPlayerLaunchRequest(intent)

        setContent {
            val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
            val userState = viewModel.userSessionUiStateFlow.collectAsStateWithLifecycle().value
            val homeState = homeViewModel.uiStateFlow.collectAsStateWithLifecycle().value
            val userCenterState = userCenterViewModel.uiStateFlow.collectAsStateWithLifecycle().value
            val isSessionReady = !userState.isBusy
            var initialLoginGateHandled by rememberSaveable { mutableStateOf(false) }
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
                    MainShellScaffold(
                        selectedTab = shellState.selectedTab,
                        onTabSelected = { tab ->
                            shellState = shellState.selectTab(tab)
                        },
                        playerState = state,
                        onOpenPlayer = {
                            startActivity(
                                PlayerActivity.createIntent(this@MainActivity)
                            )
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
                        onTogglePlaylistSheet = viewModel::onTogglePlaylistSheet,
                        onDismissPlaylistSheet = viewModel::onDismissPlaylistSheet,
                        onCyclePlaybackMode = viewModel::cyclePlaybackMode,
                        onShowOriginalOrderInShuffleChange = viewModel::setShowOriginalOrderInShuffle,
                        onSelectPlaylistItem = viewModel::selectPlaylistItem,
                        onClearPlaylist = viewModel::clearPlaylist,
                        onRemovePlaylistItem = viewModel::removePlaylistItem,
                        onMovePlaylistItem = viewModel::movePlaylistItem,
                        onSkipPrevious = viewModel::skipToPreviousTrack,
                        onSkipNext = viewModel::skipToNextTrack
                    ) { innerPadding ->
                        val topInset = innerPadding.calculateTopPadding()
                        when (shellState.selectedTab) {
                            MainTab.HOME -> {
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
                                    modifier = Modifier.padding(top = topInset)
                                )
                            }

                            MainTab.USER_CENTER -> {
                                UserCenterScreen(
                                    userState = userState,
                                    contentState = userCenterState,
                                    onRetryPlaylists = userCenterViewModel::retryPlaylists,
                                    onContentClick = ::handleContentEntryAction,
                                    onOpenLikedSongs = {
                                        startActivity(
                                            LikedContentActivity.createIntent(this@MainActivity)
                                        )
                                    },
                                    onOpenRecentSongs = {
                                        startActivity(
                                            RecentSongsActivity.createIntent(this@MainActivity)
                                        )
                                    },
                                    onOpenLocalSongs = {
                                        localSongsLauncher.launch(
                                            LocalSongsActivity.createIntent(this@MainActivity)
                                        )
                                    },
                                    onOpenPlaylistImport = {
                                        startActivity(
                                            WebPlaylistImportActivity.createIntent(this@MainActivity)
                                        )
                                    },
                                    onOpenSettings = {
                                        startActivity(
                                            SettingsActivity.createIntent(this@MainActivity)
                                        )
                                    },
                                    onLoginClick = {
                                        startActivity(LoginActivity.createIntent(this@MainActivity))
                                    },
                                    onLogoutClick = viewModel::logout,
                                    bottomContentPadding = if (state.hasSelection) {
                                        HomeChromeLayoutSpec.homeOverviewScrollBottomPadding
                                    } else {
                                        HomeChromeLayoutSpec.userCenterScrollBottomPadding
                                    },
                                    modifier = Modifier.padding(top = topInset)
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
        redirectLegacyPlayerLaunchRequest(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(STATE_SHELL_STATE, shellState)
    }

    override fun onStop() {
        viewModel.onHostStop()
        super.onStop()
    }

    private fun redirectLegacyPlayerLaunchRequest(intent: Intent?) {
        val redirectIntent = resolveLegacyPlayerLaunchRedirectIntent(
            sourceIntent = intent,
            context = this
        ) ?: return
        startActivity(redirectIntent)
        intent?.let {
            setIntent(Intent(it).replaceExtras(Bundle()))
        }
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

    @Suppress("DEPRECATION")
    private fun restoreShellState(savedInstanceState: Bundle?) {
        val restored = savedInstanceState
            ?.getSerializable(STATE_SHELL_STATE) as? MainShellState
        shellState = restored ?: MainShellState()
    }

    private companion object {
        private const val STATE_SHELL_STATE = "main_shell_state"
    }
}

internal fun resolveLegacyPlayerLaunchRedirectIntent(
    sourceIntent: Intent?,
    context: android.content.Context
): Intent? {
    val shouldOpenPlayer = PlayerEntry.shouldOpenPlayerFromIntent(sourceIntent)
    val shouldStartPlayback = PlayerEntry.shouldStartPlaybackFromIntent(sourceIntent)
    val shouldOpenPlaylist = PlayerEntry.shouldOpenPlaylistFromIntent(sourceIntent)
    if (!shouldOpenPlayer && !shouldStartPlayback && !shouldOpenPlaylist) {
        return null
    }
    return PlayerEntry.createIntent(
        context = context,
        openPlaylist = shouldOpenPlaylist,
        startPlayback = shouldStartPlayback
    )
}
