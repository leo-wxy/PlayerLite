package com.wxy.playerlite.feature.local

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal class LocalSongsSnapshotStorage(
    private val preferences: SharedPreferences
) {
    fun read(): List<LocalSongEntry> {
        val raw = preferences.getString(KEY_SNAPSHOT, null).orEmpty().trim()
        if (raw.isEmpty()) {
            return emptyList()
        }
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString(KEY_ID).trim()
                    val contentUri = item.optString(KEY_CONTENT_URI).trim()
                    val title = item.optString(KEY_TITLE).trim()
                    if (id.isEmpty() || contentUri.isEmpty() || title.isEmpty()) {
                        continue
                    }
                    add(
                        LocalSongEntry(
                            id = id,
                            contentUri = contentUri,
                            title = title,
                            artist = item.optString(KEY_ARTIST).ifBlank { DEFAULT_ARTIST },
                            album = item.optString(KEY_ALBUM).ifBlank { DEFAULT_ALBUM },
                            durationMs = item.optLong(KEY_DURATION_MS).coerceAtLeast(0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun write(items: List<LocalSongEntry>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put(KEY_ID, item.id)
                    put(KEY_CONTENT_URI, item.contentUri)
                    put(KEY_TITLE, item.title)
                    put(KEY_ARTIST, item.artist)
                    put(KEY_ALBUM, item.album)
                    put(KEY_DURATION_MS, item.durationMs)
                }
            )
        }
        preferences.edit().putString(KEY_SNAPSHOT, array.toString()).apply()
    }

    private companion object {
        const val KEY_SNAPSHOT = "local_songs_snapshot"
        const val KEY_ID = "id"
        const val KEY_CONTENT_URI = "content_uri"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ALBUM = "album"
        const val KEY_DURATION_MS = "duration_ms"
        const val DEFAULT_ARTIST = "未知歌手"
        const val DEFAULT_ALBUM = "未知专辑"
    }
}
