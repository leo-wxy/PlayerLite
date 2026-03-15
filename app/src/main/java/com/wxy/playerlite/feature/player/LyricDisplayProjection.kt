package com.wxy.playerlite.feature.player

import com.wxy.playerlite.feature.player.model.PlayerLyricUiState

internal data class ActiveLyricLineProjection(
    val activeLineIndex: Int,
    val activeLineText: String?
)

internal data class PlayerDisplayMetadataProjection(
    val title: String,
    val subtitle: String,
    val activeLineIndex: Int,
    val activeLineText: String?
)

internal fun resolvePlayerDisplayMetadataProjection(
    baseTitle: String?,
    baseArtist: String?,
    lyricUiState: PlayerLyricUiState,
    currentPositionMs: Long,
    emptyTitle: String = "未选择音频",
    emptySubtitle: String = "点击进入播放页"
): PlayerDisplayMetadataProjection {
    val normalizedTitle = baseTitle?.takeIf { it.isNotBlank() }
    val normalizedArtist = baseArtist?.takeIf { it.isNotBlank() }
    val activeLyric = resolveActiveLyricLineProjection(
        lyricUiState = lyricUiState,
        currentPositionMs = currentPositionMs
    )
    val displayTitle = activeLyric.activeLineText ?: normalizedTitle ?: emptyTitle
    val displaySubtitle = buildSongArtistDisplayText(
        title = normalizedTitle,
        artist = normalizedArtist,
        emptySubtitle = emptySubtitle
    )
    return PlayerDisplayMetadataProjection(
        title = displayTitle,
        subtitle = displaySubtitle,
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
