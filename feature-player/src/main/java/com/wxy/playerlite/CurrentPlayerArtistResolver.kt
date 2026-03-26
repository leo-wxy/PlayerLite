package com.wxy.playerlite

import com.wxy.playerlite.feature.player.model.PlayerUiState

fun resolveCurrentPlayerArtistId(state: PlayerUiState): String? {
    return state.currentArtistId
        ?.takeIf { it.isNotBlank() }
        ?: state.playlistItems
            .getOrNull(state.activePlaylistIndex)
            ?.primaryArtistId
            ?.takeIf { it.isNotBlank() }
}
