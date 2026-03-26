package com.wxy.playerlite.feature.player

import android.content.Context
import android.content.Intent
import com.wxy.playerlite.playback.model.PlaybackLaunchRequest

object PlayerEntry {
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

enum class PlaylistSheetLaunchAction {
    NONE,
    OPEN,
    CLOSE
}

fun resolvePlaylistSheetLaunchAction(
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
