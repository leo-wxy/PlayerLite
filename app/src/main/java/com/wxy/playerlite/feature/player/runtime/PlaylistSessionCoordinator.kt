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
        get() = state.activeIndex

    val items: List<PlaylistItem>
        get() = state.items

    fun containsIndex(index: Int): Boolean {
        return index in state.items.indices
    }

    fun itemAt(index: Int): PlaylistItem? {
        return state.items.getOrNull(index)
    }

    fun restore(validator: (PlaylistItem) -> Boolean): PlaylistState {
        state = controller.restore(validator)
        return state
    }

    fun addItem(item: PlaylistItem, makeActive: Boolean = true): PlaylistState {
        state = controller.addItem(item, makeActive)
        return state
    }

    fun removeAt(index: Int): PlaylistState {
        state = controller.removeAt(index)
        return state
    }

    fun removeItemById(id: String): PlaylistState {
        state = controller.removeItemById(id)
        return state
    }

    fun moveItem(fromIndex: Int, toIndex: Int): PlaylistState {
        state = controller.moveItem(fromIndex, toIndex)
        return state
    }

    fun setActiveIndex(index: Int): PlaylistState {
        state = controller.setActiveIndex(index)
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
