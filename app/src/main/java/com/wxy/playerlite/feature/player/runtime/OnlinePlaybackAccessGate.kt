package com.wxy.playerlite.feature.player.runtime

import android.net.Uri

internal data class PlaybackAccessRequest(
    val playbackUri: String,
    val requiresLogin: Boolean
)

internal enum class PlaybackAccessDecision {
    Allowed,
    RequiresLogin
}

internal class OnlinePlaybackAccessGate(
    private val isLoggedIn: () -> Boolean
) {
    fun evaluate(request: PlaybackAccessRequest): PlaybackAccessDecision {
        val uri = runCatching { Uri.parse(request.playbackUri) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (scheme == "file" || scheme == "content") {
            return PlaybackAccessDecision.Allowed
        }
        if (!request.requiresLogin) {
            return PlaybackAccessDecision.Allowed
        }
        return if (isLoggedIn()) {
            PlaybackAccessDecision.Allowed
        } else {
            PlaybackAccessDecision.RequiresLogin
        }
    }
}
