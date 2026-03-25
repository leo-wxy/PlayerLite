package com.wxy.playerlite.feature.webplaylistimport

import com.wxy.playerlite.network.core.JsonHttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface QqMusicPlaylistRemoteDataSource {
    suspend fun fetchPlaylistDetail(playlistId: String): JsonObject
}

internal const val QQ_MUSIC_API_BASE_URL = "https://c.y.qq.com"

class DefaultQqMusicPlaylistRemoteDataSource(
    private val httpClient: JsonHttpClient
) : QqMusicPlaylistRemoteDataSource {
    override suspend fun fetchPlaylistDetail(playlistId: String): JsonObject {
        return httpClient.get(
            path = "/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg",
            queryParams = linkedMapOf(
                "type" to "1",
                "json" to "1",
                "utf8" to "1",
                "onlysong" to "0",
                "new_format" to "1",
                "disstid" to playlistId,
                "loginUin" to "0",
                "hostUin" to "0",
                "format" to "json",
                "inCharset" to "utf8",
                "outCharset" to "utf-8",
                "notice" to "0",
                "platform" to "yqq.json",
                "needNewCode" to "0"
            ),
            headers = mapOf(
                "Origin" to "https://y.qq.com",
                "Referer" to "https://y.qq.com/n/yqq/playsquare/$playlistId.html",
                "User-Agent" to DEFAULT_USER_AGENT
            )
        )
    }

    private companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
    }
}

internal object QqMusicPlaylistJsonMapper {
    fun parseSnapshot(
        payload: JsonObject,
        ref: ParsedPlaylistRef,
        sourceUrl: String
    ): ImportedPlaylistSnapshot {
        val playlist = payload.arrayValue("cdlist")
            .mapNotNull { it as? JsonObject }
            .firstOrNull()
            ?: emptyJsonObject
        return ImportedPlaylistSnapshot(
            source = ref.source,
            playlistId = ref.playlistId,
            sourceUrl = sourceUrl,
            title = playlist.stringValue("dissname")
                ?: playlist.stringValue("name")
                .orEmpty(),
            creatorName = playlist.stringValue("nickname")
                ?: playlist.stringValue("nick")
                .orEmpty(),
            description = playlist.stringValue("desc").orEmpty(),
            coverUrl = playlist.stringValue("logo"),
            tracks = playlist.arrayValue("songlist").mapNotNull { element ->
                val track = element as? JsonObject ?: return@mapNotNull null
                val singers = track.arrayValue("singer").mapNotNull { singer ->
                    (singer as? JsonObject)?.stringValue("name")
                }
                val album = track.objectValue("album")
                val albumTitle = album.stringValue("title")
                    ?: album.stringValue("name")
                    .orEmpty()
                ImportedPlaylistTrack(
                    sourceTrackId = track.stringValue("id"),
                    title = track.stringValue("title")
                        ?: track.stringValue("name")
                        .orEmpty(),
                    artistNames = singers,
                    albumTitle = albumTitle,
                    coverUrl = album.stringValue("mid")?.let(::qqAlbumCoverUrl),
                    durationMs = track.longValue("interval") * 1_000L,
                    resolution = ImportedTrackResolution.Unmatched
                )
            }
        )
    }

    private fun qqAlbumCoverUrl(albumMid: String): String {
        return "https://y.gtimg.cn/music/photo_new/T002R500x500M000$albumMid.jpg"
    }
}

private fun JsonObject.objectValue(key: String): JsonObject {
    return (this[key] as? JsonObject) ?: emptyJsonObject
}

private fun JsonObject.arrayValue(key: String): JsonArray {
    return this[key]?.jsonArray ?: emptyJsonArray
}

private fun JsonObject.stringValue(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.longValue(key: String): Long {
    return stringValue(key)?.toLongOrNull() ?: 0L
}

private val emptyJsonObject = JsonObject(emptyMap())
private val emptyJsonArray = JsonArray(emptyList())
