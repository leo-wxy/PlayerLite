package com.wxy.playerlite.playback.model

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PlayableItemSnapshot(
    override val id: String,
    override val songId: String? = null,
    override val title: String,
    override val artistText: String? = null,
    override val albumTitle: String? = null,
    override val coverUrl: String? = null,
    override val durationMs: Long = 0L,
    override val playbackUri: String,
    override val playbackContext: PlaybackContext? = null,
    override val previewClip: PlaybackPreviewClip? = null,
    override val requestHeaders: Map<String, String> = emptyMap(),
    override val requiresAuthorization: Boolean = false
) : PlayableItem {
    override fun toPlayableItem(): PlayableItemSnapshot = this

    override fun toMediaItem(statusText: String?): MediaItem {
        val normalizedTitle = title.ifBlank { "Unknown audio" }
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(normalizedTitle)
            .setArtist(artistText)
            .setAlbumTitle(albumTitle)
            .setExtras(buildExtras())

        coverUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { metadataBuilder.setArtworkUri(Uri.parse(it)) }

        if (!statusText.isNullOrBlank()) {
            metadataBuilder.setSubtitle(statusText)
        }

        return MediaItem.Builder()
            .setMediaId(id.ifBlank { playbackUri })
            .setUri(playbackUri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun buildExtras(): Bundle {
        return Bundle().apply {
            putString(EXTRA_PLAYBACK_URI, playbackUri)
            putString(EXTRA_REQUEST_HEADERS_JSON, encodeHeaders(requestHeaders))
            putBoolean(EXTRA_REQUIRES_AUTHORIZATION, requiresAuthorization)
            songId?.takeIf { it.isNotBlank() }?.let { putString(EXTRA_SONG_ID, it) }
            artistText?.takeIf { it.isNotBlank() }?.let { putString(EXTRA_ARTIST_TEXT, it) }
            albumTitle?.takeIf { it.isNotBlank() }?.let { putString(EXTRA_ALBUM_TITLE, it) }
            coverUrl?.takeIf { it.isNotBlank() }?.let { putString(EXTRA_COVER_URL, it) }
            if (durationMs > 0L) {
                putLong(EXTRA_DURATION_MS, durationMs)
            }
            playbackContext?.let { context ->
                putString(EXTRA_CONTEXT_SOURCE_TYPE, context.sourceType)
                context.sourceId?.takeIf { it.isNotBlank() }?.let {
                    putString(EXTRA_CONTEXT_SOURCE_ID, it)
                }
                context.sourceTitle?.takeIf { it.isNotBlank() }?.let {
                    putString(EXTRA_CONTEXT_SOURCE_TITLE, it)
                }
            }
            previewClip?.let { clip ->
                putLong(EXTRA_PREVIEW_CLIP_START_MS, clip.startMs)
                putLong(EXTRA_PREVIEW_CLIP_END_MS, clip.endMs)
            }
        }
    }

    companion object {
        private const val EXTRA_PLAYBACK_URI = "playback_uri"
        private const val EXTRA_REQUEST_HEADERS_JSON = "request_headers_json"
        private const val EXTRA_REQUIRES_AUTHORIZATION = "requires_authorization"
        private const val EXTRA_SONG_ID = "song_id"
        private const val EXTRA_ARTIST_TEXT = "artist_text"
        private const val EXTRA_ALBUM_TITLE = "album_title"
        private const val EXTRA_COVER_URL = "cover_url"
        private const val EXTRA_DURATION_MS = "duration_ms"
        private const val EXTRA_CONTEXT_SOURCE_TYPE = "context_source_type"
        private const val EXTRA_CONTEXT_SOURCE_ID = "context_source_id"
        private const val EXTRA_CONTEXT_SOURCE_TITLE = "context_source_title"
        private const val EXTRA_PREVIEW_CLIP_START_MS = "preview_clip_start_ms"
        private const val EXTRA_PREVIEW_CLIP_END_MS = "preview_clip_end_ms"

        private val json = Json { ignoreUnknownKeys = true }

        fun fromMediaItem(mediaItem: MediaItem): PlayableItemSnapshot? {
            val extras = mediaItem.mediaMetadata.extras
            val uri = extras?.getString(EXTRA_PLAYBACK_URI)
                ?: mediaItem.localConfiguration?.uri?.toString()
                ?: return null

            val context = extras?.getString(EXTRA_CONTEXT_SOURCE_TYPE)
                ?.takeIf { it.isNotBlank() }
                ?.let { sourceType ->
                    PlaybackContext(
                        sourceType = sourceType,
                        sourceId = extras.getString(EXTRA_CONTEXT_SOURCE_ID)?.takeIf { it.isNotBlank() },
                        sourceTitle = extras.getString(EXTRA_CONTEXT_SOURCE_TITLE)?.takeIf { it.isNotBlank() }
                    )
                }
            val clip = extras
                ?.takeIf {
                    it.containsKey(EXTRA_PREVIEW_CLIP_START_MS) || it.containsKey(EXTRA_PREVIEW_CLIP_END_MS)
                }
                ?.let {
                    PlaybackPreviewClip(
                        startMs = it.getLong(EXTRA_PREVIEW_CLIP_START_MS, 0L),
                        endMs = it.getLong(EXTRA_PREVIEW_CLIP_END_MS, 0L)
                    )
                }

            return PlayableItemSnapshot(
                id = mediaItem.mediaId.takeIf { it.isNotBlank() } ?: uri,
                songId = extras?.getString(EXTRA_SONG_ID)?.takeIf { it.isNotBlank() },
                title = mediaItem.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown audio",
                artistText = mediaItem.mediaMetadata.artist?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: extras?.getString(EXTRA_ARTIST_TEXT)?.takeIf { it.isNotBlank() },
                albumTitle = mediaItem.mediaMetadata.albumTitle?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: extras?.getString(EXTRA_ALBUM_TITLE)?.takeIf { it.isNotBlank() },
                coverUrl = mediaItem.mediaMetadata.artworkUri?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: extras?.getString(EXTRA_COVER_URL)?.takeIf { it.isNotBlank() },
                durationMs = extras?.getLong(EXTRA_DURATION_MS, 0L) ?: 0L,
                playbackUri = uri,
                playbackContext = context,
                previewClip = clip,
                requestHeaders = decodeHeaders(extras?.getString(EXTRA_REQUEST_HEADERS_JSON)),
                requiresAuthorization = extras?.getBoolean(EXTRA_REQUIRES_AUTHORIZATION, false) ?: false
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
