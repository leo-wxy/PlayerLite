package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.core.playlist.PlaylistItem

data class DetailPlaybackRequest(
    val items: List<PlaylistItem>,
    val activeIndex: Int
)

interface DetailPlaybackGateway : AutoCloseable {
    fun play(request: DetailPlaybackRequest): Boolean

    override fun close() = Unit
}
