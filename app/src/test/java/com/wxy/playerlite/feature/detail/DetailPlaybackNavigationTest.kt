package com.wxy.playerlite.feature.detail

import android.content.Context
import android.content.Intent
import com.wxy.playerlite.feature.player.PlayerActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DetailPlaybackNavigationTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun createOpenPlayerIntent_shouldTargetPlayerActivityAndClearToExistingTask() {
        val intent = createOpenPlayerIntent(context)

        assertEquals(PlayerActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun createOpenPlayerAfterQueueReplacementIntent_shouldOpenExpandedPlayerAndClearToMain() {
        val intent = createOpenPlayerAfterQueueReplacementIntent(context)

        assertEquals(PlayerActivity::class.java.name, intent.component?.className)
        assertTrue(PlayerActivity.shouldStartPlaybackFromIntent(intent))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }
}
