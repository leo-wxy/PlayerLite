package com.wxy.playerlite.playback.model

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class MusicInfo(
    val id: String,
    val title: String,
    val playbackUri: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    val requiresAuthorization: Boolean = false
) {
    fun toMediaItem(statusText: String? = null): MediaItem {
        val normalizedTitle = title.ifBlank { "Unknown audio" }
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(normalizedTitle)
            .setExtras(
                Bundle().apply {
                    putString(EXTRA_PLAYBACK_URI, playbackUri)
                    putString(EXTRA_REQUEST_HEADERS_JSON, encodeHeaders(requestHeaders))
                    putBoolean(EXTRA_REQUIRES_AUTHORIZATION, requiresAuthorization)
                }
            )
        if (!statusText.isNullOrBlank()) {
            metadataBuilder.setSubtitle(statusText)
        }

        return MediaItem.Builder()
            .setMediaId(id.ifBlank { playbackUri })
            .setUri(playbackUri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    companion object {
        private const val EXTRA_PLAYBACK_URI = "playback_uri"
        private const val EXTRA_REQUEST_HEADERS_JSON = "request_headers_json"
        private const val EXTRA_REQUIRES_AUTHORIZATION = "requires_authorization"
        private val json = Json { ignoreUnknownKeys = true }

        fun fromMediaItem(mediaItem: MediaItem): MusicInfo? {
            val uri = mediaItem.mediaMetadata.extras?.getString(EXTRA_PLAYBACK_URI)
                ?: mediaItem.localConfiguration?.uri?.toString()
                ?: return null

            val id = mediaItem.mediaId.takeIf { it.isNotBlank() } ?: uri
            val title = mediaItem.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown audio"
            return MusicInfo(
                id = id,
                title = title,
                playbackUri = uri,
                requestHeaders = decodeHeaders(
                    mediaItem.mediaMetadata.extras?.getString(EXTRA_REQUEST_HEADERS_JSON)
                ),
                requiresAuthorization = mediaItem.mediaMetadata.extras
                    ?.getBoolean(EXTRA_REQUIRES_AUTHORIZATION, false)
                    ?: false
            )
        }

        private fun encodeHeaders(headers: Map<String, String>): String {
            return buildJsonObject {
                headers.forEach { (key, value) ->
                    put(key, JsonPrimitive(value))
                }
            }.toString()
        }

        private fun decodeHeaders(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) {
                return emptyMap()
            }
            return runCatching {
                val parsedJson: JsonObject = json.parseToJsonElement(raw).jsonObject
                buildMap {
                    parsedJson.entries.forEach { entry ->
                        put(entry.key, entry.value.jsonPrimitive.content)
                    }
                }
            }.getOrDefault(emptyMap())
        }
    }
}
