package com.wxy.playerlite.feature.player

import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayerViewModelFactoryTest {
    @Test
    fun androidViewModelFactory_shouldCreatePlayerViewModel() {
        val application = RuntimeEnvironment.getApplication()

        val viewModel = ViewModelProvider.AndroidViewModelFactory
            .getInstance(application)
            .create(PlayerViewModel::class.java)

        assertNotNull(viewModel)
    }
}
