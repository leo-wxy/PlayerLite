package com.wxy.playerlite.core.playlist

import com.wxy.playerlite.playback.model.PlaybackMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class PlaylistController(
    private val storage: PlaylistStorage,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val storageKey: String = STORAGE_KEY,
    private val persistDebounceMs: Long = DEFAULT_PERSIST_DEBOUNCE_MS,
    private val orderShuffler: (List<String>) -> List<String> = { ids -> ids.shuffled() }
) {
    var state: PlaylistState = PlaylistState.empty()
        private set

    private var persistJob: Job? = null

    fun restore(validator: (PlaylistItem) -> Boolean): PlaylistState {
        val raw = storage.read(storageKey)
        if (raw.isNullOrBlank()) {
            state = PlaylistState.empty()
            return state
        }

        val decoded = PlaylistStateCodec.decode(raw)
        if (decoded == null) {
            storage.remove(storageKey)
            state = PlaylistState.empty()
            return state
        }

        val filteredItems = decoded.originalItems.filter(validator)
        val restored = decoded.copy(originalItems = filteredItems).normalized()
        state = restored

        if (restored != decoded) {
            persistNow()
        }

        return state
    }

    fun replaceWithSingle(item: PlaylistItem): PlaylistState {
        return updateState(
            PlaylistState(
                version = PlaylistState.VERSION,
                originalItems = listOf(item),
                shuffledOrderIds = listOf(item.id),
                activeItemId = item.id
            ).normalized()
        )
    }

    fun replaceAll(
        items: List<PlaylistItem>,
        activeIndex: Int = 0
    ): PlaylistState {
        if (items.isEmpty()) {
            return updateState(
                PlaylistState.empty().copy(
                    playbackMode = state.playbackMode,
                    showOriginalOrderInShuffle = state.showOriginalOrderInShuffle
                )
            )
        }
        val normalizedActiveIndex = activeIndex.coerceIn(0, items.lastIndex)
        val activeItemId = items[normalizedActiveIndex].id
        val shuffledOrderIds = when (state.playbackMode) {
            PlaybackMode.SHUFFLE -> buildShuffleOrder(items.map { it.id }, activeItemId)
            else -> items.map { it.id }
        }
        return updateState(
            PlaylistState(
                version = PlaylistState.VERSION,
                originalItems = items,
                shuffledOrderIds = shuffledOrderIds,
                activeItemId = activeItemId,
                playbackMode = state.playbackMode,
                showOriginalOrderInShuffle = state.showOriginalOrderInShuffle
            ).normalized()
        )
    }

    fun addItem(item: PlaylistItem, makeActive: Boolean = true): PlaylistState {
        val nextItems = state.originalItems + item
        val nextActiveItemId = when {
            nextItems.isEmpty() -> null
            makeActive -> item.id
            state.activeItemId in nextItems.map { it.id } -> state.activeItemId
            else -> nextItems.first().id
        }
        val nextShuffleOrder = when (state.playbackMode) {
            PlaybackMode.SHUFFLE -> PlaylistState.normalizedPlaybackOrderIds(
                nextItems,
                state.shuffledOrderIds + item.id
            )

            else -> PlaylistState.normalizedPlaybackOrderIds(nextItems, state.shuffledOrderIds)
        }
        return updateState(
            state.copy(
                originalItems = nextItems,
                shuffledOrderIds = nextShuffleOrder,
                activeItemId = nextActiveItemId
            ).normalized()
        )
    }

    fun removeItemById(id: String): PlaylistState {
        val targetIndex = state.originalItems.indexOfFirst { it.id == id }
        if (targetIndex < 0) {
            return state
        }
        return removeAt(targetIndex)
    }

    fun removeAt(index: Int): PlaylistState {
        if (index !in state.originalItems.indices) {
            return state
        }

        val nextItems = state.originalItems.toMutableList().also { it.removeAt(index) }
        val removedId = state.originalItems[index].id
        val nextActiveId = when {
            nextItems.isEmpty() -> null
            state.activeItemId == removedId -> nextItems.getOrNull(index)?.id ?: nextItems.last().id
            else -> state.activeItemId
        }

        return updateState(
            state.copy(
                originalItems = nextItems,
                shuffledOrderIds = state.shuffledOrderIds.filterNot { it == removedId },
                activeItemId = nextActiveId
            ).normalized()
        )
    }

    fun moveItem(fromIndex: Int, toIndex: Int): PlaylistState {
        if (fromIndex !in state.originalItems.indices || toIndex !in state.originalItems.indices || fromIndex == toIndex) {
            return state
        }

        val nextItems = state.originalItems.toMutableList()
        val moving = nextItems.removeAt(fromIndex)
        nextItems.add(toIndex, moving)

        return updateState(
            state.copy(originalItems = nextItems).normalized()
        )
    }

    fun setActiveIndex(index: Int): PlaylistState {
        if (index !in state.originalItems.indices) {
            return state
        }
        val nextActiveId = state.originalItems[index].id
        if (state.activeItemId == nextActiveId) {
            return state
        }
        return updateState(state.copy(activeItemId = nextActiveId).normalized())
    }

    fun setActiveItemId(itemId: String): PlaylistState {
        if (state.originalItems.none { it.id == itemId } || state.activeItemId == itemId) {
            return state
        }
        return updateState(state.copy(activeItemId = itemId).normalized())
    }

    fun updateItemsById(
        updatesById: Map<String, PlaylistItem>
    ): PlaylistState {
        if (updatesById.isEmpty()) {
            return state
        }
        val nextItems = state.originalItems.map { item ->
            updatesById[item.id] ?: item
        }
        if (nextItems == state.originalItems) {
            return state
        }
        return updateState(
            state.copy(originalItems = nextItems).normalized()
        )
    }

    fun setPlaybackMode(playbackMode: PlaybackMode): PlaylistState {
        if (state.playbackMode == playbackMode) {
            return state
        }
        val nextShuffleOrder = if (playbackMode == PlaybackMode.SHUFFLE) {
            buildShuffleOrder(state.originalItems.map { it.id }, state.activeItemId)
        } else {
            state.shuffledOrderIds
        }
        return updateState(
            state.copy(
                playbackMode = playbackMode,
                shuffledOrderIds = nextShuffleOrder
            ).normalized()
        )
    }

    fun setShowOriginalOrderInShuffle(show: Boolean): PlaylistState {
        if (state.showOriginalOrderInShuffle == show) {
            return state
        }
        return updateState(state.copy(showOriginalOrderInShuffle = show).normalized())
    }

    fun moveToNext(): PlaylistState {
        val nextIndex = state.activeIndex + 1
        if (nextIndex !in state.originalItems.indices) {
            return state
        }
        return setActiveIndex(nextIndex)
    }

    fun moveToPrevious(): PlaylistState {
        val nextIndex = state.activeIndex - 1
        if (nextIndex !in state.originalItems.indices) {
            return state
        }
        return setActiveIndex(nextIndex)
    }

    fun flush() {
        persistJob?.cancel()
        persistJob = null
        persistNow()
    }

    private fun buildShuffleOrder(ids: List<String>, activeItemId: String?): List<String> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        val shuffled = orderShuffler(ids)
        val normalized = PlaylistState.normalizedPlaybackOrderIds(
            items = ids.map { id -> PlaylistItem(id = id, uri = id, displayName = id) },
            shuffledOrderIds = shuffled
        )
        return if (activeItemId != null && activeItemId in normalized) {
            normalized
        } else {
            normalized
        }
    }

    private fun updateState(next: PlaylistState): PlaylistState {
        if (next == state) {
            return state
        }
        state = next
        schedulePersist()
        return state
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch(ioDispatcher) {
            delay(persistDebounceMs)
            persistNow()
        }
    }

    private fun persistNow() {
        if (state.originalItems.isEmpty()) {
            storage.remove(storageKey)
            return
        }
        storage.write(storageKey, PlaylistStateCodec.encode(state))
    }

    companion object {
        const val STORAGE_KEY = "playlist_state_v1"
        private const val DEFAULT_PERSIST_DEBOUNCE_MS = 250L
    }
}

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
            item.primaryArtistId?.takeIf { it.isNotBlank() }?.let { itemJson.put("primaryArtistId", it) }
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
            when (val version = root.optInt("version", -1)) {
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
