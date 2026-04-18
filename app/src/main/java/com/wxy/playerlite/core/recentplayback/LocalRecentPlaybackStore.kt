package com.wxy.playerlite.core.recentplayback

import android.content.SharedPreferences
import com.wxy.playerlite.core.playlist.PlaylistItemType
import org.json.JSONArray
import org.json.JSONObject

internal data class LocalRecentPlaybackRecord(
    val recordKey: String,
    val sourceType: PlaylistItemType,
    val songId: String?,
    val playbackUri: String?,
    val title: String,
    val artistText: String,
    val albumTitle: String,
    val primaryArtistId: String?,
    val albumId: String?,
    val coverUrl: String?,
    val durationMs: Long,
    val playedAtMs: Long
)

internal interface LocalRecentPlaybackStore {
    fun read(limit: Int = DEFAULT_LIMIT): List<LocalRecentPlaybackRecord>

    fun record(record: LocalRecentPlaybackRecord)

    fun remove(recordKey: String): Boolean

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}

internal class SharedPreferencesLocalRecentPlaybackStore(
    private val preferences: SharedPreferences
) : LocalRecentPlaybackStore {
    private val recordLock = Any()

    override fun read(limit: Int): List<LocalRecentPlaybackRecord> {
        val raw = preferences.getString(KEY_RECORDS, null).orEmpty().trim()
        if (raw.isEmpty()) {
            return emptyList()
        }
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val playbackUri = item.optString(KEY_PLAYBACK_URI).trim().ifBlank { null }
                    val songId = item.optString(KEY_SONG_ID).trim().ifBlank { null }
                    val recordKey = item.optString(KEY_RECORD_KEY).trim()
                        .ifBlank {
                            when {
                                !songId.isNullOrBlank() -> "online:$songId"
                                !playbackUri.isNullOrBlank() -> "local:$playbackUri"
                                else -> ""
                            }
                        }
                    val title = item.optString(KEY_TITLE).trim()
                    if (recordKey.isEmpty() || title.isEmpty()) {
                        continue
                    }
                    add(
                        LocalRecentPlaybackRecord(
                            recordKey = recordKey,
                            sourceType = PlaylistItemType.fromWireValue(
                                item.optString(KEY_SOURCE_TYPE)
                            ) ?: if (songId != null) PlaylistItemType.ONLINE else PlaylistItemType.LOCAL,
                            songId = songId,
                            playbackUri = playbackUri,
                            title = title,
                            artistText = item.optString(KEY_ARTIST_TEXT).ifBlank { DEFAULT_ARTIST },
                            albumTitle = item.optString(KEY_ALBUM_TITLE).orEmpty(),
                            primaryArtistId = item.optString(KEY_PRIMARY_ARTIST_ID).ifBlank { null },
                            albumId = item.optString(KEY_ALBUM_ID).ifBlank { null },
                            coverUrl = item.optString(KEY_COVER_URL).ifBlank { null },
                            durationMs = item.optLong(KEY_DURATION_MS).coerceAtLeast(0L),
                            playedAtMs = item.optLong(KEY_PLAYED_AT_MS).coerceAtLeast(0L)
                        )
                    )
                }
            }.sortedByDescending(LocalRecentPlaybackRecord::playedAtMs)
                .take(limit.coerceAtLeast(0))
        }.getOrDefault(emptyList())
    }

    override fun record(record: LocalRecentPlaybackRecord) {
        if (record.recordKey.isBlank() || record.title.isBlank()) {
            return
        }
        synchronized(recordLock) {
            val nextRecord = record.copy(
                artistText = record.artistText.ifBlank { DEFAULT_ARTIST },
                playedAtMs = System.currentTimeMillis()
            )
            val nextItems = buildList {
                add(nextRecord)
                addAll(read(limit = Int.MAX_VALUE).filterNot { it.recordKey == nextRecord.recordKey })
            }.take(LocalRecentPlaybackStore.DEFAULT_LIMIT)
            preferences.edit()
                .putString(KEY_RECORDS, encode(nextItems))
                .commit()
        }
    }

    override fun remove(recordKey: String): Boolean {
        if (recordKey.isBlank()) {
            return false
        }
        synchronized(recordLock) {
            val currentItems = read(limit = Int.MAX_VALUE)
            val nextItems = currentItems.filterNot { it.recordKey == recordKey }
            if (nextItems.size == currentItems.size) {
                return false
            }
            preferences.edit()
                .putString(KEY_RECORDS, encode(nextItems))
                .commit()
            return true
        }
    }

    private fun encode(items: List<LocalRecentPlaybackRecord>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put(KEY_RECORD_KEY, item.recordKey)
                    put(KEY_SOURCE_TYPE, item.sourceType.wireValue)
                    put(KEY_SONG_ID, item.songId)
                    put(KEY_PLAYBACK_URI, item.playbackUri)
                    put(KEY_TITLE, item.title)
                    put(KEY_ARTIST_TEXT, item.artistText)
                    put(KEY_ALBUM_TITLE, item.albumTitle)
                    put(KEY_PRIMARY_ARTIST_ID, item.primaryArtistId)
                    put(KEY_ALBUM_ID, item.albumId)
                    put(KEY_COVER_URL, item.coverUrl)
                    put(KEY_DURATION_MS, item.durationMs)
                    put(KEY_PLAYED_AT_MS, item.playedAtMs)
                }
            )
        }
        return array.toString()
    }

    private companion object {
        const val KEY_RECORDS = "local_recent_playback_records"
        const val KEY_RECORD_KEY = "record_key"
        const val KEY_SOURCE_TYPE = "source_type"
        const val KEY_SONG_ID = "song_id"
        const val KEY_PLAYBACK_URI = "playback_uri"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST_TEXT = "artist_text"
        const val KEY_ALBUM_TITLE = "album_title"
        const val KEY_PRIMARY_ARTIST_ID = "primary_artist_id"
        const val KEY_ALBUM_ID = "album_id"
        const val KEY_COVER_URL = "cover_url"
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_PLAYED_AT_MS = "played_at_ms"
        const val DEFAULT_ARTIST = "未知歌手"
    }
}
