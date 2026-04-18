package com.wxy.playerlite.feature.main

import android.content.Context
import com.wxy.playerlite.feature.player.PlayerActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RecentSongsActivityIntentTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun createRecentPlaybackPlayerIntent_shouldAlwaysStartPlayback() {
        val intent = createRecentPlaybackPlayerIntent(context)

        assertTrue(PlayerActivity.shouldStartPlaybackFromIntent(intent))
        assertFalse(PlayerActivity.shouldOpenPlaylistFromIntent(intent))
    }
}
