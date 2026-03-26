package com.wxy.playerlite.playlist.core

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistState
import com.wxy.playerlite.playback.model.PlaybackMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    fun updateItemsById(updatesById: Map<String, PlaylistItem>): PlaylistState {
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
