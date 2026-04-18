package com.wxy.playerlite.feature.detail

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.player.PlayerViewModel
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PAUSED
import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_PLAYING
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.song.createAlbumDetailIntent
import com.wxy.playerlite.feature.song.createArtistDetailIntent
import com.wxy.playerlite.feature.song.createSongDetailIntent
import com.wxy.playerlite.feature.player.ui.components.PlaylistBottomSheet
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

abstract class BasePlaybackDetailActivity : ComponentActivity() {
    private val playerViewModel: PlayerViewModel by viewModels()

    internal fun setPlaybackDetailContent(
        content: @Composable (bottomOverlayPadding: Dp) -> Unit
    ) {
        enableEdgeToEdge()
        setContent {
            PlayerLiteTheme {
                val playerState = playerViewModel.uiStateFlow.collectAsStateWithLifecycle().value
                val navigationBottomPadding =
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val hasMiniPlayer = playerState.hasSelection
                val bottomOverlayPadding = if (hasMiniPlayer) {
                    DetailMiniPlayerContentPadding + navigationBottomPadding
                } else {
                    0.dp
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    content(bottomOverlayPadding)
                    PlaybackDetailChrome(
                        playerState = playerState,
                        bottomPadding = DetailMiniPlayerBottomPadding + navigationBottomPadding,
                        onOpenPlayer = ::openPlayerFromDetail,
                        onTogglePlayback = {
                            togglePlayback(playerState)
                        },
                        onOpenPlaylist = playerViewModel::onTogglePlaylistSheet,
                        onDismissPlaylist = playerViewModel::onDismissPlaylistSheet,
                        onCyclePlaybackMode = playerViewModel::cyclePlaybackMode,
                        onShowOriginalOrderInShuffleChange = playerViewModel::setShowOriginalOrderInShuffle,
                        onSelectPlaylistItem = playerViewModel::selectPlaylistItem,
                        onClearPlaylist = playerViewModel::clearPlaylist,
                        onRemovePlaylistItem = playerViewModel::removePlaylistItem,
                        onOpenQueueSongDetail = ::openQueueSongDetail,
                        onOpenQueueArtist = ::openQueueArtist,
                        onOpenQueueAlbum = ::openQueueAlbum,
                        onMovePlaylistItem = playerViewModel::movePlaylistItem,
                        onSkipPrevious = playerViewModel::skipToPreviousTrack,
                        onSkipNext = playerViewModel::skipToNextTrack
                    )
                }
            }
        }
    }

    private fun togglePlayback(playerState: PlayerUiState) {
        when (playerState.playbackState) {
            AUDIO_TRACK_PLAYSTATE_PLAYING -> playerViewModel.pausePlayback()
            AUDIO_TRACK_PLAYSTATE_PAUSED -> playerViewModel.resumePlayback()
            else -> playerViewModel.playSelectedAudio()
        }
    }

    private fun openPlayerFromDetail() {
        startActivity(createOpenPlayerIntent(this))
    }

    protected fun openPlayerAfterQueueReplacement() {
        playerViewModel.playSelectedAudio()
        startActivity(createOpenPlayerIntent(this))
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
    }

    private fun openQueueAlbum(albumId: String) {
        PlaylistItem(
            id = "album:$albumId",
            displayName = "",
            albumId = albumId
        ).createAlbumDetailIntent(this)?.let(::startActivity)
    }
}

@Composable
internal fun PlaybackDetailChrome(
    playerState: PlayerUiState,
    bottomPadding: Dp,
    onOpenPlayer: () -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onDismissPlaylist: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onShowOriginalOrderInShuffleChange: (Boolean) -> Unit,
    onSelectPlaylistItem: (Int) -> Unit,
    onClearPlaylist: () -> Unit,
    onRemovePlaylistItem: (Int) -> Unit,
    onOpenQueueSongDetail: (PlaylistItem) -> Unit = {},
    onOpenQueueArtist: (String) -> Unit = {},
    onOpenQueueAlbum: (String) -> Unit = {},
    onMovePlaylistItem: (Int, Int) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (playerState.hasSelection) {
            DetailMiniPlayerHost(bottomPadding = bottomPadding) { hostModifier ->
                DetailMiniPlayerBar(
                    playerState = playerState,
                    onOpenPlayer = onOpenPlayer,
                    onTogglePlayback = onTogglePlayback,
                    onOpenPlaylist = onOpenPlaylist,
                    onSkipPrevious = onSkipPrevious,
                    onSkipNext = onSkipNext,
                    modifier = hostModifier
                )
            }
        }
        PlaylistBottomSheet(
            visible = playerState.showPlaylistSheet,
            items = playerState.playlistItems,
            activeIndex = playerState.activePlaylistIndex,
            playbackMode = playerState.playbackMode,
            showOriginalOrderInShuffle = playerState.showOriginalOrderInShuffle,
            canReorder = playerState.canReorderPlaylist,
            onDismiss = onDismissPlaylist,
            onCyclePlaybackMode = onCyclePlaybackMode,
            onShowOriginalOrderInShuffleChange = onShowOriginalOrderInShuffleChange,
            onSelect = onSelectPlaylistItem,
            onClearAll = onClearPlaylist,
            onRemove = onRemovePlaylistItem,
            onOpenSongDetail = onOpenQueueSongDetail,
            onOpenArtist = onOpenQueueArtist,
            onOpenAlbum = onOpenQueueAlbum,
            onMove = onMovePlaylistItem
        )
    }
}
