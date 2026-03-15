package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.core.playlist.PlaylistController
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistState

internal class PlaylistSessionCoordinator(
    private val controller: PlaylistController
) {
    var state: PlaylistState = PlaylistState.empty()
        private set

    val activeItem: PlaylistItem?
        get() = state.activeItem

    val activeIndex: Int
        get() = state.displayActiveIndex

    val items: List<PlaylistItem>
        get() = state.displayItems

    val playbackItems: List<PlaylistItem>
        get() = state.playbackItems

    val playbackActiveIndex: Int
        get() = state.playbackActiveIndex

    val originalItems: List<PlaylistItem>
        get() = state.originalItems

    val canReorderCurrentView: Boolean
        get() = state.canReorderDisplayItems

    fun containsIndex(index: Int): Boolean {
        return index in state.displayItems.indices
    }

    fun itemAt(index: Int): PlaylistItem? {
        return state.displayItems.getOrNull(index)
    }

    fun restore(validator: (PlaylistItem) -> Boolean): PlaylistState {
        state = controller.restore(validator)
        return state
    }

    fun addItem(item: PlaylistItem, makeActive: Boolean = true): PlaylistState {
        state = controller.addItem(item, makeActive)
        return state
    }

    fun replaceAll(
        items: List<PlaylistItem>,
        activeIndex: Int
    ): PlaylistState {
        state = controller.replaceAll(items, activeIndex)
        return state
    }

    fun removeAt(index: Int): PlaylistState {
        val item = itemAt(index) ?: return state
        state = controller.removeItemById(item.id)
        return state
    }

    fun removeItemById(id: String): PlaylistState {
        state = controller.removeItemById(id)
        return state
    }

    fun moveItem(fromIndex: Int, toIndex: Int): PlaylistState {
        if (!state.canReorderDisplayItems) {
            return state
        }
        state = controller.moveItem(fromIndex, toIndex)
        return state
    }

    fun setActiveIndex(index: Int): PlaylistState {
        val item = itemAt(index) ?: return state
        state = controller.setActiveItemId(item.id)
        return state
    }

    fun setActiveItemId(itemId: String): PlaylistState {
        state = controller.setActiveItemId(itemId)
        return state
    }

    fun updateItemsById(updatesById: Map<String, PlaylistItem>): PlaylistState {
        state = controller.updateItemsById(updatesById)
        return state
    }

    fun setPlaybackMode(playbackMode: com.wxy.playerlite.playback.model.PlaybackMode): PlaylistState {
        state = controller.setPlaybackMode(playbackMode)
        return state
    }

    fun setShowOriginalOrderInShuffle(show: Boolean): PlaylistState {
        state = controller.setShowOriginalOrderInShuffle(show)
        return state
    }

    fun moveToNext(): PlaylistState {
        state = controller.moveToNext()
        return state
    }

    fun flush() {
        controller.flush()
    }
}
