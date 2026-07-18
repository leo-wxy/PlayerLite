package com.wxy.playerlite.user

import com.wxy.playerlite.user.model.LoginState
import com.wxy.playerlite.user.model.UserSession
import com.wxy.playerlite.user.storage.UserSessionStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DefaultUserRepository(
    private val storage: UserSessionStorage,
    private val remoteDataSource: UserRemoteDataSource
) : UserRepository {
    private val _loginState = MutableStateFlow<LoginState>(LoginState.LoggedOut)

    override val loginStateFlow: StateFlow<LoginState> = _loginState.asStateFlow()

    override fun currentSession(): UserSession? {
        return (loginStateFlow.value as? LoginState.LoggedIn)?.session
    }

    override suspend fun restorePersistedSession() {
        val cached = storage.read() ?: run {
            _loginState.value = LoginState.LoggedOut
            return
        }
        _loginState.value = LoginState.LoggedIn(cached)
        try {
            val refreshed = remoteDataSource.refreshUserInfo(cached)
            val updated = cached.copy(
                userInfo = refreshed,
                lastValidatedAtMs = System.currentTimeMillis()
            )
            storage.write(updated)
            _loginState.value = LoginState.LoggedIn(updated)
        } catch (_: UserSessionInvalidException) {
            storage.clear()
            _loginState.value = LoginState.LoggedOut
        }
    }

    override suspend fun loginWithPhone(
        phone: String,
        password: String,
        countryCode: String
    ): UserSession {
        val session = remoteDataSource.loginWithPhone(
            phone = phone,
            password = password,
            countryCode = countryCode
        )
        storage.write(session)
        _loginState.value = LoginState.LoggedIn(session)
        return session
    }

    override suspend fun loginWithEmail(
        email: String,
        password: String
    ): UserSession {
        val session = remoteDataSource.loginWithEmail(
            email = email,
            password = password
        )
        storage.write(session)
        _loginState.value = LoginState.LoggedIn(session)
        return session
    }

    override suspend fun refreshUserInfo(): UserSession? {
        val current = currentSession() ?: return null
        return try {
            val refreshed = remoteDataSource.refreshUserInfo(current)
            val updated = current.copy(
                userInfo = refreshed,
                lastValidatedAtMs = System.currentTimeMillis()
            )
            storage.write(updated)
            _loginState.value = LoginState.LoggedIn(updated)
            updated
        } catch (_: UserSessionInvalidException) {
            storage.clear()
            _loginState.value = LoginState.LoggedOut
            null
        }
    }

    override suspend fun logout() {
        currentSession()?.let { session ->
            runCatching { remoteDataSource.logout(session) }
        }
        storage.clear()
        _loginState.value = LoginState.LoggedOut
    }
}
