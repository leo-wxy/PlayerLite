package com.wxy.playerlite.feature.player

import com.wxy.playerlite.network.core.JsonHttpClient
import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal interface LyricRepository {
    suspend fun readCachedLyrics(songId: String): ParsedLyrics?

    suspend fun fetchLyrics(songId: String): ParsedLyrics?
}

internal class DefaultLyricRepository(
    private val remoteDataSource: LyricRemoteDataSource,
    private val localStore: LyricLocalStore
) : LyricRepository {
    override suspend fun readCachedLyrics(songId: String): ParsedLyrics? {
        val rawLyric = localStore.read(songId) ?: return null
        return LyricParser.parse(songId = songId, rawLyric = rawLyric)
    }

    override suspend fun fetchLyrics(songId: String): ParsedLyrics? {
        val payload = remoteDataSource.fetchLyrics(songId)
        check(payload.intValue("code") == 200) {
            "Lyric request failed: code=${payload.intValue("code")}"
        }
        val rawLyric = payload.objectValue("lrc").stringValue("lyric").orEmpty()
        val parsed = LyricParser.parse(songId = songId, rawLyric = rawLyric) ?: return null
        localStore.write(songId = songId, rawLyric = rawLyric)
        return parsed
    }
}

internal interface LyricRemoteDataSource {
    suspend fun fetchLyrics(songId: String): JsonObject
}

internal class NeteaseLyricRemoteDataSource(
    private val httpClient: JsonHttpClient
) : LyricRemoteDataSource {
    override suspend fun fetchLyrics(songId: String): JsonObject {
        return httpClient.get(
            path = "/lyric",
            queryParams = mapOf("id" to songId),
            requiresAuth = false
        )
    }
}

internal class LyricLocalStore(
    private val directory: File,
    private val maxEntries: Int = 100
) {
    fun read(songId: String): String? {
        val file = resolveFile(songId)
        if (!file.exists()) {
            return null
        }
        file.setLastModified(System.currentTimeMillis())
        return file.readText()
    }

    fun write(songId: String, rawLyric: String) {
        directory.mkdirs()
        val file = resolveFile(songId)
        file.writeText(rawLyric)
        file.setLastModified(System.currentTimeMillis())
        pruneIfNeeded()
    }

    private fun pruneIfNeeded() {
        val files = directory.listFiles { candidate ->
            candidate.isFile && candidate.extension == FILE_EXTENSION
        }?.toList().orEmpty()
        if (files.size <= maxEntries) {
            return
        }
        files.sortedBy { it.lastModified() }
            .take(files.size - maxEntries)
            .forEach(File::delete)
    }

    private fun resolveFile(songId: String): File {
        return File(directory, "$songId.$FILE_EXTENSION")
    }

    private companion object {
        private const val FILE_EXTENSION = "lrc"
    }
}

internal object LyricParser {
    private val timestampPattern = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{1,3}))?]""")

    fun parse(songId: String, rawLyric: String): ParsedLyrics? {
        val normalized = rawLyric.trim()
        if (normalized.isBlank()) {
            return null
        }
        val lines = buildList {
            normalized.lineSequence().forEach { rawLine ->
                val matches = timestampPattern.findAll(rawLine).toList()
                if (matches.isEmpty()) {
                    return@forEach
                }
                val text = timestampPattern.replace(rawLine, "").trim()
                if (text.isBlank()) {
                    return@forEach
                }
                matches.forEach { match ->
                    add(
                        LyricLine(
                            timestampMs = parseTimestampMs(match),
                            text = text
                        )
                    )
                }
            }
        }.sortedBy { it.timestampMs }
        if (lines.isEmpty()) {
            return null
        }
        return ParsedLyrics(
            songId = songId,
            lines = lines,
            rawText = normalized
        )
    }

    private fun parseTimestampMs(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val fraction = match.groupValues.getOrElse(3) { "" }
        val fractionMs = when (fraction.length) {
            1 -> fraction.toLongOrNull()?.times(100L) ?: 0L
            2 -> fraction.toLongOrNull()?.times(10L) ?: 0L
            3 -> fraction.toLongOrNull() ?: 0L
            else -> 0L
        }
        return minutes * 60_000L + seconds * 1_000L + fractionMs
    }
}

private fun JsonObject.objectValue(key: String): JsonObject {
    return this[key] as? JsonObject ?: JsonObject(emptyMap())
}

private fun JsonObject.stringValue(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.intValue(key: String): Int {
    return stringValue(key)?.toIntOrNull() ?: 0
}
