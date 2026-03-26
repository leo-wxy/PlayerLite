package com.wxy.playerlite.feature.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxy.playerlite.resolveCurrentPlayerArtistId
import com.wxy.playerlite.feature.artist.ArtistDetailActivity
import com.wxy.playerlite.feature.player.ui.PlayerScreenCallbacks
import com.wxy.playerlite.feature.player.ui.PlayerScreen
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

class PlayerActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()
    private var pendingOpenPlayerLaunchRequests by mutableStateOf(0)
    private var pendingStartPlaybackLaunchRequests by mutableStateOf(0)
    private var pendingOpenPlaylistLaunchRequests by mutableStateOf(0)

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        viewModel.onAudioPicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enqueueLaunchRequest(intent)
        enableEdgeToEdge()
        setContent {
            val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
            val resolvedCurrentArtistId = resolveCurrentPlayerArtistId(state)
            var launchRequestHandled by rememberSaveable {
                mutableStateOf(
                    pendingOpenPlayerLaunchRequests == 0 &&
                        pendingStartPlaybackLaunchRequests == 0 &&
                        pendingOpenPlaylistLaunchRequests == 0
                )
            }
            LaunchedEffect(
                pendingOpenPlayerLaunchRequests,
                pendingStartPlaybackLaunchRequests,
                pendingOpenPlaylistLaunchRequests,
                state.showPlaylistSheet
            ) {
                if (!launchRequestHandled) {
                    if (pendingStartPlaybackLaunchRequests > 0) {
                        viewModel.playSelectedAudio()
                    }
                    when (
                        resolvePlaylistSheetLaunchAction(
                            hasOpenPlayerLaunchRequest = pendingOpenPlayerLaunchRequests > 0,
                            hasOpenPlaylistLaunchRequest = pendingOpenPlaylistLaunchRequests > 0,
                            isPlaylistSheetVisible = state.showPlaylistSheet
                        )
                    ) {
                        PlaylistSheetLaunchAction.OPEN -> viewModel.onTogglePlaylistSheet()
                        PlaylistSheetLaunchAction.CLOSE -> viewModel.onDismissPlaylistSheet()
                        PlaylistSheetLaunchAction.NONE -> Unit
                    }
                    pendingOpenPlayerLaunchRequests = 0
                    pendingStartPlaybackLaunchRequests = 0
                    pendingOpenPlaylistLaunchRequests = 0
                    launchRequestHandled = true
                }
            }
            LaunchedEffect(
                pendingOpenPlayerLaunchRequests,
                pendingStartPlaybackLaunchRequests,
                pendingOpenPlaylistLaunchRequests
            ) {
                if (
                    pendingOpenPlayerLaunchRequests > 0 ||
                    pendingStartPlaybackLaunchRequests > 0 ||
                    pendingOpenPlaylistLaunchRequests > 0
                ) {
                    launchRequestHandled = false
                }
            }
            PlayerLiteTheme {
                val screenCallbacks = PlayerScreenCallbacks(
                    onPickAudio = {
                        pickAudioLauncher.launch(arrayOf("audio/*"))
                    },
                    onTogglePlaylistSheet = viewModel::onTogglePlaylistSheet,
                    onDismissPlaylistSheet = viewModel::onDismissPlaylistSheet,
                    onShowSongWiki = viewModel::onShowSongWiki,
                    onDismissSongWiki = viewModel::onDismissSongWiki,
                    onRetrySongWiki = viewModel::onRetrySongWiki,
                    onRetryLyrics = viewModel::onRetryLyrics,
                    onSelectTopTab = viewModel::onSelectTopTab,
                    onSelectPlaylistItem = viewModel::selectPlaylistItem,
                    onClearPlaylist = viewModel::clearPlaylist,
                    onRemovePlaylistItem = viewModel::removePlaylistItem,
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
                    onDismissMoreActionsSheet = viewModel::onDismissPlayerMoreActions,
                    onDismissAudioEffectPage = viewModel::dismissAudioEffectSettings,
                    onShowPlaybackSpeedSettings = viewModel::showPlaybackSpeedSettings,
                    onShowAudioEffectSettings = viewModel::showAudioEffectSettings,
                    onReturnToMoreActionsRoot = viewModel::returnToPlayerMoreActionsRoot,
                    onSelectPlaybackSpeed = viewModel::updatePlaybackSpeed,
                    onSelectAudioEffectPreset = viewModel::updateAudioEffectPreset,
                    onBackClick = ::finish,
                    onShareClick = viewModel::onShareCurrentTrack,
                    onArtistClick = {
                        resolvedCurrentArtistId
                            ?.takeIf { it.isNotBlank() }
                            ?.let { artistId ->
                                startActivity(
                                    ArtistDetailActivity.createIntent(
                                        context = this@PlayerActivity,
                                        artistId = artistId
                                    )
                                )
                            }
                    },
                    onFavoriteClick = viewModel::onFavoriteCurrentTrack,
                    onMoreClick = viewModel::onShowPlayerMoreActions
                )
                PlayerScreen(
                    uiState = state,
                    currentDurationText = viewModel.formatDuration(state.displayedSeekMs),
                    totalDurationText = viewModel.formatDuration(state.durationMs),
                    currentArtistId = resolvedCurrentArtistId,
                    showSongWikiInlineButton = true,
                    enableEnterMotion = false,
                    callbacks = screenCallbacks
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        enqueueLaunchRequest(intent)
    }

    override fun onStop() {
        viewModel.onHostStop()
        super.onStop()
    }

    private fun enqueueLaunchRequest(intent: Intent?) {
        if (PlayerEntry.shouldOpenPlayerFromIntent(intent)) {
            pendingOpenPlayerLaunchRequests += 1
        }
        if (PlayerEntry.shouldStartPlaybackFromIntent(intent)) {
            pendingStartPlaybackLaunchRequests += 1
        }
        if (PlayerEntry.shouldOpenPlaylistFromIntent(intent)) {
            pendingOpenPlaylistLaunchRequests += 1
        }
    }

    companion object {
        fun createIntent(
            context: Context,
            openPlaylist: Boolean = false,
            startPlayback: Boolean = false
        ): Intent {
            return PlayerEntry.createIntent(
                context = context,
                openPlaylist = openPlaylist,
                startPlayback = startPlayback
            )
        }

        fun shouldOpenPlaylistFromIntent(intent: Intent?): Boolean {
            return PlayerEntry.shouldOpenPlaylistFromIntent(intent)
        }

        fun shouldOpenPlayerFromIntent(intent: Intent?): Boolean {
            return PlayerEntry.shouldOpenPlayerFromIntent(intent)
        }

        fun shouldStartPlaybackFromIntent(intent: Intent?): Boolean {
            return PlayerEntry.shouldStartPlaybackFromIntent(intent)
        }
    }
}
