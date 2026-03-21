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
import com.wxy.playerlite.feature.player.ui.PlayerScreen
import com.wxy.playerlite.playback.model.PlaybackLaunchRequest
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
                PlayerScreen(
                    fileName = state.currentTrackTitle,
                    artistText = state.currentTrackArtist,
                    status = state.statusText,
                    hasSelection = state.hasSelection,
                    playlistItems = state.playlistItems,
                    activePlaylistIndex = state.activePlaylistIndex,
                    showPlaylistSheet = state.showPlaylistSheet,
                    showSongWikiSheet = state.showSongWikiSheet,
                    songWikiUiState = state.songWikiUiState,
                    lyricUiState = state.lyricUiState,
                    selectedTopTab = state.selectedTopTab,
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
                    currentArtistId = resolvedCurrentArtistId,
                    currentCoverUrl = state.currentCoverUrl,
                    showSongWikiInlineButton = true,
                    enableEnterMotion = false,
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
        if (shouldOpenPlayerFromIntent(intent)) {
            pendingOpenPlayerLaunchRequests += 1
        }
        if (shouldStartPlaybackFromIntent(intent)) {
            pendingStartPlaybackLaunchRequests += 1
        }
        if (shouldOpenPlaylistFromIntent(intent)) {
            pendingOpenPlaylistLaunchRequests += 1
        }
    }

    companion object {
        fun createIntent(
            context: Context,
            openPlaylist: Boolean = false,
            startPlayback: Boolean = false
        ): Intent {
            return PlaybackLaunchRequest.createPlayerActivityIntent(
                context = context,
                openPlaylist = openPlaylist,
                startPlayback = startPlayback
            )
        }

        fun shouldOpenPlaylistFromIntent(intent: Intent?): Boolean {
            return PlaybackLaunchRequest.shouldOpenPlaylist(intent)
        }

        fun shouldOpenPlayerFromIntent(intent: Intent?): Boolean {
            return PlaybackLaunchRequest.shouldOpenPlayer(intent)
        }

        fun shouldStartPlaybackFromIntent(intent: Intent?): Boolean {
            return PlaybackLaunchRequest.shouldStartPlayback(intent)
        }
    }
}

internal enum class PlaylistSheetLaunchAction {
    NONE,
    OPEN,
    CLOSE
}

internal fun resolvePlaylistSheetLaunchAction(
    hasOpenPlayerLaunchRequest: Boolean,
    hasOpenPlaylistLaunchRequest: Boolean,
    isPlaylistSheetVisible: Boolean
): PlaylistSheetLaunchAction {
    if (!hasOpenPlayerLaunchRequest) {
        return PlaylistSheetLaunchAction.NONE
    }
    return when {
        hasOpenPlaylistLaunchRequest && !isPlaylistSheetVisible -> PlaylistSheetLaunchAction.OPEN
        !hasOpenPlaylistLaunchRequest && isPlaylistSheetVisible -> PlaylistSheetLaunchAction.CLOSE
        else -> PlaylistSheetLaunchAction.NONE
    }
}
