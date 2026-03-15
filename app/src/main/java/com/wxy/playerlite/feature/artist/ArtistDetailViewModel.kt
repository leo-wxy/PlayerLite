package com.wxy.playerlite.feature.artist

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackGateway
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ArtistDetailUiState(
    val headerState: ArtistDetailHeaderUiState = ArtistDetailHeaderUiState.Loading,
    val encyclopediaState: ArtistEncyclopediaUiState = ArtistEncyclopediaUiState.Loading,
    val hotSongsState: ArtistHotSongsUiState = ArtistHotSongsUiState.Loading
)

internal sealed interface ArtistDetailHeaderUiState {
    data object Loading : ArtistDetailHeaderUiState

    data class Content(
        val content: ArtistDetailContent
    ) : ArtistDetailHeaderUiState

    data class Error(
        val message: String
    ) : ArtistDetailHeaderUiState
}

internal sealed interface ArtistEncyclopediaUiState {
    data object Loading : ArtistEncyclopediaUiState

    data class Content(
        val content: ArtistEncyclopediaContent
    ) : ArtistEncyclopediaUiState

    data class Error(
        val message: String
    ) : ArtistEncyclopediaUiState

    data object Empty : ArtistEncyclopediaUiState
}

internal sealed interface ArtistHotSongsUiState {
    data object Loading : ArtistHotSongsUiState

    data class Content(
        val items: List<ArtistHotSongRow>
    ) : ArtistHotSongsUiState

    data class Error(
        val message: String
    ) : ArtistHotSongsUiState

    data object Empty : ArtistHotSongsUiState
}

internal class ArtistDetailViewModel(
    private val artistId: String,
    private val repository: ArtistDetailRepository,
    private val playbackGateway: DetailPlaybackGateway
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiStateFlow: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    init {
        loadHeader()
        loadEncyclopedia()
        loadHotSongs()
    }

    fun retry() {
        if (_uiState.value.headerState !is ArtistDetailHeaderUiState.Content) {
            loadHeader()
        }
        loadEncyclopedia()
        loadHotSongs()
    }

    fun playAll(): Boolean {
        return playTrack(0)
    }

    fun playTrack(index: Int): Boolean {
        val header = (_uiState.value.headerState as? ArtistDetailHeaderUiState.Content)?.content
            ?: return false
        val hotSongs = (_uiState.value.hotSongsState as? ArtistHotSongsUiState.Content)?.items.orEmpty()
        val request = buildArtistPlaybackRequest(
            header = header,
            hotSongs = hotSongs,
            requestedActiveIndex = index
        ) ?: return false
        return playbackGateway.play(request)
    }

    private fun loadHeader() {
        viewModelScope.launch {
            if (_uiState.value.headerState !is ArtistDetailHeaderUiState.Content) {
                _uiState.value = _uiState.value.copy(
                    headerState = ArtistDetailHeaderUiState.Loading
                )
            }
            runCatching {
                repository.fetchArtistDetail(artistId)
            }.onSuccess { content ->
                _uiState.value = _uiState.value.copy(
                    headerState = ArtistDetailHeaderUiState.Content(content)
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    headerState = ArtistDetailHeaderUiState.Error(
                        message = error.message ?: "歌手详情加载失败"
                    )
                )
            }
        }
    }

    private fun loadEncyclopedia() {
        viewModelScope.launch {
            if (_uiState.value.encyclopediaState !is ArtistEncyclopediaUiState.Content) {
                _uiState.value = _uiState.value.copy(
                    encyclopediaState = ArtistEncyclopediaUiState.Loading
                )
            }
            runCatching {
                repository.fetchArtistEncyclopedia(artistId)
            }.onSuccess { content ->
                _uiState.value = _uiState.value.copy(
                    encyclopediaState = if (content.summary.isBlank() && content.sections.isEmpty()) {
                        ArtistEncyclopediaUiState.Empty
                    } else {
                        ArtistEncyclopediaUiState.Content(content)
                    }
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    encyclopediaState = ArtistEncyclopediaUiState.Error(
                        message = error.message ?: "歌手百科加载失败"
                    )
                )
            }
        }
    }

    private fun loadHotSongs() {
        viewModelScope.launch {
            if (_uiState.value.hotSongsState !is ArtistHotSongsUiState.Content) {
                _uiState.value = _uiState.value.copy(
                    hotSongsState = ArtistHotSongsUiState.Loading
                )
            }
            runCatching {
                repository.fetchArtistHotSongs(artistId)
            }.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    hotSongsState = if (items.isEmpty()) {
                        ArtistHotSongsUiState.Empty
                    } else {
                        ArtistHotSongsUiState.Content(items)
                    }
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    hotSongsState = ArtistHotSongsUiState.Error(
                        message = error.message ?: "热门歌曲加载失败"
                    )
                )
            }
        }
    }

    companion object {
        fun factory(
            artistId: String,
            repository: ArtistDetailRepository,
            playbackGateway: DetailPlaybackGateway
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    check(modelClass.isAssignableFrom(ArtistDetailViewModel::class.java))
                    @Suppress("UNCHECKED_CAST")
                    return ArtistDetailViewModel(
                        artistId = artistId,
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

private fun buildArtistPlaybackRequest(
    header: ArtistDetailContent,
    hotSongs: List<ArtistHotSongRow>,
    requestedActiveIndex: Int
): DetailPlaybackRequest? {
    if (hotSongs.isEmpty()) {
        return null
    }
    val indexedItems = hotSongs.mapIndexedNotNull { index, track ->
        track.trackId.takeIf { it.isNotBlank() }?.let { trackId ->
            index to PlaylistItem(
                id = "artist:${header.artistId}:$index:$trackId",
                displayName = track.title,
                songId = trackId,
                title = track.title,
                artistText = track.artistText,
                albumTitle = track.albumTitle,
                coverUrl = track.coverUrl,
                durationMs = track.durationMs,
                itemType = PlaylistItemType.ONLINE,
                contextType = "artist",
                contextId = header.artistId,
                contextTitle = header.name
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
