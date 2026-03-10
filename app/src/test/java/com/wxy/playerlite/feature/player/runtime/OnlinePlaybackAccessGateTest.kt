package com.wxy.playerlite.feature.player.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlinePlaybackAccessGateTest {
    @Test
    fun evaluate_shouldRequireLoginForProtectedOnlineRequestWhenLoggedOut() {
        val gate = OnlinePlaybackAccessGate(
            isLoggedIn = { false }
        )

        val decision = gate.evaluate(
            PlaybackAccessRequest(
                playbackUri = "https://example.com/protected.mp3",
                requiresLogin = true
            )
        )

        assertEquals(PlaybackAccessDecision.RequiresLogin, decision)
    }

    @Test
    fun evaluate_shouldAllowLocalContentWithoutLogin() {
        val gate = OnlinePlaybackAccessGate(
            isLoggedIn = { false }
        )

        val decision = gate.evaluate(
            PlaybackAccessRequest(
                playbackUri = "content://media/external/audio/media/1",
                requiresLogin = false
            )
        )

        assertEquals(PlaybackAccessDecision.Allowed, decision)
    }

    @Test
    fun evaluate_shouldAllowAnonymousHttpWithoutLogin() {
        val gate = OnlinePlaybackAccessGate(
            isLoggedIn = { false }
        )

        val decision = gate.evaluate(
            PlaybackAccessRequest(
                playbackUri = "https://example.com/public.mp3",
                requiresLogin = false
            )
        )

        assertEquals(PlaybackAccessDecision.Allowed, decision)
    }
}
