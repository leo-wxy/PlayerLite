package com.wxy.playerlite.playlist

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
    private val persistDebounceMs: Long = DEFAULT_PERSIST_DEBOUNCE_MS
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

        val filteredItems = decoded.items.filter(validator)
        val restored = decoded.copy(items = filteredItems).normalized()
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
                items = listOf(item),
                activeIndex = 0
            )
        )
    }

    fun addItem(item: PlaylistItem, makeActive: Boolean = true): PlaylistState {
        val nextItems = state.items + item
        val nextActiveIndex = when {
            nextItems.isEmpty() -> -1
            makeActive -> nextItems.lastIndex
            state.activeIndex in nextItems.indices -> state.activeIndex
            else -> 0
        }
        return updateState(
            state.copy(items = nextItems, activeIndex = nextActiveIndex).normalized()
        )
    }

    fun removeItemById(id: String): PlaylistState {
        val targetIndex = state.items.indexOfFirst { it.id == id }
        if (targetIndex < 0) {
            return state
        }
        return removeAt(targetIndex)
    }

    fun removeAt(index: Int): PlaylistState {
        if (index !in state.items.indices) {
            return state
        }

        val nextItems = state.items.toMutableList().also { it.removeAt(index) }
        val nextActiveIndex = when {
            nextItems.isEmpty() -> -1
            state.activeIndex > index -> state.activeIndex - 1
            state.activeIndex == index -> state.activeIndex.coerceAtMost(nextItems.lastIndex)
            else -> state.activeIndex
        }

        return updateState(
            state.copy(items = nextItems, activeIndex = nextActiveIndex).normalized()
        )
    }

    fun moveItem(fromIndex: Int, toIndex: Int): PlaylistState {
        if (fromIndex !in state.items.indices || toIndex !in state.items.indices || fromIndex == toIndex) {
            return state
        }

        val nextItems = state.items.toMutableList()
        val moving = nextItems.removeAt(fromIndex)
        nextItems.add(toIndex, moving)

        val nextActiveIndex = when {
            state.activeIndex == fromIndex -> toIndex
            fromIndex < state.activeIndex && toIndex >= state.activeIndex -> state.activeIndex - 1
            fromIndex > state.activeIndex && toIndex <= state.activeIndex -> state.activeIndex + 1
            else -> state.activeIndex
        }

        return updateState(
            state.copy(items = nextItems, activeIndex = nextActiveIndex).normalized()
        )
    }

    fun setActiveIndex(index: Int): PlaylistState {
        if (index !in state.items.indices || state.activeIndex == index) {
            return state
        }
        return updateState(state.copy(activeIndex = index).normalized())
    }

    fun moveToNext(): PlaylistState {
        val nextIndex = state.activeIndex + 1
        if (nextIndex !in state.items.indices) {
            return state
        }
        return updateState(state.copy(activeIndex = nextIndex).normalized())
    }

    fun moveToPrevious(): PlaylistState {
        val nextIndex = state.activeIndex - 1
        if (nextIndex !in state.items.indices) {
            return state
        }
        return updateState(state.copy(activeIndex = nextIndex).normalized())
    }

    fun flush() {
        persistJob?.cancel()
        persistJob = null
        persistNow()
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
        if (state.items.isEmpty()) {
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
        root.put("activeIndex", state.activeIndex)

        val items = JSONArray()
        state.items.forEach { item ->
            val itemJson = JSONObject()
            itemJson.put("id", item.id)
            itemJson.put("uri", item.uri)
            itemJson.put("displayName", item.displayName)
            items.put(itemJson)
        }
        root.put("items", items)

        return root.toString()
    }

    fun decode(raw: String): PlaylistState? {
        return try {
            val root = JSONObject(raw)
            val version = root.optInt("version", -1)
            if (version != PlaylistState.VERSION) {
                return null
            }

            val activeIndex = root.optInt("activeIndex", -1)
            val itemsJson = root.optJSONArray("items") ?: JSONArray()
            val items = buildList {
                for (i in 0 until itemsJson.length()) {
                    val itemJson = itemsJson.optJSONObject(i) ?: continue
                    val id = itemJson.optString("id", "").trim()
                    val uri = itemJson.optString("uri", "").trim()
                    val displayName = itemJson.optString("displayName", "").trim()
                    if (id.isEmpty() || uri.isEmpty()) {
                        continue
                    }
                    add(
                        PlaylistItem(
                            id = id,
                            uri = uri,
                            displayName = if (displayName.isNotEmpty()) displayName else "Unknown audio"
                        )
                    )
                }
            }

            PlaylistState(
                version = version,
                items = items,
                activeIndex = activeIndex
            ).normalized()
        } catch (_: JSONException) {
            null
        }
    }
}
