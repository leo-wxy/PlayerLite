package com.wxy.playerlite.feature.player.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.player.source.ContentUriSource
import com.wxy.playerlite.player.source.IPlaysource
import com.wxy.playerlite.player.source.LocalFileSource
import java.io.File

internal class MediaSourceRepository(
    private val appContext: Context
) {
    fun ensurePersistentReadPermission(uri: Uri): Result<Unit> {
        if (uri.scheme != "content") {
            return Result.success(Unit)
        }
        if (hasPersistedReadPermission(uri)) {
            return Result.success(Unit)
        }
        return runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            check(hasPersistedReadPermission(uri)) {
                "persisted read permission missing after request"
            }
        }
    }

    fun isPlaylistItemReadable(item: PlaylistItem): Boolean {
        if (item.isOnline) {
            return !item.songId.isNullOrBlank()
        }
        val uri = try {
            Uri.parse(item.uri)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return hasReadableAccess(uri)
    }

    fun createPlayableSource(uri: Uri): IPlaysource? {
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path)
            if (!file.exists() || !file.canRead()) {
                return null
            }
            return LocalFileSource(file)
        }
        if (uri.scheme == "content" && !hasPersistedReadPermission(uri)) {
            return null
        }
        return runCatching {
            appContext.contentResolver.openInputStream(uri)?.close()
            ContentUriSource(appContext, uri)
        }.getOrNull()
    }

    fun hasPersistedReadPermission(uri: Uri): Boolean {
        if (uri.scheme != "content") {
            return false
        }
        return appContext.contentResolver.persistedUriPermissions.any { permission ->
            permission.isReadPermission && permission.uri == uri
        }
    }

    fun hasReadableAccess(uri: Uri): Boolean {
        if (uri.scheme == "file") {
            val path = uri.path ?: return false
            val file = File(path)
            return file.exists() && file.canRead()
        }
        if (uri.scheme == "content" && !hasPersistedReadPermission(uri)) {
            return false
        }
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { _ -> true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun queryDisplayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return "selected_audio"
    }
}
