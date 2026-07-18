package com.wxy.playerlite.user.storage

import android.content.SharedPreferences
import com.wxy.playerlite.user.model.UserSession

interface UserSessionStorage {
    fun read(): UserSession?

    fun write(session: UserSession)

    fun clear()
}

class SharedPreferencesUserSessionStorage(
    private val preferences: SharedPreferences
) : UserSessionStorage {
    override fun read(): UserSession? {
        val raw = preferences.getString(KEY_SESSION, null) ?: return null
        return UserSessionSnapshotCodec.decode(raw)
    }

    override fun write(session: UserSession) {
        preferences.edit()
            .putString(KEY_SESSION, UserSessionSnapshotCodec.encode(session))
            .apply()
    }

    override fun clear() {
        preferences.edit()
            .remove(KEY_SESSION)
            .apply()
    }

    private companion object {
        private const val KEY_SESSION = "user_session"
    }
}
