package com.wxy.playerlite.playlist

import android.content.SharedPreferences

interface PlaylistStorage {
    fun read(key: String): String?

    fun write(key: String, value: String)

    fun remove(key: String)
}

class SharedPreferencesPlaylistStorage(
    private val preferences: SharedPreferences
) : PlaylistStorage {
    override fun read(key: String): String? {
        return preferences.getString(key, null)
    }

    override fun write(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }
}
