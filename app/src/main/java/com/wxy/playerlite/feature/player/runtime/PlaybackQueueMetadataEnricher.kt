package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playback.SongDetailRepository

internal class PlaybackQueueMetadataEnricher(
    private val repository: SongDetailRepository,
    private val pageSize: Int = DEFAULT_PAGE_SIZE
) {
    suspend fun enrich(
        items: List<PlaylistItem>,
        activeIndex: Int,
        onPageResolved: (Map<String, PlaylistItem>) -> Unit
    ) {
        val onlineItems = items.filter { it.isOnline && !it.songId.isNullOrBlank() }
        if (onlineItems.isEmpty()) {
            return
        }

        val activeItem = items.getOrNull(activeIndex)
            ?.takeIf { it.isOnline && !it.songId.isNullOrBlank() }
        val remainingItems = onlineItems.filterNot { it.id == activeItem?.id }

        activeItem?.let { item ->
            resolvePage(listOf(item), onPageResolved)
        }

        remainingItems
            .chunked(pageSize.coerceAtLeast(1))
            .forEach { page ->
                resolvePage(page, onPageResolved)
            }
    }

    private suspend fun resolvePage(
        pageItems: List<PlaylistItem>,
        onPageResolved: (Map<String, PlaylistItem>) -> Unit
    ) {
        if (pageItems.isEmpty()) {
            return
        }
        val resolvedBySongId = repository.fetchSongs(
            songIds = pageItems.mapNotNull { it.songId }
        ).associateBy { it.songId }

        val updates = buildMap<String, PlaylistItem> {
            pageItems.forEach { item ->
                val songId = item.songId ?: return@forEach
                val resolved = resolvedBySongId[songId] ?: return@forEach
                put(
                    item.id,
                    item.copy(
                        displayName = resolved.title.ifBlank { item.displayName },
                        title = resolved.title.ifBlank { item.title },
                        artistText = resolved.artistText ?: item.artistText,
                        primaryArtistId = resolved.artistIds.firstOrNull() ?: item.primaryArtistId,
                        albumTitle = resolved.albumTitle ?: item.albumTitle,
                        coverUrl = resolved.coverUrl ?: item.coverUrl,
                        durationMs = resolved.durationMs.takeIf { it > 0L } ?: item.durationMs
                    )
                )
            }
        }
        if (updates.isNotEmpty()) {
            onPageResolved(updates)
        }
    }

    private companion object {
        private const val DEFAULT_PAGE_SIZE = 50
    }
}
