package com.wxy.playerlite.feature.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.artist.ArtistDetailActivity
import com.wxy.playerlite.feature.player.model.PlayerOrientationMode
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.ui.PlayerScreenCallbacks
import com.wxy.playerlite.feature.player.ui.PlayerScreen
import com.wxy.playerlite.feature.song.SongDetailActivity
import com.wxy.playerlite.feature.song.SongRef
import com.wxy.playerlite.feature.song.createAlbumDetailIntent
import com.wxy.playerlite.feature.song.createArtistDetailIntent
import com.wxy.playerlite.feature.song.createSongDetailIntent
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
            LaunchedEffect(state.orientationMode) {
                requestedOrientation = resolvePlayerRequestedOrientation(state.orientationMode)
            }
            PlayerLiteTheme {
                val screenCallbacks = PlayerScreenCallbacks(
                    onPickAudio = {
                        pickAudioLauncher.launch(arrayOf("audio/*"))
                    },
                    onTogglePlaylistSheet = viewModel::onTogglePlaylistSheet,
                    onDismissPlaylistSheet = viewModel::onDismissPlaylistSheet,
                    onRetryLyrics = viewModel::onRetryLyrics,
                    onSelectTopTab = viewModel::onSelectTopTab,
                    onCycleOrientationMode = viewModel::setPlayerOrientationMode,
                    onSelectPlaylistItem = viewModel::selectPlaylistItem,
                    onClearPlaylist = viewModel::clearPlaylist,
                    onRemovePlaylistItem = viewModel::removePlaylistItem,
                    onOpenQueueSongDetail = ::openQueueSongDetail,
                    onOpenQueueArtist = ::openQueueArtist,
                    onOpenQueueAlbum = ::openQueueAlbum,
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
                    onDismissAudioQualitySheet = viewModel::dismissAudioQualitySettings,
                    onShowPlaybackSpeedSettings = viewModel::showPlaybackSpeedSettings,
                    onShowAudioEffectSettings = viewModel::showAudioEffectSettings,
                    onShowAudioQualitySettings = viewModel::showAudioQualitySettings,
                    onReturnToMoreActionsRoot = viewModel::returnToPlayerMoreActionsRoot,
                    onSelectPlaybackSpeed = viewModel::updatePlaybackSpeed,
                    onSelectAudioQuality = viewModel::updatePreferredAudioQuality,
                    onSelectAudioEffectPreset = viewModel::updateAudioEffectPreset,
                    onBackClick = ::finish,
                    onOpenSongDetail = {
                        resolveCurrentSongRef(state)?.let { ref ->
                            startActivity(
                                SongDetailActivity.createIntent(
                                    context = this@PlayerActivity,
                                    ref = ref
                                )
                            )
                        }
                    },
                    onShareClick = {
                        val shareText = resolveCurrentSongShareText(state)
                        if (shareText == null) {
                            Toast.makeText(
                                this@PlayerActivity,
                                "当前歌曲暂不支持分享",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, shareText),
                                    "分享歌曲"
                                )
                            )
                        }
                    },
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
                    canOpenSongDetail = resolveCurrentSongRef(state) != null,
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

    private fun openQueueSongDetail(item: PlaylistItem) {
        val intent = item.createSongDetailIntent(this)
        if (intent == null) {
            Toast.makeText(this, "当前歌曲详情暂时无法打开", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(intent)
    }

    private fun openQueueArtist(artistId: String) {
        PlaylistItem(
            id = "artist:$artistId",
            displayName = "",
            primaryArtistId = artistId
        ).createArtistDetailIntent(this)?.let(::startActivity)
            ?: Toast.makeText(this, "当前歌手暂时无法打开", Toast.LENGTH_SHORT).show()
    }

    private fun openQueueAlbum(albumId: String) {
        PlaylistItem(
            id = "album:$albumId",
            displayName = "",
            albumId = albumId
        ).createAlbumDetailIntent(this)?.let(::startActivity)
            ?: Toast.makeText(this, "当前专辑暂时无法打开", Toast.LENGTH_SHORT).show()
    }
}

internal fun resolvePlayerRequestedOrientation(
    orientationMode: PlayerOrientationMode
): Int {
    return when (orientationMode) {
        PlayerOrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        PlayerOrientationMode.LANDSCAPE_LOCKED -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        PlayerOrientationMode.PORTRAIT_LOCKED -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
}

private fun resolveCurrentSongRef(state: PlayerUiState): SongRef? {
    val activeItem = state.playlistItems.getOrNull(state.activePlaylistIndex) ?: return null
    val onlineSongId = state.currentSongId?.takeIf { it.isNotBlank() } ?: activeItem.songId
    if (!onlineSongId.isNullOrBlank()) {
        return SongRef.Online(songId = onlineSongId)
    }
    val playbackUri = activeItem.uri.takeIf { it.isNotBlank() } ?: return null
    return SongRef.Local(
        playbackUri = playbackUri,
        title = activeItem.title.ifBlank { state.currentTrackTitle },
        artistText = activeItem.artistText ?: state.currentTrackArtist.orEmpty(),
        albumTitle = activeItem.albumTitle,
        durationMs = activeItem.durationMs,
        coverUrl = activeItem.coverUrl
    )
}

private fun resolveCurrentSongShareText(state: PlayerUiState): String? {
    val ref = resolveCurrentSongRef(state) ?: return null
    val activeItem = state.playlistItems.getOrNull(state.activePlaylistIndex)
    val title = activeItem?.title?.takeIf { it.isNotBlank() }
        ?: state.currentTrackTitle.takeIf { it.isNotBlank() }
        ?: return null
    val artist = activeItem?.artistText?.takeIf { it.isNotBlank() }
        ?: state.currentTrackArtist?.takeIf { it.isNotBlank() }
    return buildString {
        append(title)
        artist?.let {
            append(" - ")
            append(it)
        }
        if (ref is SongRef.Online) {
            append("\nhttps://music.163.com/#/song?id=")
            append(ref.songId)
        }
    }
}
