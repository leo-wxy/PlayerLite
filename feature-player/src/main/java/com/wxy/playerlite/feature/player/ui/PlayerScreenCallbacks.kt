package com.wxy.playerlite.feature.player.ui

import com.wxy.playerlite.feature.player.model.PlayerOrientationMode
import com.wxy.playerlite.feature.player.model.PlayerTopTab
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.player.AudioEffectPreset

class PlayerScreenCallbacks(
    val onPickAudio: () -> Unit,
    val onTogglePlaylistSheet: () -> Unit,
    val onDismissPlaylistSheet: () -> Unit,
    val onShowSongWiki: () -> Unit,
    val onDismissSongWiki: () -> Unit,
    val onRetrySongWiki: () -> Unit,
    val onRetryLyrics: () -> Unit = {},
    val onSelectTopTab: ((PlayerTopTab) -> Unit)? = null,
    val onCycleOrientationMode: (PlayerOrientationMode) -> Unit = {},
    val onSelectPlaylistItem: (Int) -> Unit,
    val onClearPlaylist: () -> Unit = {},
    val onRemovePlaylistItem: (Int) -> Unit,
    val onMovePlaylistItem: (Int, Int) -> Unit,
    val onPlay: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onCyclePlaybackMode: () -> Unit,
    val onShowOriginalOrderInShuffleChange: (Boolean) -> Unit,
    val onSeekValueChange: (Long) -> Unit,
    val onSeekFinished: () -> Unit,
    val onDismissMoreActionsSheet: () -> Unit = {},
    val onDismissAudioEffectPage: () -> Unit = {},
    val onDismissAudioQualitySheet: () -> Unit = {},
    val onShowPlaybackSpeedSettings: () -> Unit = {},
    val onShowAudioEffectSettings: () -> Unit = {},
    val onShowAudioQualitySettings: () -> Unit = {},
    val onReturnToMoreActionsRoot: () -> Unit = {},
    val onSelectPlaybackSpeed: (Float) -> Unit = {},
    val onSelectAudioQuality: (PlaybackAudioQuality) -> Unit = {},
    val onSelectAudioEffectPreset: (AudioEffectPreset) -> Unit = {},
    val onBackClick: () -> Unit = {},
    val onShareClick: () -> Unit = {},
    val onArtistClick: () -> Unit = {},
    val onFavoriteClick: () -> Unit = {},
    val onMoreClick: () -> Unit = {}
)
