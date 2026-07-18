package com.wxy.playerlite.playback.session

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesPlaybackSessionStateStorage(
    private val preferences: SharedPreferences,
    private val nowProvider: () -> Long = System::currentTimeMillis
) {
    fun read(): PlaybackSessionState? {
        val activeItemId = preferences.getString(KEY_ACTIVE_ITEM_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return PlaybackSessionState(
            activeItemId = activeItemId,
            positionMs = preferences.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L),
            playWhenReady = preferences.getBoolean(KEY_PLAY_WHEN_READY, false),
            savedAtMs = preferences.getLong(KEY_SAVED_AT_MS, 0L).coerceAtLeast(0L)
        )
    }

    fun write(state: PlaybackSessionState) {
        preferences.edit()
            .putString(KEY_ACTIVE_ITEM_ID, state.activeItemId)
            .putLong(KEY_POSITION_MS, state.positionMs.coerceAtLeast(0L))
            .putBoolean(KEY_PLAY_WHEN_READY, state.playWhenReady)
            .putLong(
                KEY_SAVED_AT_MS,
                state.savedAtMs.takeIf { it > 0L } ?: nowProvider()
            )
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_ACTIVE_ITEM_ID)
            .remove(KEY_POSITION_MS)
            .remove(KEY_PLAY_WHEN_READY)
            .remove(KEY_SAVED_AT_MS)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "playback_session_state"
        private const val KEY_ACTIVE_ITEM_ID = "active_item_id"
        private const val KEY_POSITION_MS = "position_ms"
        private const val KEY_PLAY_WHEN_READY = "play_when_ready"
        private const val KEY_SAVED_AT_MS = "saved_at_ms"

        fun fromContext(context: Context): SharedPreferencesPlaybackSessionStateStorage {
            return SharedPreferencesPlaybackSessionStateStorage(
                preferences = context.applicationContext.getSharedPreferences(
                    PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                )
            )
        }
    }
}
