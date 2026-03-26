package com.wxy.playerlite.playlist.core

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.core.playlist.PlaylistState
import com.wxy.playerlite.playback.model.PlaybackMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object PlaylistStateCodec {
    fun encode(state: PlaylistState): String {
        val root = JSONObject()
        root.put("version", PlaylistState.VERSION)
        root.put("activeItemId", state.activeItemId)
        root.put("playbackMode", state.playbackMode.wireValue)
        root.put("showOriginalOrderInShuffle", state.showOriginalOrderInShuffle)

        val originalItems = JSONArray()
        state.originalItems.forEach { item ->
            val itemJson = JSONObject()
            itemJson.put("id", item.id)
            itemJson.put("uri", item.uri)
            itemJson.put("displayName", item.displayName)
            itemJson.put("itemType", item.itemType.wireValue)
            item.songId?.takeIf { it.isNotBlank() }?.let { itemJson.put("songId", it) }
            item.title.takeIf { it.isNotBlank() && it != item.displayName }?.let {
                itemJson.put("title", it)
            }
            item.artistText?.takeIf { it.isNotBlank() }?.let { itemJson.put("artistText", it) }
            item.primaryArtistId?.takeIf { it.isNotBlank() }?.let {
                itemJson.put("primaryArtistId", it)
            }
            item.albumTitle?.takeIf { it.isNotBlank() }?.let { itemJson.put("albumTitle", it) }
            item.coverUrl?.takeIf { it.isNotBlank() }?.let { itemJson.put("coverUrl", it) }
            if (item.durationMs > 0L) {
                itemJson.put("durationMs", item.durationMs)
            }
            item.contextType?.takeIf { it.isNotBlank() }?.let { itemJson.put("contextType", it) }
            item.contextId?.takeIf { it.isNotBlank() }?.let { itemJson.put("contextId", it) }
            item.contextTitle?.takeIf { it.isNotBlank() }?.let { itemJson.put("contextTitle", it) }
            originalItems.put(itemJson)
        }
        root.put("originalItems", originalItems)
        root.put("shuffledOrderIds", JSONArray(state.shuffledOrderIds))

        return root.toString()
    }

    fun decode(raw: String): PlaylistState? {
        return try {
            val root = JSONObject(raw)
            when (root.optInt("version", -1)) {
                1 -> decodeLegacyV1(root)
                2 -> decodeV2(root)
                PlaylistState.VERSION -> decodeV3(root)
                else -> null
            }
        } catch (_: JSONException) {
            null
        }
    }

    private fun decodeLegacyV1(root: JSONObject): PlaylistState {
        val activeIndex = root.optInt("activeIndex", -1)
        val items = readItems(root.optJSONArray("items") ?: JSONArray())
        return PlaylistState(
            version = PlaylistState.VERSION,
            originalItems = items,
            shuffledOrderIds = items.map { it.id },
            activeItemId = items.getOrNull(activeIndex)?.id,
            playbackMode = PlaybackMode.LIST_LOOP,
            showOriginalOrderInShuffle = false
        ).normalized()
    }

    private fun decodeV2(root: JSONObject): PlaylistState {
        return decodeCurrentShape(root)
    }

    private fun decodeV3(root: JSONObject): PlaylistState {
        return decodeCurrentShape(root)
    }

    private fun decodeCurrentShape(root: JSONObject): PlaylistState {
        val originalItems = readItems(root.optJSONArray("originalItems") ?: JSONArray())
        val shuffledOrderIds = buildList {
            val idsJson = root.optJSONArray("shuffledOrderIds") ?: JSONArray()
            for (i in 0 until idsJson.length()) {
                idsJson.optString(i, "").trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
        return PlaylistState(
            version = PlaylistState.VERSION,
            originalItems = originalItems,
            shuffledOrderIds = shuffledOrderIds,
            activeItemId = root.optString("activeItemId", "").trim().ifEmpty { null },
            playbackMode = PlaybackMode.fromWireValue(
                root.takeIf { it.has("playbackMode") }?.optString("playbackMode")
            ),
            showOriginalOrderInShuffle = root.optBoolean("showOriginalOrderInShuffle", false)
        ).normalized()
    }

    private fun readItems(itemsJson: JSONArray): List<PlaylistItem> {
        return buildList {
            for (i in 0 until itemsJson.length()) {
                val itemJson = itemsJson.optJSONObject(i) ?: continue
                val id = itemJson.optString("id", "").trim()
                val uri = itemJson.optString("uri", "").trim()
                val displayName = itemJson.optString("displayName", "").trim()
                val songId = itemJson.optString("songId", "").trim().ifEmpty { null }
                val itemType = PlaylistItemType.fromWireValue(itemJson.optString("itemType", ""))
                    ?: inferLegacyItemType(uri = uri, songId = songId)
                if (id.isEmpty()) {
                    continue
                }
                if (itemType == PlaylistItemType.LOCAL && uri.isEmpty()) {
                    continue
                }
                if (itemType == PlaylistItemType.ONLINE && songId.isNullOrBlank()) {
                    continue
                }
                val resolvedDisplayName = if (displayName.isNotEmpty()) displayName else "Unknown audio"
                add(
                    PlaylistItem(
                        id = id,
                        uri = uri,
                        displayName = resolvedDisplayName,
                        songId = songId,
                        title = itemJson.optString("title", "").trim().ifEmpty { resolvedDisplayName },
                        artistText = itemJson.optString("artistText", "").trim().ifEmpty { null },
                        primaryArtistId = itemJson.optString("primaryArtistId", "").trim().ifEmpty { null },
                        albumTitle = itemJson.optString("albumTitle", "").trim().ifEmpty { null },
                        coverUrl = itemJson.optString("coverUrl", "").trim().ifEmpty { null },
                        durationMs = itemJson.optLong("durationMs", 0L).coerceAtLeast(0L),
                        itemType = itemType,
                        contextType = itemJson.optString("contextType", "").trim().ifEmpty { null },
                        contextId = itemJson.optString("contextId", "").trim().ifEmpty { null },
                        contextTitle = itemJson.optString("contextTitle", "").trim().ifEmpty { null }
                    )
                )
            }
        }
    }

    private fun inferLegacyItemType(
        uri: String,
        songId: String?
    ): PlaylistItemType {
        return if (!songId.isNullOrBlank() && isLikelyRemoteUri(uri)) {
            PlaylistItemType.ONLINE
        } else if (!songId.isNullOrBlank() && uri.isBlank()) {
            PlaylistItemType.ONLINE
        } else {
            PlaylistItemType.LOCAL
        }
    }

    private fun isLikelyRemoteUri(uri: String): Boolean {
        val normalized = uri.trim().lowercase()
        return normalized.startsWith("http://") || normalized.startsWith("https://")
    }
}
