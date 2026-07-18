package com.wxy.playerlite.feature.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SongDetailViewModel(
    private val ref: SongRef,
    private val repository: SongDetailFeatureRepository,
    private val actionGateway: SongDetailActionGateway
) : ViewModel() {
    private val _uiState = MutableStateFlow(SongDetailUiState())
    val uiStateFlow: StateFlow<SongDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SongDetailEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SongDetailEvent> = _events.asSharedFlow()

    init {
        load()
    }

    fun retry() {
        load()
    }

    fun play() {
        val content = (_uiState.value.contentState as? SongDetailContentState.Content)?.content ?: return
        if (actionGateway.play(content.playlistItem)) {
            _events.tryEmit(SongDetailEvent.OpenPlayer)
        } else {
            _events.tryEmit(SongDetailEvent.ShowMessage("播放启动失败，请稍后重试"))
        }
    }

    fun playNext() {
        val content = (_uiState.value.contentState as? SongDetailContentState.Content)?.content ?: return
        val message = if (actionGateway.playNext(content.playlistItem)) {
            "已加入下一首播放"
        } else {
            "下一首播放失败，请先开始播放一首歌"
        }
        _events.tryEmit(SongDetailEvent.ShowMessage(message))
    }

    fun openLandscapePlayer() {
        val content = (_uiState.value.contentState as? SongDetailContentState.Content)?.content ?: return
        if (actionGateway.play(content.playlistItem)) {
            _events.tryEmit(SongDetailEvent.OpenLandscapePlayer)
        } else {
            _events.tryEmit(SongDetailEvent.ShowMessage("横屏模式启动失败，请稍后重试"))
        }
    }

    fun openArtist() {
        val content = (_uiState.value.contentState as? SongDetailContentState.Content)?.content ?: return
        content.primaryArtistId?.takeIf { it.isNotBlank() }?.let {
            _events.tryEmit(SongDetailEvent.OpenArtist(it))
        }
    }

    fun openAlbum() {
        val content = (_uiState.value.contentState as? SongDetailContentState.Content)?.content ?: return
        content.albumId?.takeIf { it.isNotBlank() }?.let {
            _events.tryEmit(SongDetailEvent.OpenAlbum(it))
        }
    }

    fun openSong(songId: String) {
        if (songId.isBlank()) return
        _events.tryEmit(SongDetailEvent.OpenSong(songId))
    }

    fun openPlaylist(playlistId: String) {
        if (playlistId.isBlank()) return
        _events.tryEmit(SongDetailEvent.OpenPlaylist(playlistId))
    }

    fun share() {
        val content = (_uiState.value.contentState as? SongDetailContentState.Content)?.content ?: return
        _events.tryEmit(
            SongDetailEvent.Share(
                buildString {
                    append(content.title)
                    content.artistText.takeIf { it.isNotBlank() }?.let {
                        append(" - ")
                        append(it)
                    }
                    val onlineSongId = (content.ref as? SongRef.Online)?.songId
                    if (!onlineSongId.isNullOrBlank()) {
                        append("\nhttps://music.163.com/#/song?id=")
                        append(onlineSongId)
                    }
                }
            )
        )
    }

    fun favorite() {
        val content = (_uiState.value.contentState as? SongDetailContentState.Content)?.content ?: return
        val songId = (content.ref as? SongRef.Online)?.songId
        if (songId.isNullOrBlank() || !content.canFavorite) {
            _events.tryEmit(SongDetailEvent.ShowMessage("当前歌曲不支持收藏"))
            return
        }
        if (_uiState.value.isFavoriting) {
            return
        }
        _uiState.value = _uiState.value.copy(isFavoriting = true)
        viewModelScope.launch {
            repository.favoriteSong(songId)
                .onSuccess {
                    _events.tryEmit(SongDetailEvent.ShowMessage("已加入我喜欢的音乐"))
                }
                .onFailure { error ->
                    _events.tryEmit(
                        SongDetailEvent.ShowMessage(
                            error.message ?: "收藏失败，请稍后重试"
                        )
                    )
                }
            _uiState.value = _uiState.value.copy(isFavoriting = false)
        }
    }

    companion object {
        fun factory(
            ref: SongRef,
            repository: SongDetailFeatureRepository,
            actionGateway: SongDetailActionGateway
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    check(modelClass.isAssignableFrom(SongDetailViewModel::class.java))
                    @Suppress("UNCHECKED_CAST")
                    return SongDetailViewModel(
                        ref = ref,
                        repository = repository,
                        actionGateway = actionGateway
                    ) as T
                }
            }
        }
    }

    private fun load() {
        _uiState.value = _uiState.value.copy(
            contentState = SongDetailContentState.Loading
        )
        viewModelScope.launch {
            runCatching {
                repository.loadSongDetail(ref)
            }.onSuccess { content ->
                _uiState.value = _uiState.value.copy(
                    contentState = SongDetailContentState.Content(content)
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    contentState = SongDetailContentState.Error(
                        message = error.message ?: "歌曲详情加载失败"
                    )
                )
            }
        }
    }
}
