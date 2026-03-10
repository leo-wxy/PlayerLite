package com.wxy.playerlite.core

import android.content.Context
import com.wxy.playerlite.network.core.AuthHeaderProvider
import com.wxy.playerlite.network.core.JsonHttpClient
import com.wxy.playerlite.user.DefaultUserRepository
import com.wxy.playerlite.user.UserRepository
import com.wxy.playerlite.user.model.toAuthHeaders
import com.wxy.playerlite.user.remote.NeteaseUserRemoteDataSource
import com.wxy.playerlite.user.storage.SharedPreferencesUserSessionStorage

internal object AppContainer {
    private const val USER_API_BASE_URL = "http://139.9.223.233:3000"
    private const val USER_SESSION_PREFS = "user_session"

    @Volatile
    private var services: Services? = null

    fun userRepository(context: Context): UserRepository {
        return getServices(context).userRepository
    }

    private fun getServices(context: Context): Services {
        val existing = services
        if (existing != null) {
            return existing
        }
        return synchronized(this) {
            services ?: buildServices(context.applicationContext).also {
                services = it
            }
        }
    }

    private fun buildServices(context: Context): Services {
        val preferences = context.getSharedPreferences(USER_SESSION_PREFS, Context.MODE_PRIVATE)
        val storage = SharedPreferencesUserSessionStorage(preferences)
        val httpClient = JsonHttpClient(
            baseUrl = USER_API_BASE_URL,
            authHeaderProvider = AuthHeaderProvider {
                storage.read()?.toAuthHeaders() ?: emptyMap()
            }
        )
        val remoteDataSource = NeteaseUserRemoteDataSource(httpClient)
        return Services(
            userRepository = DefaultUserRepository(
                storage = storage,
                remoteDataSource = remoteDataSource
            )
        )
    }

    private data class Services(
        val userRepository: UserRepository
    )
}
