package com.wxy.playerlite.feature.local

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackGateway
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackRequest
import com.wxy.playerlite.feature.player.runtime.RuntimeDetailPlaybackGateway
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class LocalSongsViewModel(
    application: Application,
    private val repository: LocalSongsRepository,
    private val playbackGateway: DetailPlaybackGateway
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(LocalSongsUiState())
    val uiStateFlow: StateFlow<LocalSongsUiState> = _uiState.asStateFlow()
    private val _uiEvents = MutableSharedFlow<LocalSongsUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<LocalSongsUiEvent> = _uiEvents.asSharedFlow()

    private var hasPermission: Boolean = false
    private var initializedWithPermission: Boolean = false

    fun onPermissionStateChanged(granted: Boolean) {
        hasPermission = granted
        if (!granted) {
            _uiState.value = _uiState.value.copy(
                requiresPermission = true,
                isLoading = false,
                isScanning = false,
                errorMessage = null
            )
            return
        }
        if (initializedWithPermission) {
            _uiState.value = _uiState.value.copy(requiresPermission = false)
            return
        }
        initializedWithPermission = true
        viewModelScope.launch {
            val cachedSongs = repository.readCachedSongs()
            if (cachedSongs.isNotEmpty()) {
                _uiState.value = LocalSongsUiState(
                    songs = cachedSongs,
                    hasCachedSongs = true,
                    requiresPermission = false
                )
            } else {
                scanInternal(showLoading = true)
            }
        }
    }

    fun onScanRequested() {
        if (!hasPermission) {
            _uiState.value = _uiState.value.copy(requiresPermission = true)
            return
        }
        viewModelScope.launch {
            scanInternal(showLoading = _uiState.value.songs.isEmpty())
        }
    }

    fun playAll() {
        if (_uiState.value.songs.isEmpty()) {
            return
        }
        startPlayback(activeIndex = 0)
    }

    fun playSong(index: Int) {
        val songs = _uiState.value.songs
        if (index !in songs.indices) {
            return
        }
        startPlayback(activeIndex = index)
    }

    private fun startPlayback(activeIndex: Int) {
        val songs = _uiState.value.songs
        val started = playbackGateway.play(
            DetailPlaybackRequest(
                items = songs.map(LocalSongEntry::toPlaylistItem),
                activeIndex = activeIndex
            )
        )
        if (started) {
            _uiEvents.tryEmit(LocalSongsUiEvent.OpenPlayer)
        } else {
            _uiEvents.tryEmit(LocalSongsUiEvent.ShowMessage("播放启动失败，请稍后重试"))
        }
    }

    override fun onCleared() {
        playbackGateway.close()
        super.onCleared()
    }

    private suspend fun scanInternal(showLoading: Boolean) {
        val previousSongs = _uiState.value.songs
        _uiState.value = _uiState.value.copy(
            isLoading = showLoading,
            isScanning = !showLoading,
            requiresPermission = false,
            errorMessage = null
        )
        repository.scanSongs().fold(
            onSuccess = { songs ->
                _uiState.value = LocalSongsUiState(
                    songs = songs,
                    hasCachedSongs = songs.isNotEmpty(),
                    requiresPermission = false
                )
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    songs = previousSongs,
                    isLoading = false,
                    isScanning = false,
                    requiresPermission = false,
                    errorMessage = error.message ?: "本地歌曲扫描失败"
                )
            }
        )
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val application = appContext as Application
                    return LocalSongsViewModel(
                        application = application,
                        repository = DefaultLocalSongsRepository(
                            contentResolver = appContext.contentResolver,
                            storage = LocalSongsSnapshotStorage(
                                preferences = appContext.getSharedPreferences(
                                    "local_songs_snapshot",
                                    Context.MODE_PRIVATE
                                )
                            )
                        ),
                        playbackGateway = RuntimeDetailPlaybackGateway(appContext)
                    ) as T
                }
            }
        }
    }
}
