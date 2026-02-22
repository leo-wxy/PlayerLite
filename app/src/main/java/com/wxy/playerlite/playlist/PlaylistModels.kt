package com.wxy.playerlite.playlist

data class PlaylistItem(
    val id: String,
    val uri: String,
    val displayName: String
)

data class PlaylistState(
    val version: Int = VERSION,
    val items: List<PlaylistItem> = emptyList(),
    val activeIndex: Int = -1
) {
    companion object {
        const val VERSION = 1

        fun empty(): PlaylistState = PlaylistState()
    }

    val activeItem: PlaylistItem?
        get() = items.getOrNull(activeIndex)

    fun normalized(): PlaylistState {
        if (items.isEmpty()) {
            return copy(version = VERSION, activeIndex = -1)
        }
        val normalizedActive = if (activeIndex in items.indices) {
            activeIndex
        } else {
            0
        }
        return copy(version = VERSION, activeIndex = normalizedActive)
    }
}
