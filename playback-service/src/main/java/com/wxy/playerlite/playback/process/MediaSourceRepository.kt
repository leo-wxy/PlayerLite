package com.wxy.playerlite.playback.process

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

internal class MediaSourceRepository(
    private val appContext: Context
) {
    fun copyUriToCacheFile(uri: Uri): File? {
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return file
            }
        }

        val safeName = (uri.lastPathSegment ?: "selected_audio").replace("[^A-Za-z0-9._-]".toRegex(), "_")
        val inputFile = File(appContext.cacheDir, "input_$safeName")

        val input = appContext.contentResolver.openInputStream(uri) ?: return null
        input.use { source ->
            FileOutputStream(inputFile).use { output ->
                source.copyTo(output)
            }
        }
        return inputFile
    }
}
