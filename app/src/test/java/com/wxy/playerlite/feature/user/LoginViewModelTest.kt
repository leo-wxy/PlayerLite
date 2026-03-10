package com.wxy.playerlite.feature.user

import com.wxy.playerlite.user.UserRepository
import com.wxy.playerlite.user.model.LoginState
import com.wxy.playerlite.user.model.UserInfo
import com.wxy.playerlite.user.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun submitLogin_successShouldUpdateUiStateAndFlagCompletion() = runTest {
        val repository = FakeUserRepository().apply {
            loginResult = session()
        }
        val viewModel = LoginViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository
        )

        viewModel.updatePhone("13800138000")
        viewModel.updatePassword("password")
        viewModel.submitLogin()
        advanceUntilIdle()

        assertTrue(viewModel.uiStateFlow.value.isLoggedIn)
        assertEquals("Codex", viewModel.uiStateFlow.value.userTitle)
        assertTrue(viewModel.uiStateFlow.value.loginSucceeded)
    }

    @Test
    fun submitLogin_failureShouldKeepUserLoggedOut() = runTest {
        val repository = FakeUserRepository().apply {
            loginError = IllegalStateException("手机号或密码错误")
        }
        val viewModel = LoginViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository
        )

        viewModel.updatePhone("13800138000")
        viewModel.updatePassword("wrong")
        viewModel.submitLogin()
        advanceUntilIdle()

        assertFalse(viewModel.uiStateFlow.value.isLoggedIn)
        assertEquals("手机号或密码错误", viewModel.uiStateFlow.value.statusText)
    }

    @Test
    fun init_shouldReflectRestoredLoggedInState() = runTest {
        val repository = FakeUserRepository().apply {
            restoredSession = session().copy(
                userInfo = session().userInfo.copy(nickname = "Restored")
            )
        }
        val viewModel = LoginViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiStateFlow.value.isLoggedIn)
        assertEquals("Restored", viewModel.uiStateFlow.value.userTitle)
    }

    @Test
    fun skip_shouldMarkSkipRequestedWithoutLoggingIn() = runTest {
        val repository = FakeUserRepository()
        val viewModel = LoginViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository
        )

        viewModel.skipLogin()
        advanceUntilIdle()

        assertTrue(viewModel.uiStateFlow.value.skipRequested)
        assertFalse(viewModel.uiStateFlow.value.isLoggedIn)
    }

    @Test
    fun selectEmailLogin_shouldSwitchFormModeAndResetStatus() = runTest {
        val repository = FakeUserRepository()
        val viewModel = LoginViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository
        )

        viewModel.selectLoginMethod(LoginMethod.EMAIL)
        advanceUntilIdle()

        assertEquals(LoginMethod.EMAIL, viewModel.uiStateFlow.value.loginMethod)
        assertEquals("登录只影响受保护的在线播放能力，本地播放不受影响", viewModel.uiStateFlow.value.statusText)
    }

    @Test
    fun submitEmailLogin_successShouldUpdateUiStateAndFlagCompletion() = runTest {
        val repository = FakeUserRepository().apply {
            emailLoginResult = session().copy(
                userInfo = session().userInfo.copy(
                    nickname = "Mail Codex",
                    accountIdentity = "codex@example.com"
                )
            )
        }
        val viewModel = LoginViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository
        )

        viewModel.selectLoginMethod(LoginMethod.EMAIL)
        viewModel.updateEmail("codex@example.com")
        viewModel.updatePassword("password")
        viewModel.submitLogin()
        advanceUntilIdle()

        assertTrue(viewModel.uiStateFlow.value.isLoggedIn)
        assertEquals("Mail Codex", viewModel.uiStateFlow.value.userTitle)
        assertTrue(viewModel.uiStateFlow.value.loginSucceeded)
    }

    @Test
    fun submitEmailLogin_missingCredentialsShouldShowEmailValidationMessage() = runTest {
        val repository = FakeUserRepository()
        val viewModel = LoginViewModel(
            application = RuntimeEnvironment.getApplication(),
            repository = repository
        )

        viewModel.selectLoginMethod(LoginMethod.EMAIL)
        viewModel.submitLogin()
        advanceUntilIdle()

        assertEquals("请输入邮箱和密码", viewModel.uiStateFlow.value.statusText)
        assertFalse(viewModel.uiStateFlow.value.isLoggedIn)
    }

    private fun session(): UserSession {
        return UserSession(
            cookie = "MUSIC_U=token; __csrf=csrf-token;",
            csrfToken = "csrf-token",
            userInfo = UserInfo(
                userId = 77L,
                accountId = 88L,
                nickname = "Codex",
                avatarUrl = "https://example.com/avatar.jpg",
                vipType = 11,
                level = 9,
                signature = "hello",
                backgroundUrl = "https://example.com/bg.jpg",
                playlistCount = 9,
                followeds = 8,
                follows = 7,
                eventCount = 6,
                listenSongs = 321,
                accountIdentity = "13800138000"
            ),
            lastValidatedAtMs = 123L
        )
    }

    private class FakeUserRepository : UserRepository {
        private val state = MutableStateFlow<LoginState>(LoginState.LoggedOut)

        var loginResult: UserSession? = null
        var emailLoginResult: UserSession? = null
        var loginError: Throwable? = null
        var restoredSession: UserSession? = null

        override val loginStateFlow: StateFlow<LoginState> = state

        override fun currentSession(): UserSession? {
            return (state.value as? LoginState.LoggedIn)?.session
        }

        override suspend fun restorePersistedSession() {
            restoredSession?.let {
                state.value = LoginState.LoggedIn(it)
            }
        }

        override suspend fun loginWithPhone(
            phone: String,
            password: String,
            countryCode: String
        ): UserSession {
            loginError?.let { throw it }
            val session = requireNotNull(loginResult)
            state.value = LoginState.LoggedIn(session)
            return session
        }

        override suspend fun loginWithEmail(
            email: String,
            password: String
        ): UserSession {
            loginError?.let { throw it }
            val session = requireNotNull(emailLoginResult)
            state.value = LoginState.LoggedIn(session)
            return session
        }

        override suspend fun refreshUserInfo(): UserSession? {
            return currentSession()
        }

        override suspend fun logout() {
            state.value = LoginState.LoggedOut
        }
    }
}
