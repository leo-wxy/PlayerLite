package com.wxy.playerlite.core.playlist

import com.wxy.playerlite.playback.model.PlaybackMode

data class PlaylistItem(
    val id: String,
    val uri: String,
    val displayName: String
)

data class PlaylistState(
    val version: Int = VERSION,
    val originalItems: List<PlaylistItem> = emptyList(),
    val shuffledOrderIds: List<String> = emptyList(),
    val activeItemId: String? = null,
    val playbackMode: PlaybackMode = PlaybackMode.LIST_LOOP,
    val showOriginalOrderInShuffle: Boolean = false
) {
    companion object {
        const val VERSION = 2

        fun empty(): PlaylistState = PlaylistState()

        fun normalizedPlaybackOrderIds(
            items: List<PlaylistItem>,
            shuffledOrderIds: List<String>
        ): List<String> {
            val validIds = items.map { it.id }
            if (validIds.isEmpty()) {
                return emptyList()
            }
            val filtered = shuffledOrderIds
                .filter { it in validIds }
                .distinct()
            return filtered + validIds.filterNot { it in filtered }
        }

        fun orderItemsByIds(
            items: List<PlaylistItem>,
            orderIds: List<String>
        ): List<PlaylistItem> {
            val itemById = items.associateBy { it.id }
            return orderIds.mapNotNull(itemById::get)
        }
    }

    val items: List<PlaylistItem>
        get() = originalItems

    val activeIndex: Int
        get() = originalItems.indexOfFirst { it.id == activeItemId }

    val activeItem: PlaylistItem?
        get() = originalItems.firstOrNull { it.id == activeItemId }

    val playbackOrderIds: List<String>
        get() = when (playbackMode) {
            PlaybackMode.SHUFFLE -> normalizedPlaybackOrderIds(originalItems, shuffledOrderIds)
            else -> originalItems.map { it.id }
        }

    val playbackItems: List<PlaylistItem>
        get() = orderItemsByIds(originalItems, playbackOrderIds)

    val playbackActiveIndex: Int
        get() = playbackOrderIds.indexOf(activeItemId)

    val displayItems: List<PlaylistItem>
        get() = if (playbackMode == PlaybackMode.SHUFFLE && !showOriginalOrderInShuffle) {
            playbackItems
        } else {
            originalItems
        }

    val displayActiveIndex: Int
        get() = displayItems.indexOfFirst { it.id == activeItemId }

    val canReorderDisplayItems: Boolean
        get() = playbackMode != PlaybackMode.SHUFFLE || showOriginalOrderInShuffle

    fun normalized(): PlaylistState {
        if (originalItems.isEmpty()) {
            return copy(
                version = VERSION,
                shuffledOrderIds = emptyList(),
                activeItemId = null
            )
        }

        val normalizedIds = normalizedPlaybackOrderIds(originalItems, shuffledOrderIds)
        val normalizedActiveId = activeItemId
            ?.takeIf { id -> originalItems.any { it.id == id } }
            ?: originalItems.first().id

        return copy(
            version = VERSION,
            shuffledOrderIds = normalizedIds,
            activeItemId = normalizedActiveId
        )
    }
}
