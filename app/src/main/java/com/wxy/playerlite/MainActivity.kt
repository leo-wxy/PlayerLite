package com.wxy.playerlite

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxy.playerlite.feature.player.PlayerViewModel
import com.wxy.playerlite.feature.player.ui.PlayerScreen
import com.wxy.playerlite.feature.user.InitialLoginLaunchGate
import com.wxy.playerlite.feature.user.LoginActivity
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

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
            var initialLoginGateHandled by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(userState.isBusy, userState.isLoggedIn, initialLoginGateHandled) {
                if (
                    InitialLoginLaunchGate.shouldLaunch(
                        isSessionReady = !userState.isBusy,
                        isLoggedIn = userState.isLoggedIn,
                        hasHandledInitialGate = initialLoginGateHandled
                    )
                ) {
                    initialLoginGateHandled = true
                    loginLauncher.launch(LoginActivity.createIntent(this@MainActivity))
                }
            }
            PlayerLiteTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
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
                        modifier = Modifier.padding(innerPadding),
                        onPickAudio = {
                            pickAudioLauncher.launch(arrayOf("audio/*"))
                        },
                        onRunUiTestEntry = viewModel::runUiTestEntry,
                        onClearCache = viewModel::clearCache,
                        onOpenLogin = {
                            startActivity(LoginActivity.createIntent(this))
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
        }
    }

    override fun onStop() {
        viewModel.onHostStop()
        super.onStop()
    }
}
