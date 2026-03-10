package com.wxy.playerlite.user

import com.wxy.playerlite.user.model.LoginState
import com.wxy.playerlite.user.model.UserInfo
import com.wxy.playerlite.user.model.UserSession
import com.wxy.playerlite.user.remote.UserInfoFixture
import com.wxy.playerlite.user.storage.UserSessionStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultUserRepositoryTest {
    @Test
    fun loginWithEmail_shouldPersistSessionAndPublishLoggedIn() = runBlocking {
        val storage = FakeUserSessionStorage()
        val session = UserSession(
            cookie = "MUSIC_U=token; __csrf=csrf-token;",
            csrfToken = "csrf-token",
            userInfo = UserInfoFixture.loggedIn().copy(accountIdentity = "codex@example.com"),
            lastValidatedAtMs = 10L
        )
        val remote = FakeUserRemoteDataSource(
            emailLoginSession = session
        )
        val repository = DefaultUserRepository(
            storage = storage,
            remoteDataSource = remote
        )

        val result = repository.loginWithEmail(
            email = "codex@example.com",
            password = "password"
        )

        assertEquals(session, result)
        assertEquals(session, storage.session)
        val state = repository.loginStateFlow.value
        assertTrue(state is LoginState.LoggedIn)
        assertEquals("codex@example.com", (state as LoginState.LoggedIn).session.userInfo.accountIdentity)
    }

    @Test
    fun restorePersistedSession_shouldPublishLoggedInAndRefreshUserInfo() = runBlocking {
        val storage = FakeUserSessionStorage(
            session = UserSession(
                cookie = "MUSIC_U=token; __csrf=csrf-token;",
                csrfToken = "csrf-token",
                userInfo = UserInfoFixture.loggedIn(),
                lastValidatedAtMs = 10L
            )
        )
        val remote = FakeUserRemoteDataSource(
            userDetail = UserInfoFixture.loggedIn().copy(level = 9, listenSongs = 321)
        )
        val repository = DefaultUserRepository(
            storage = storage,
            remoteDataSource = remote
        )

        repository.restorePersistedSession()

        val state = repository.loginStateFlow.value
        assertTrue(state is LoginState.LoggedIn)
        val session = (state as LoginState.LoggedIn).session
        assertEquals(9, session.userInfo.level)
        assertEquals(321, session.userInfo.listenSongs)
        assertEquals(session, storage.session)
    }

    @Test
    fun restorePersistedSession_shouldClearStorageWhenValidationFailsUnauthorized() = runBlocking {
        val storage = FakeUserSessionStorage(
            session = UserSession(
                cookie = "MUSIC_U=token; __csrf=csrf-token;",
                csrfToken = "csrf-token",
                userInfo = UserInfoFixture.loggedIn(),
                lastValidatedAtMs = 10L
            )
        )
        val remote = FakeUserRemoteDataSource(
            invalidSession = true
        )
        val repository = DefaultUserRepository(
            storage = storage,
            remoteDataSource = remote
        )

        repository.restorePersistedSession()

        assertTrue(repository.loginStateFlow.value is LoginState.LoggedOut)
        assertNull(storage.session)
    }

    @Test
    fun logout_shouldClearPersistedSessionAndResetState() = runBlocking {
        val storage = FakeUserSessionStorage(
            session = UserSession(
                cookie = "MUSIC_U=token; __csrf=csrf-token;",
                csrfToken = "csrf-token",
                userInfo = UserInfoFixture.loggedIn(),
                lastValidatedAtMs = 10L
            )
        )
        val remote = FakeUserRemoteDataSource()
        val repository = DefaultUserRepository(
            storage = storage,
            remoteDataSource = remote
        )
        repository.restorePersistedSession()

        repository.logout()

        assertTrue(repository.loginStateFlow.value is LoginState.LoggedOut)
        assertNull(storage.session)
        assertTrue(remote.logoutCalled)
    }

    private class FakeUserSessionStorage(
        var session: UserSession? = null
    ) : UserSessionStorage {
        override fun read(): UserSession? = session

        override fun write(session: UserSession) {
            this.session = session
        }

        override fun clear() {
            session = null
        }
    }

    private class FakeUserRemoteDataSource(
        private val userDetail: UserInfo? = null,
        private val invalidSession: Boolean = false,
        private val emailLoginSession: UserSession? = null
    ) : UserRemoteDataSource {
        var logoutCalled: Boolean = false

        override suspend fun loginWithPhone(
            phone: String,
            password: String,
            countryCode: String
        ): UserSession {
            error("not used in this test")
        }

        override suspend fun loginWithEmail(
            email: String,
            password: String
        ): UserSession {
            return requireNotNull(emailLoginSession) {
                "emailLoginSession must be configured for this test"
            }
        }

        override suspend fun refreshUserInfo(session: UserSession): UserInfo {
            if (invalidSession) {
                throw UserSessionInvalidException("expired")
            }
            return userDetail ?: session.userInfo
        }

        override suspend fun logout(session: UserSession) {
            logoutCalled = true
        }
    }
}
