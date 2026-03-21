package com.wxy.playerlite.feature.player

import com.wxy.playerlite.feature.player.model.PlayerLyricUiState
import com.wxy.playerlite.feature.player.model.PlayerUiState
import com.wxy.playerlite.feature.player.ui.resolvePlayerTrackText

internal data class ActiveLyricLineProjection(
    val activeLineIndex: Int,
    val activeLineText: String?
)

internal data class PlayerDisplayContentProjection(
    val resolvedTitle: String?,
    val resolvedArtist: String?,
    val songArtistLine: String,
    val miniPlayerContentLine: String,
    val displayMetadataTitle: String,
    val activeLineIndex: Int,
    val activeLineText: String?
)

internal fun resolvePlayerDisplayContentProjection(
    playerState: PlayerUiState,
    emptyTitle: String = "未选择音频",
    emptySubtitle: String = "点击进入播放页"
): PlayerDisplayContentProjection {
    val fallbackTrackText = playerState.selectedFileName
        .takeIf { it.isNotBlank() && it != "No audio selected" }
        ?.let(::resolvePlayerTrackText)
    val resolvedTitle = playerState.currentTrackTitle
        .takeIf { it.isNotBlank() && it != "No audio selected" }
        ?: fallbackTrackText?.title
    val resolvedArtist = playerState.currentTrackArtist
        ?.takeIf { it.isNotBlank() }
        ?: playerState.playlistItems
            .getOrNull(playerState.activePlaylistIndex)
            ?.artistText
            ?.takeIf { it.isNotBlank() }
        ?: fallbackTrackText?.artist
    val activeLyric = resolveActiveLyricLineProjection(
        lyricUiState = playerState.lyricUiState,
        currentPositionMs = playerState.displayedSeekMs
    )
    val songArtistLine = buildSongArtistDisplayText(
        title = resolvedTitle,
        artist = resolvedArtist,
        emptySubtitle = emptySubtitle
    )
    return PlayerDisplayContentProjection(
        resolvedTitle = resolvedTitle,
        resolvedArtist = resolvedArtist,
        songArtistLine = songArtistLine,
        miniPlayerContentLine = activeLyric.activeLineText ?: songArtistLine,
        displayMetadataTitle = activeLyric.activeLineText ?: resolvedTitle ?: emptyTitle,
        activeLineIndex = activeLyric.activeLineIndex,
        activeLineText = activeLyric.activeLineText
    )
}

internal fun resolveActiveLyricLineProjection(
    lyricUiState: PlayerLyricUiState,
    currentPositionMs: Long
): ActiveLyricLineProjection {
    if (lyricUiState !is PlayerLyricUiState.Content) {
        return ActiveLyricLineProjection(
            activeLineIndex = -1,
            activeLineText = null
        )
    }
    val activeLineIndex = resolveActiveLyricLineIndex(
        lines = lyricUiState.lyrics.lines,
        currentPositionMs = currentPositionMs
    )
    val activeLineText = lyricUiState.lyrics.lines
        .getOrNull(activeLineIndex)
        ?.text
        ?.takeIf { it.isNotBlank() }
    return ActiveLyricLineProjection(
        activeLineIndex = activeLineIndex,
        activeLineText = activeLineText
    )
}

internal fun buildSongArtistDisplayText(
    title: String?,
    artist: String?,
    emptySubtitle: String = "点击进入播放页"
): String {
    val normalizedTitle = title?.takeIf { it.isNotBlank() }
    val normalizedArtist = artist?.takeIf { it.isNotBlank() }
    return when {
        normalizedTitle != null && normalizedArtist != null -> {
            "$normalizedTitle - $normalizedArtist"
        }

        normalizedTitle != null -> normalizedTitle
        normalizedArtist != null -> normalizedArtist
        else -> emptySubtitle
    }
}

internal fun resolveActiveLyricLineIndex(
    lines: List<LyricLine>,
    currentPositionMs: Long
): Int {
    if (lines.isEmpty()) {
        return -1
    }
    for (index in lines.lastIndex downTo 0) {
        if (currentPositionMs >= lines[index].timestampMs) {
            return index
        }
    }
    return 0
}
