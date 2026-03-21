package com.wxy.playerlite

import android.content.Context
import android.content.Intent
import com.wxy.playerlite.feature.player.PlayerActivity
import com.wxy.playerlite.playback.model.PlaybackLaunchRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MainActivityLaunchRequestTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun resolveLegacyPlayerLaunchRedirectIntent_withoutPlaybackLaunchExtras_shouldReturnNull() {
        val redirectIntent = resolveLegacyPlayerLaunchRedirectIntent(
            sourceIntent = Intent(context, MainActivity::class.java),
            context = context
        )

        assertNull(redirectIntent)
    }

    @Test
    fun resolveLegacyPlayerLaunchRedirectIntent_withStartPlaybackFlag_shouldTargetPlayerActivity() {
        val sourceIntent = PlaybackLaunchRequest.createPlayerActivityIntent(
            context = context,
            startPlayback = true
        )

        val redirectIntent = resolveLegacyPlayerLaunchRedirectIntent(
            sourceIntent = sourceIntent,
            context = context
        )

        assertNotNull(redirectIntent)
        assertEquals(PlayerActivity::class.java.name, redirectIntent?.component?.className)
        assertTrue(PlayerActivity.shouldStartPlaybackFromIntent(redirectIntent))
        assertFalse(PlayerActivity.shouldOpenPlaylistFromIntent(redirectIntent))
    }

    @Test
    fun resolveLegacyPlayerLaunchRedirectIntent_withPlaylistFlag_shouldPreservePlaylistLaunch() {
        val sourceIntent = PlaybackLaunchRequest.createPlayerActivityIntent(
            context = context,
            openPlaylist = true
        )

        val redirectIntent = resolveLegacyPlayerLaunchRedirectIntent(
            sourceIntent = sourceIntent,
            context = context
        )

        assertNotNull(redirectIntent)
        assertEquals(PlayerActivity::class.java.name, redirectIntent?.component?.className)
        assertTrue(PlayerActivity.shouldOpenPlayerFromIntent(redirectIntent))
        assertTrue(PlayerActivity.shouldOpenPlaylistFromIntent(redirectIntent))
        assertFalse(PlayerActivity.shouldStartPlaybackFromIntent(redirectIntent))
    }
}
