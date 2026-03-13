package com.wxy.playerlite.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class PlaylistDetailUiState(
    val headerState: PlaylistHeaderUiState = PlaylistHeaderUiState.Loading,
    val tracksState: PlaylistTracksUiState = PlaylistTracksUiState.Loading
)

internal sealed interface PlaylistHeaderUiState {
    data object Loading : PlaylistHeaderUiState

    data class Content(
        val content: PlaylistHeaderContent
    ) : PlaylistHeaderUiState

    data class Error(
        val message: String
    ) : PlaylistHeaderUiState
}

internal sealed interface PlaylistTracksUiState {
    data object Loading : PlaylistTracksUiState

    data class Content(
        val items: List<PlaylistTrackRow>,
        val isLoadingMore: Boolean = false,
        val loadMoreErrorMessage: String? = null,
        val endReached: Boolean = false
    ) : PlaylistTracksUiState

    data class Error(
        val message: String
    ) : PlaylistTracksUiState

    data object Empty : PlaylistTracksUiState
}

internal class PlaylistDetailViewModel(
    private val playlistId: String,
    private val repository: PlaylistDetailRepository,
    private val pageSize: Int = DEFAULT_DETAIL_TRACK_PAGE_SIZE
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiStateFlow: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        loadHeader()
        loadTracks()
    }

    fun retry() {
        if (_uiState.value.headerState !is PlaylistHeaderUiState.Content) {
            loadHeader()
        }
        when (val tracksState = _uiState.value.tracksState) {
            PlaylistTracksUiState.Empty,
            PlaylistTracksUiState.Loading,
            is PlaylistTracksUiState.Error -> loadTracks()

            is PlaylistTracksUiState.Content -> {
                if (tracksState.loadMoreErrorMessage != null) {
                    loadTracks(loadMore = true)
                }
            }
        }
    }

    fun loadMoreTracks() {
        loadTracks(loadMore = true)
    }

    private fun loadHeader() {
        viewModelScope.launch {
            if (_uiState.value.headerState !is PlaylistHeaderUiState.Content) {
                _uiState.value = _uiState.value.copy(
                    headerState = PlaylistHeaderUiState.Loading
                )
            }
            runCatching {
                repository.fetchPlaylistHeader(playlistId)
            }.onSuccess { content ->
                val tracksState = when (val currentTracksState = _uiState.value.tracksState) {
                    is PlaylistTracksUiState.Content -> currentTracksState.copy(
                        endReached = currentTracksState.items.size >= content.trackCount
                    )

                    else -> currentTracksState
                }
                _uiState.value = _uiState.value.copy(
                    headerState = PlaylistHeaderUiState.Content(content),
                    tracksState = tracksState
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    headerState = PlaylistHeaderUiState.Error(
                        message = error.message ?: "歌单详情加载失败"
                    )
                )
            }
        }
    }

    private fun loadTracks(loadMore: Boolean = false) {
        viewModelScope.launch {
            val currentTracksState = _uiState.value.tracksState
            val existingItems = (currentTracksState as? PlaylistTracksUiState.Content)?.items.orEmpty()
            if (loadMore) {
                val contentState = currentTracksState as? PlaylistTracksUiState.Content ?: return@launch
                if (contentState.isLoadingMore || contentState.endReached) {
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    tracksState = contentState.copy(
                        isLoadingMore = true,
                        loadMoreErrorMessage = null
                    )
                )
            } else if (currentTracksState !is PlaylistTracksUiState.Content) {
                _uiState.value = _uiState.value.copy(
                    tracksState = PlaylistTracksUiState.Loading
                )
            }
            runCatching {
                repository.fetchPlaylistTracks(
                    playlistId = playlistId,
                    offset = if (loadMore) existingItems.size else 0,
                    limit = pageSize
                )
            }.onSuccess { items ->
                val totalCount = (_uiState.value.headerState as? PlaylistHeaderUiState.Content)
                    ?.content
                    ?.trackCount
                _uiState.value = _uiState.value.copy(
                    tracksState = if (!loadMore && items.isEmpty()) {
                        PlaylistTracksUiState.Empty
                    } else {
                        val mergedItems = if (loadMore) {
                            existingItems + items
                        } else {
                            items
                        }
                        PlaylistTracksUiState.Content(
                            items = mergedItems,
                            isLoadingMore = false,
                            loadMoreErrorMessage = null,
                            endReached = shouldMarkPlaylistEndReached(
                                totalCount = totalCount,
                                loadedCount = mergedItems.size,
                                latestPageSize = items.size,
                                pageSize = pageSize
                            )
                        )
                    }
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    tracksState = if (loadMore && currentTracksState is PlaylistTracksUiState.Content) {
                        currentTracksState.copy(
                            isLoadingMore = false,
                            loadMoreErrorMessage = error.message ?: "歌曲列表加载失败"
                        )
                    } else {
                        PlaylistTracksUiState.Error(
                            message = error.message ?: "歌曲列表加载失败"
                        )
                    }
                )
            }
        }
    }

    companion object {
        fun factory(
            playlistId: String,
            repository: PlaylistDetailRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    check(modelClass.isAssignableFrom(PlaylistDetailViewModel::class.java))
                    @Suppress("UNCHECKED_CAST")
                    return PlaylistDetailViewModel(
                        playlistId = playlistId,
                        repository = repository
                    ) as T
                }
            }
        }
    }
}

private fun shouldMarkPlaylistEndReached(
    totalCount: Int?,
    loadedCount: Int,
    latestPageSize: Int,
    pageSize: Int
): Boolean {
    return when {
        totalCount != null && totalCount > 0 -> loadedCount >= totalCount
        latestPageSize == 0 -> true
        latestPageSize < pageSize -> true
        else -> false
    }
}
