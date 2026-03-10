package com.wxy.playerlite.user

import com.wxy.playerlite.user.model.LoginState
import com.wxy.playerlite.user.model.UserSession
import kotlinx.coroutines.flow.StateFlow

interface UserRepository {
    val loginStateFlow: StateFlow<LoginState>

    fun currentSession(): UserSession?

    suspend fun restorePersistedSession()

    suspend fun loginWithPhone(
        phone: String,
        password: String,
        countryCode: String = UserRemoteDataSource.DEFAULT_COUNTRY_CODE
    ): UserSession

    suspend fun loginWithEmail(
        email: String,
        password: String
    ): UserSession

    suspend fun refreshUserInfo(): UserSession?

    suspend fun logout()
}
