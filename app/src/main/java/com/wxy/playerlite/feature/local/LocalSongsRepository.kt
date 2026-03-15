package com.wxy.playerlite.feature.local

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface LocalSongsRepository {
    suspend fun readCachedSongs(): List<LocalSongEntry>

    suspend fun scanSongs(): Result<List<LocalSongEntry>>
}

internal class DefaultLocalSongsRepository(
    private val contentResolver: ContentResolver,
    private val storage: LocalSongsSnapshotStorage
) : LocalSongsRepository {
    override suspend fun readCachedSongs(): List<LocalSongEntry> {
        return withContext(Dispatchers.IO) {
            storage.read()
        }
    }

    override suspend fun scanSongs(): Result<List<LocalSongEntry>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val songs = mutableListOf<LocalSongEntry>()
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION
                )
                val selection = buildString {
                    append("${MediaStore.Audio.AudioColumns.IS_MUSIC} != 0")
                    append(" AND ${MediaStore.MediaColumns.SIZE} > 0")
                }
                val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(idIndex)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            mediaId
                        ).toString()
                        val title = cursor.getString(titleIndex).orEmpty().ifBlank { "未知歌曲" }
                        songs += LocalSongEntry(
                            id = contentUri,
                            contentUri = contentUri,
                            title = title,
                            artist = cursor.getString(artistIndex).orEmpty().ifBlank { "未知歌手" },
                            album = cursor.getString(albumIndex).orEmpty().ifBlank { "未知专辑" },
                            durationMs = cursor.getLong(durationIndex).coerceAtLeast(0L)
                        )
                    }
                }
                storage.write(songs)
                songs
            }
        }
    }
}
