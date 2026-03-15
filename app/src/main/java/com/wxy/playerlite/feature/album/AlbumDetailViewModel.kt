package com.wxy.playerlite.feature.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackGateway
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class AlbumDetailUiState(
    val contentState: AlbumContentUiState = AlbumContentUiState.Loading,
    val dynamicState: AlbumDynamicUiState = AlbumDynamicUiState.Loading
)

internal sealed interface AlbumContentUiState {
    data object Loading : AlbumContentUiState

    data class Content(
        val content: AlbumDetailContent,
        val isLoadingMore: Boolean = false,
        val loadMoreErrorMessage: String? = null,
        val endReached: Boolean = false
    ) : AlbumContentUiState

    data class Error(
        val message: String
    ) : AlbumContentUiState
}

internal sealed interface AlbumDynamicUiState {
    data object Loading : AlbumDynamicUiState

    data class Content(
        val content: AlbumDynamicInfo
    ) : AlbumDynamicUiState

    data class Error(
        val message: String
    ) : AlbumDynamicUiState

    data object Empty : AlbumDynamicUiState
}

internal class AlbumDetailViewModel(
    private val albumId: String,
    private val repository: AlbumDetailRepository,
    private val playbackGateway: DetailPlaybackGateway,
    private val pageSize: Int = DEFAULT_ALBUM_TRACK_PAGE_SIZE
) : ViewModel() {
    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiStateFlow: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        loadContent()
        loadDynamic()
    }

    fun retry() {
        when (val contentState = _uiState.value.contentState) {
            AlbumContentUiState.Loading,
            is AlbumContentUiState.Error -> loadContent()

            is AlbumContentUiState.Content -> {
                if (contentState.loadMoreErrorMessage != null) {
                    loadContent(loadMore = true)
                }
            }
        }
        loadDynamic()
    }

    fun loadMoreTracks() {
        loadContent(loadMore = true)
    }

    fun playAll(): Boolean {
        return playTrack(0)
    }

    fun playTrack(index: Int): Boolean {
        val content = (_uiState.value.contentState as? AlbumContentUiState.Content)?.content
            ?: return false
        val request = buildAlbumPlaybackRequest(
            content = content,
            requestedActiveIndex = index
        ) ?: return false
        return playbackGateway.play(request)
    }

    private fun loadContent(loadMore: Boolean = false) {
        viewModelScope.launch {
            val currentContentState = _uiState.value.contentState
            val existingContent = (currentContentState as? AlbumContentUiState.Content)?.content
            if (loadMore) {
                val contentState = currentContentState as? AlbumContentUiState.Content ?: return@launch
                if (contentState.isLoadingMore || contentState.endReached) {
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    contentState = contentState.copy(
                        isLoadingMore = true,
                        loadMoreErrorMessage = null
                    )
                )
            } else if (_uiState.value.contentState !is AlbumContentUiState.Content) {
                _uiState.value = _uiState.value.copy(
                    contentState = AlbumContentUiState.Loading
                )
            }
            runCatching {
                repository.fetchAlbumContent(
                    albumId = albumId,
                    offset = if (loadMore) existingContent?.tracks?.size ?: 0 else 0,
                    limit = pageSize
                )
            }.onSuccess { pageContent ->
                val mergedContent = if (loadMore && existingContent != null) {
                    existingContent.copy(
                        albumId = pageContent.albumId,
                        title = pageContent.title,
                        artistText = pageContent.artistText,
                        description = pageContent.description,
                        coverUrl = pageContent.coverUrl,
                        company = pageContent.company,
                        publishTimeText = pageContent.publishTimeText,
                        trackCount = pageContent.trackCount,
                        tracks = existingContent.tracks + pageContent.tracks
                    )
                } else {
                    pageContent
                }
                _uiState.value = _uiState.value.copy(
                    contentState = AlbumContentUiState.Content(
                        content = mergedContent,
                        isLoadingMore = false,
                        loadMoreErrorMessage = null,
                        endReached = shouldMarkAlbumEndReached(
                            totalCount = mergedContent.trackCount,
                            loadedCount = mergedContent.tracks.size,
                            latestPageSize = pageContent.tracks.size,
                            pageSize = pageSize
                        )
                    )
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    contentState = if (loadMore && currentContentState is AlbumContentUiState.Content) {
                        currentContentState.copy(
                            isLoadingMore = false,
                            loadMoreErrorMessage = error.message ?: "专辑歌曲列表加载失败"
                        )
                    } else {
                        AlbumContentUiState.Error(
                            message = error.message ?: "专辑详情加载失败"
                        )
                    }
                )
            }
        }
    }

    private fun loadDynamic() {
        viewModelScope.launch {
            if (_uiState.value.dynamicState !is AlbumDynamicUiState.Content) {
                _uiState.value = _uiState.value.copy(
                    dynamicState = AlbumDynamicUiState.Loading
                )
            }
            runCatching {
                repository.fetchAlbumDynamic(albumId)
            }.onSuccess { content ->
                _uiState.value = _uiState.value.copy(
                    dynamicState = if (
                        content.commentCount == 0 &&
                        content.shareCount == 0 &&
                        content.subscribedCount == 0
                    ) {
                        AlbumDynamicUiState.Empty
                    } else {
                        AlbumDynamicUiState.Content(content)
                    }
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    dynamicState = AlbumDynamicUiState.Error(
                        message = error.message ?: "专辑动态信息加载失败"
                    )
                )
            }
        }
    }

    companion object {
        fun factory(
            albumId: String,
            repository: AlbumDetailRepository,
            playbackGateway: DetailPlaybackGateway
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    check(modelClass.isAssignableFrom(AlbumDetailViewModel::class.java))
                    @Suppress("UNCHECKED_CAST")
                    return AlbumDetailViewModel(
                        albumId = albumId,
                        repository = repository,
                        playbackGateway = playbackGateway
                    ) as T
                }
            }
        }
    }

    override fun onCleared() {
        playbackGateway.close()
        super.onCleared()
    }
}

private fun buildAlbumPlaybackRequest(
    content: AlbumDetailContent,
    requestedActiveIndex: Int
): DetailPlaybackRequest? {
    if (content.tracks.isEmpty()) {
        return null
    }
    val indexedItems = content.tracks.mapIndexedNotNull { index, track ->
        track.trackId.takeIf { it.isNotBlank() }?.let { trackId ->
            index to PlaylistItem(
                id = "album:${content.albumId}:$index:$trackId",
                displayName = track.title,
                songId = trackId,
                title = track.title,
                artistText = track.artistText,
                albumTitle = track.albumTitle,
                coverUrl = track.coverUrl ?: content.coverUrl,
                durationMs = track.durationMs,
                itemType = PlaylistItemType.ONLINE,
                contextType = "album",
                contextId = content.albumId,
                contextTitle = content.title
            )
        }
    }
    if (indexedItems.isEmpty()) {
        return null
    }
    val normalizedActiveIndex = indexedItems.indexOfFirst { it.first == requestedActiveIndex }
        .takeIf { it >= 0 }
        ?: 0
    return DetailPlaybackRequest(
        items = indexedItems.map { it.second },
        activeIndex = normalizedActiveIndex
    )
}

private fun shouldMarkAlbumEndReached(
    totalCount: Int,
    loadedCount: Int,
    latestPageSize: Int,
    pageSize: Int
): Boolean {
    return when {
        totalCount > 0 -> loadedCount >= totalCount
        latestPageSize == 0 -> true
        latestPageSize < pageSize -> true
        else -> false
    }
}
