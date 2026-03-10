package com.wxy.playerlite.user

import com.wxy.playerlite.user.model.UserInfo
import com.wxy.playerlite.user.model.UserSession

interface UserRemoteDataSource {
    suspend fun loginWithPhone(
        phone: String,
        password: String,
        countryCode: String = DEFAULT_COUNTRY_CODE
    ): UserSession

    suspend fun loginWithEmail(
        email: String,
        password: String
    ): UserSession

    suspend fun refreshUserInfo(session: UserSession): UserInfo

    suspend fun logout(session: UserSession)

    companion object {
        const val DEFAULT_COUNTRY_CODE = "86"
    }
}
