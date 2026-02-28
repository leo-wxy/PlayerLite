package com.wxy.playerlite.feature.player.runtime

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.wxy.playerlite.core.playlist.PlaylistItem
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal class MediaSourceRepository(
    private val appContext: Context
) {
    fun persistReadPermission(uri: Uri) {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers do not support persistable permissions.
        } catch (_: IllegalArgumentException) {
            // Ignore invalid permission requests.
        }
    }

    fun isPlaylistItemReadable(item: PlaylistItem): Boolean {
        val uri = try {
            Uri.parse(item.uri)
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (uri.scheme == "file") {
            val path = uri.path ?: return false
            val file = File(path)
            return file.exists() && file.canRead()
        }
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { _ -> true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun copyUriToCacheFile(uri: Uri): File? {
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return file
            }
        }
        val safeName = queryDisplayName(uri).replace("[^A-Za-z0-9._-]".toRegex(), "_")
        val inputFile = File(appContext.cacheDir, "input_$safeName")

        val input = appContext.contentResolver.openInputStream(uri) ?: return null
        input.use { source ->
            FileOutputStream(inputFile).use { output ->
                source.copyTo(output)
            }
        }
        return inputFile
    }

    fun importToPrivateStorage(uri: Uri, displayName: String): File? {
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return file
            }
        }

        val safeName = displayName.replace("[^A-Za-z0-9._-]".toRegex(), "_")
        val mediaDir = File(appContext.filesDir, "imported_media").apply { mkdirs() }
        val output = File(mediaDir, "${UUID.randomUUID()}_$safeName")

        val input = appContext.contentResolver.openInputStream(uri) ?: return null
        input.use { source ->
            FileOutputStream(output).use { sink ->
                source.copyTo(sink)
            }
        }
        return output
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
