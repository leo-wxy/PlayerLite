package com.wxy.playerlite.feature.detail

import android.content.Context
import android.content.Intent
import com.wxy.playerlite.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DetailPlaybackNavigationTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun createOpenPlayerAfterQueueReplacementIntent_shouldOpenExpandedPlayerAndClearToMain() {
        val intent = createOpenPlayerAfterQueueReplacementIntent(context)

        assertTrue(MainActivity.shouldOpenPlayerFromIntent(intent))
        assertTrue(MainActivity.shouldStartPlaybackFromIntent(intent))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }
}
