package com.wxy.playerlite.playback.model

import android.content.Context
import android.content.Intent

object PlaybackLaunchRequest {
    private const val EXTRA_OPEN_PLAYER =
        "com.wxy.playerlite.playback.extra.OPEN_PLAYER"
    private const val EXTRA_START_PLAYBACK =
        "com.wxy.playerlite.playback.extra.START_PLAYBACK"
    private const val EXTRA_OPEN_PLAYLIST =
        "com.wxy.playerlite.playback.extra.OPEN_PLAYLIST"

    fun createPlayerActivityIntent(
        context: Context,
        openPlaylist: Boolean = false,
        startPlayback: Boolean = false
    ): Intent {
        return Intent()
            .setClassName(
                context.packageName,
                "${context.packageName}.feature.player.PlayerActivity"
            )
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply {
                putExtra(EXTRA_OPEN_PLAYER, true)
                putExtra(EXTRA_START_PLAYBACK, startPlayback)
                putExtra(EXTRA_OPEN_PLAYLIST, openPlaylist)
            }
    }

    fun shouldOpenPlayer(intent: Intent?): Boolean {
        return intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true
    }

    fun shouldStartPlayback(intent: Intent?): Boolean {
        return intent?.getBooleanExtra(EXTRA_START_PLAYBACK, false) == true
    }

    fun shouldOpenPlaylist(intent: Intent?): Boolean {
        return intent?.getBooleanExtra(EXTRA_OPEN_PLAYLIST, false) == true
    }
}
