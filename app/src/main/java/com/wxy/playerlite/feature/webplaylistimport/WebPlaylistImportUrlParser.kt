package com.wxy.playerlite.feature.webplaylistimport

import java.net.URI

enum class ImportedPlaylistSource(
    val wireValue: String
) {
    NETEASE("netease"),
    QQ_MUSIC("qq_music")
}

data class ParsedPlaylistRef(
    val source: ImportedPlaylistSource,
    val playlistId: String
)

class WebPlaylistImportUrlParser {
    fun parse(raw: String): ParsedPlaylistRef {
        val normalized = raw.trim()
        require(normalized.isNotBlank()) { "Playlist url is blank" }

        val uri = URI(normalized)
        val host = uri.host?.lowercase().orEmpty()
        return when {
            host.contains("music.163.com") -> ParsedPlaylistRef(
                source = ImportedPlaylistSource.NETEASE,
                playlistId = extractNeteasePlaylistId(uri)
            )

            host.contains("y.qq.com") || host.contains("qq.com") -> ParsedPlaylistRef(
                source = ImportedPlaylistSource.QQ_MUSIC,
                playlistId = extractQqMusicPlaylistId(uri)
            )

            else -> throw IllegalArgumentException("Unsupported playlist source: $host")
        }
    }

    private fun extractNeteasePlaylistId(uri: URI): String {
        extractQueryValue(uri.fragment, "id")?.let { return it }
        extractQueryValue(uri.rawQuery, "id")?.let { return it }
        return uri.path.orEmpty()
            .split('/')
            .firstOrNull { it.all(Char::isDigit) }
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing Netease playlist id")
    }

    private fun extractQqMusicPlaylistId(uri: URI): String {
        extractQueryValue(uri.rawQuery, "id")?.let { return it }
        return Regex("/playlist/(\\d+)")
            .find(uri.path.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?: throw IllegalArgumentException("Missing QQ Music playlist id")
    }

    private fun extractQueryValue(raw: String?, key: String): String? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val query = raw.substringAfter('?', raw)
        return query.split('&')
            .asSequence()
            .mapNotNull { segment ->
                val parts = segment.split('=', limit = 2)
                if (parts.size == 2 && parts[0] == key && parts[1].isNotBlank()) {
                    parts[1]
                } else {
                    null
                }
            }
            .firstOrNull()
    }
}
