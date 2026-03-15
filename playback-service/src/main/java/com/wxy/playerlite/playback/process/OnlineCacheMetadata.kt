package com.wxy.playerlite.playback.process

import com.wxy.playerlite.cache.core.CacheLookupSnapshot
import java.io.File

internal object OnlineCacheMetadata {
    const val CLIP_MODE_KEY = "onlinePlaybackClipMode"

    fun buildExtraMetadata(clipMode: OnlineClipMode): Map<String, String> {
        return mapOf(CLIP_MODE_KEY to clipMode.wireValue)
    }

    fun isTrustedForReuse(
        snapshot: CacheLookupSnapshot,
        expectedClipMode: OnlineClipMode
    ): Boolean {
        if (expectedClipMode != OnlineClipMode.FULL) {
            return true
        }
        return read(snapshot.extraFilePath)[CLIP_MODE_KEY] == OnlineClipMode.FULL.wireValue
    }

    fun persist(
        extraFilePath: String,
        values: Map<String, String>
    ) {
        if (extraFilePath.isBlank() || values.isEmpty()) {
            return
        }
        val file = File(extraFilePath)
        val payload = read(extraFilePath).toMutableMap()
        values.forEach { (key, value) ->
            payload[key] = value
        }
        file.parentFile?.mkdirs()
        file.writeText(
            payload.entries.joinToString(
                prefix = "{\n",
                postfix = "\n}\n",
                separator = ",\n"
            ) { (key, value) ->
                "  \"$key\": \"${escape(value)}\""
            }
        )
    }

    fun purgeSnapshot(snapshot: CacheLookupSnapshot) {
        listOf(snapshot.dataFilePath, snapshot.configFilePath, snapshot.extraFilePath)
            .map(::File)
            .forEach { file ->
                runCatching {
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
    }

    private fun read(extraFilePath: String): Map<String, String> {
        if (extraFilePath.isBlank()) {
            return emptyMap()
        }
        val file = File(extraFilePath)
        if (!file.exists()) {
            return emptyMap()
        }
        return runCatching {
            buildMap {
                EXTRA_ENTRY_REGEX.findAll(file.readText()).forEach { match ->
                    put(
                        match.groupValues[1],
                        match.groupValues[2].unescape()
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }

    private fun String.unescape(): String {
        return this
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private val EXTRA_ENTRY_REGEX = Regex("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
}
