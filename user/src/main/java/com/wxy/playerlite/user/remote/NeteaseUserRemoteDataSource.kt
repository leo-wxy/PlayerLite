package com.wxy.playerlite.user.remote

import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.network.core.NetworkRequestException
import com.wxy.playerlite.user.UserRemoteDataSource
import com.wxy.playerlite.user.UserRequestException
import com.wxy.playerlite.user.UserSessionInvalidException
import com.wxy.playerlite.user.model.UserInfo
import com.wxy.playerlite.user.model.UserSession
import com.wxy.playerlite.user.model.toAuthHeaders
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

class NeteaseUserRemoteDataSource(
    private val httpClient: JsonHttpClient
) : UserRemoteDataSource {
    override suspend fun loginWithPhone(
        phone: String,
        password: String,
        countryCode: String
    ): UserSession {
        val payload = try {
            httpClient.postForm(
                path = "/login/cellphone",
                formParams = mapOf(
                    "phone" to phone,
                    "password" to password,
                    "countrycode" to countryCode
                )
            )
        } catch (error: NetworkRequestException) {
            throw UserRequestException(error.message ?: "Login failed")
        }
        ensureSuccess(payload)
        val session = NeteaseUserJsonMapper.parseLoginResponse(payload)
        val detailedUser = refreshUserInfo(session)
        return session.copy(
            userInfo = detailedUser,
            lastValidatedAtMs = System.currentTimeMillis()
        )
    }

    override suspend fun loginWithEmail(
        email: String,
        password: String
    ): UserSession {
        val payload = try {
            httpClient.postForm(
                path = "/login",
                formParams = mapOf(
                    "email" to email,
                    "password" to password
                )
            )
        } catch (error: NetworkRequestException) {
            throw UserRequestException(error.message ?: "Login failed")
        }
        ensureSuccess(payload)
        val session = NeteaseUserJsonMapper.parseLoginResponse(payload)
        val detailedUser = refreshUserInfo(session)
        return session.copy(
            userInfo = detailedUser,
            lastValidatedAtMs = System.currentTimeMillis()
        )
    }

    override suspend fun refreshUserInfo(session: UserSession): UserInfo {
        val payload = try {
            httpClient.get(
                path = "/user/detail",
                queryParams = mapOf("uid" to session.userInfo.userId.toString()),
                requiresAuth = true,
                headers = session.toAuthHeaders()
            )
        } catch (error: NetworkRequestException) {
            throw UserRequestException(error.message ?: "User detail request failed")
        }
        ensureSuccess(payload)
        return NeteaseUserJsonMapper.mergeUserDetail(session.userInfo, payload)
    }

    override suspend fun logout(session: UserSession) {
        val payload = try {
            httpClient.postForm(
                path = "/logout",
                formParams = emptyMap(),
                requiresAuth = true,
                headers = session.toAuthHeaders()
            )
        } catch (error: NetworkRequestException) {
            throw UserRequestException(error.message ?: "Logout failed")
        }
        ensureSuccess(payload)
    }

    private fun ensureSuccess(payload: JsonObject) {
        val code = payload.int("code") ?: payload.int(JsonHttpClient.KEY_HTTP_STATUS) ?: -1
        if (code == 200) {
            return
        }
        val message = payload.string("message")
            ?.takeIf { it.isNotBlank() }
            ?: payload.string("msg")?.takeIf { it.isNotBlank() }
            ?: "Request failed($code)"
        if (code == 301 || code == 302) {
            throw UserSessionInvalidException(message)
        }
        throw UserRequestException(message)
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
