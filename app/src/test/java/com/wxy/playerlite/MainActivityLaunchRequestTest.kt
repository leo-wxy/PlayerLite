package com.wxy.playerlite

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MainActivityLaunchRequestTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun createIntentWithOpenPlayerFlag_shouldMarkLaunchRequest() {
        val intent = MainActivity.createIntent(
            context = context,
            openPlayer = true
        )

        assertTrue(MainActivity.shouldOpenPlayerFromIntent(intent))
    }

    @Test
    fun createIntentWithoutOpenPlayerFlag_shouldNotMarkLaunchRequest() {
        val intent = MainActivity.createIntent(
            context = context,
            openPlayer = false
        )

        assertFalse(MainActivity.shouldOpenPlayerFromIntent(intent))
    }
}
