package com.wxy.playerlite.feature.detail

import android.content.Context
import android.content.Intent
import com.wxy.playerlite.MainActivity

internal fun createOpenPlayerAfterQueueReplacementIntent(context: Context): Intent {
    return MainActivity.createIntent(
        context = context,
        openPlayer = true,
        startPlayback = true
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
