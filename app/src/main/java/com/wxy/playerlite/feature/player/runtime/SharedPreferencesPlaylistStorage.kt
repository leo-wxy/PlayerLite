package com.wxy.playerlite.feature.player.runtime

import android.content.SharedPreferences
import com.wxy.playerlite.playlist.core.PlaylistStorage

internal class SharedPreferencesPlaylistStorage(
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
