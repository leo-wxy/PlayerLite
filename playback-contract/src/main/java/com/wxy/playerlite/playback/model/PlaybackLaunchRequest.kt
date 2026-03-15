package com.wxy.playerlite.playback.model

import android.content.Context
import android.content.Intent

object PlaybackLaunchRequest {
    private const val EXTRA_OPEN_PLAYER =
        "com.wxy.playerlite.playback.extra.OPEN_PLAYER"
    private const val EXTRA_START_PLAYBACK =
        "com.wxy.playerlite.playback.extra.START_PLAYBACK"

    fun createMainActivityIntent(
        context: Context,
        openPlayer: Boolean = false,
        startPlayback: Boolean = false
    ): Intent {
        val shouldOpenPlayer = openPlayer || startPlayback
        return Intent()
            .setClassName(context.packageName, "${context.packageName}.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply {
                putExtra(EXTRA_OPEN_PLAYER, shouldOpenPlayer)
                putExtra(EXTRA_START_PLAYBACK, startPlayback)
            }
    }

    fun shouldOpenPlayer(intent: Intent?): Boolean {
        return intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true
    }

    fun shouldStartPlayback(intent: Intent?): Boolean {
        return intent?.getBooleanExtra(EXTRA_START_PLAYBACK, false) == true
    }
}
