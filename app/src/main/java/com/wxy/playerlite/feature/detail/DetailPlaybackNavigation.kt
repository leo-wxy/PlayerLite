package com.wxy.playerlite.feature.detail

import android.content.Context
import android.content.Intent
import com.wxy.playerlite.feature.player.PlayerActivity

internal fun createOpenPlayerIntent(context: Context): Intent {
    return PlayerActivity.createIntent(context = context)
        .apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
}

internal fun createOpenPlayerAfterQueueReplacementIntent(context: Context): Intent {
    return PlayerActivity.createIntent(
        context = context,
        startPlayback = true
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
