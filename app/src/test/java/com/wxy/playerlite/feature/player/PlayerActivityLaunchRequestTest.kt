package com.wxy.playerlite.feature.player

import android.content.Context
import android.content.pm.ActivityInfo
import com.wxy.playerlite.feature.player.model.PlayerOrientationMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayerActivityLaunchRequestTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun createIntentWithOpenPlaylistFlag_shouldMarkPlaylistLaunchRequest() {
        val intent = PlayerActivity.createIntent(
            context = context,
            openPlaylist = true
        )

        assertTrue(PlayerActivity.shouldOpenPlaylistFromIntent(intent))
        assertFalse(PlayerActivity.shouldStartPlaybackFromIntent(intent))
    }

    @Test
    fun createIntentWithStartPlaybackFlag_shouldMarkStartPlaybackLaunchRequest() {
        val intent = PlayerActivity.createIntent(
            context = context,
            startPlayback = true
        )

        assertTrue(PlayerActivity.shouldStartPlaybackFromIntent(intent))
        assertFalse(PlayerActivity.shouldOpenPlaylistFromIntent(intent))
    }

    @Test
    fun resolvePlaylistSheetLaunchAction_plainPlayerLaunch_shouldCloseExistingSheet() {
        val action = resolvePlaylistSheetLaunchAction(
            hasOpenPlayerLaunchRequest = true,
            hasOpenPlaylistLaunchRequest = false,
            isPlaylistSheetVisible = true
        )

        assertEquals(PlaylistSheetLaunchAction.CLOSE, action)
    }

    @Test
    fun resolvePlaylistSheetLaunchAction_playlistLaunch_shouldOpenSheetWhenHidden() {
        val action = resolvePlaylistSheetLaunchAction(
            hasOpenPlayerLaunchRequest = true,
            hasOpenPlaylistLaunchRequest = true,
            isPlaylistSheetVisible = false
        )

        assertEquals(PlaylistSheetLaunchAction.OPEN, action)
    }

    @Test
    fun resolvePlaylistSheetLaunchAction_plainPlayerLaunch_shouldNotReopenSheetWhenHidden() {
        val action = resolvePlaylistSheetLaunchAction(
            hasOpenPlayerLaunchRequest = true,
            hasOpenPlaylistLaunchRequest = false,
            isPlaylistSheetVisible = false
        )

        assertEquals(PlaylistSheetLaunchAction.NONE, action)
    }

    @Test
    fun resolvePlayerRequestedOrientation_shouldMapEveryOrientationMode() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            resolvePlayerRequestedOrientation(PlayerOrientationMode.AUTO)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            resolvePlayerRequestedOrientation(PlayerOrientationMode.LANDSCAPE_LOCKED)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            resolvePlayerRequestedOrientation(PlayerOrientationMode.PORTRAIT_LOCKED)
        )
    }
}
