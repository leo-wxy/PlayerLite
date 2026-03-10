package com.wxy.playerlite.feature.user

import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LoginViewModelFactoryTest {
    @Test
    fun androidViewModelFactory_shouldCreateLoginViewModel() {
        val application = RuntimeEnvironment.getApplication()

        val viewModel = ViewModelProvider.AndroidViewModelFactory
            .getInstance(application)
            .create(LoginViewModel::class.java)

        assertNotNull(viewModel)
    }
}
