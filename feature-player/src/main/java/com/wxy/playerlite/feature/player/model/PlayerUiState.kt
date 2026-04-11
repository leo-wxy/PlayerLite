package com.wxy.playerlite.feature.player.model

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.feature.player.ParsedLyrics
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.playback.model.SongAudioQualityCatalog
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.PlaybackSpeed
import com.wxy.playerlite.player.PlaybackOutputInfo

sealed interface PlayerLyricUiState {
    data object Placeholder : PlayerLyricUiState
    data object Loading : PlayerLyricUiState

    data class Content(
        val lyrics: ParsedLyrics
    ) : PlayerLyricUiState

    data class Empty(
        val message: String
    ) : PlayerLyricUiState

    data class Error(
        val message: String
    ) : PlayerLyricUiState
}

enum class PlayerTopTab {
    SONG,
    LYRICS
}

enum class PlayerOrientationMode {
    AUTO,
    LANDSCAPE_LOCKED,
    PORTRAIT_LOCKED
}

fun resolvePlayerOrientationToggleTarget(
    currentMode: PlayerOrientationMode,
    isCurrentlyLandscape: Boolean
): PlayerOrientationMode {
    return when (currentMode) {
        PlayerOrientationMode.AUTO -> {
            if (isCurrentlyLandscape) {
                PlayerOrientationMode.PORTRAIT_LOCKED
            } else {
                PlayerOrientationMode.LANDSCAPE_LOCKED
            }
        }

        PlayerOrientationMode.LANDSCAPE_LOCKED -> PlayerOrientationMode.PORTRAIT_LOCKED
        PlayerOrientationMode.PORTRAIT_LOCKED -> PlayerOrientationMode.LANDSCAPE_LOCKED
    }
}

enum class PlayerMoreActionsPage {
    ROOT,
    SPEED,
    AUDIO_EFFECT
}

sealed interface PlayerAudioQualityCatalogUiState {
    data object Placeholder : PlayerAudioQualityCatalogUiState
    data object Loading : PlayerAudioQualityCatalogUiState

    data class Content(
        val catalog: SongAudioQualityCatalog
    ) : PlayerAudioQualityCatalogUiState

    data class Empty(
        val message: String
    ) : PlayerAudioQualityCatalogUiState

    data class Unsupported(
        val message: String
    ) : PlayerAudioQualityCatalogUiState
}

data class PlayerCombinedStatusUi(
    val audioQualityLabel: String?,
    val audioEffectLabel: String?
)

data class PlayerUiState(
    val selectedFileName: String = "No audio selected",
    val currentTrackTitle: String = "No audio selected",
    val currentTrackArtist: String? = null,
    val currentArtistId: String? = null,
    val currentCoverUrl: String? = null,
    val currentSongIdOverride: String? = null,
    val statusText: String = "Pick a local audio file, then tap Play",
    val audioMeta: AudioMetaDisplay = emptyAudioMeta(),
    val playbackOutputInfo: PlaybackOutputInfo? = null,
    val hasSelection: Boolean = false,
    val playlistItems: List<PlaylistItem> = emptyList(),
    val activePlaylistIndex: Int = -1,
    val showPlaylistSheet: Boolean = false,
    val showMoreActionsSheet: Boolean = false,
    val showAudioEffectPage: Boolean = false,
    val showAudioQualitySheet: Boolean = false,
    val moreActionsPage: PlayerMoreActionsPage = PlayerMoreActionsPage.ROOT,
    val lyricUiState: PlayerLyricUiState = PlayerLyricUiState.Placeholder,
    val audioQualityCatalogUiState: PlayerAudioQualityCatalogUiState = PlayerAudioQualityCatalogUiState.Placeholder,
    val selectedTopTab: PlayerTopTab = PlayerTopTab.SONG,
    val orientationMode: PlayerOrientationMode = PlayerOrientationMode.AUTO,
    val isPreparing: Boolean = false,
    val playbackState: Int = AUDIO_TRACK_PLAYSTATE_UNAVAILABLE,
    val isSeekSupported: Boolean = false,
    val playbackSpeed: Float = PlaybackSpeed.DEFAULT.value,
    val playbackMode: PlaybackMode = PlaybackMode.LIST_LOOP,
    val preferredAudioQuality: PlaybackAudioQuality = PlaybackAudioQuality.EXHIGH,
    val appliedAudioQuality: PlaybackAudioQuality? = null,
    val audioEffectPreset: AudioEffectPreset = AudioEffectPreset.DEFAULT,
    val showOriginalOrderInShuffle: Boolean = false,
    val canReorderPlaylist: Boolean = true,
    val durationMs: Long = 0L,
    val seekPositionMs: Long = 0L,
    val seekDragPositionMs: Long = 0L,
    val isSeekDragging: Boolean = false
) {
    val displayedSeekMs: Long
        get() = if (isSeekDragging) seekDragPositionMs else seekPositionMs

    val currentAudioEffectDisplayName: String
        get() = audioEffectPreset.displayName

    val currentPreferredAudioQualityDisplayName: String
        get() = preferredAudioQuality.displayName

    val currentAppliedAudioQualityDisplayName: String?
        get() = appliedAudioQuality?.displayName

    val combinedStatusUi: PlayerCombinedStatusUi?
        get() {
            val audioQualityLabel = currentAppliedAudioQualityDisplayName
            val audioEffectLabel = audioEffectPreset
                .takeIf { it != AudioEffectPreset.DEFAULT }
                ?.displayName
            return if (audioQualityLabel == null && audioEffectLabel == null) {
                null
            } else {
                PlayerCombinedStatusUi(
                    audioQualityLabel = audioQualityLabel,
                    audioEffectLabel = audioEffectLabel
                )
            }
        }

    val canSkipPrevious: Boolean
        get() = playlistItems.size > 1

    val canSkipNext: Boolean
        get() = playlistItems.size > 1

    val currentSongId: String?
        get() = currentSongIdOverride?.takeIf { it.isNotBlank() }
            ?: playlistItems
                .getOrNull(activePlaylistIndex)
                ?.songId
                ?.takeIf { it.isNotBlank() }
}

fun emptyAudioMeta(): AudioMetaDisplay {
    return AudioMetaDisplay(
        codec = "-",
        sampleRate = "-",
        channels = "-",
        bitRate = "-",
        durationMs = 0L
    )
}
