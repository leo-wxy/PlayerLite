package com.wxy.playerlite.feature.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.core.playback.AppPlaybackGraph
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackGateway
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackRequest
import com.wxy.playerlite.user.UserRepository
import com.wxy.playerlite.user.UserSessionInvalidException
import com.wxy.playerlite.user.model.LoginState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class DailyRecommendedSongsUiState(
    val isLoggedIn: Boolean = false,
    val contentState: DailyRecommendedSongsContentState = DailyRecommendedSongsContentState.Idle
)

internal sealed interface DailyRecommendedSongsContentState {
    data object Idle : DailyRecommendedSongsContentState

    data object Loading : DailyRecommendedSongsContentState

    data class Content(
        val items: List<DailyRecommendedSongUiModel>
    ) : DailyRecommendedSongsContentState

    data object Empty : DailyRecommendedSongsContentState

    data class Error(
        val message: String
    ) : DailyRecommendedSongsContentState
}

internal sealed interface DailyRecommendedSongsUiEvent {
    data object OpenPlayer : DailyRecommendedSongsUiEvent

    data class ShowMessage(
        val message: String
    ) : DailyRecommendedSongsUiEvent
}

internal class DailyRecommendedSongsViewModel(
    application: Application,
    private val repository: DailyRecommendedSongsRepository,
    private val userRepository: UserRepository,
    private val playbackGateway: DetailPlaybackGateway
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        repository = AppContainer.dailyRecommendedSongsRepository(application.applicationContext),
        userRepository = AppContainer.userRepository(application.applicationContext),
        playbackGateway = AppPlaybackGraph.detailPlaybackGateway(application.applicationContext)
    )

    constructor(
        application: Application,
        repository: DailyRecommendedSongsRepository,
        userRepository: UserRepository
    ) : this(
        application = application,
        repository = repository,
        userRepository = userRepository,
        playbackGateway = NoOpDetailPlaybackGateway
    )

    private val _uiState = MutableStateFlow(DailyRecommendedSongsUiState())
    val uiStateFlow: StateFlow<DailyRecommendedSongsUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<DailyRecommendedSongsUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<DailyRecommendedSongsUiEvent> = _uiEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            userRepository.loginStateFlow.collect { loginState ->
                when (loginState) {
                    LoginState.LoggedOut -> {
                        _uiState.value = DailyRecommendedSongsUiState()
                    }

                    is LoginState.LoggedIn -> {
                        _uiState.value = _uiState.value.copy(isLoggedIn = true)
                        loadIfNeeded()
                    }
                }
            }
        }
    }

    fun retry() {
        if (userRepository.currentSession() == null) {
            return
        }
        load(force = true)
    }

    fun playAll() {
        playAt(index = 0)
    }

    fun playAt(index: Int) {
        val items = (uiStateFlow.value.contentState as? DailyRecommendedSongsContentState.Content)
            ?.items
            .orEmpty()
        if (index !in items.indices) {
            return
        }
        val started = playbackGateway.play(
            request = DetailPlaybackRequest(
                items = items.toPlaylistItems(),
                activeIndex = index
            )
        )
        if (started) {
            _uiEvents.tryEmit(DailyRecommendedSongsUiEvent.OpenPlayer)
        } else {
            _uiEvents.tryEmit(
                DailyRecommendedSongsUiEvent.ShowMessage("播放启动失败，请稍后重试")
            )
        }
    }

    override fun onCleared() {
        playbackGateway.close()
        super.onCleared()
    }

    private fun loadIfNeeded() {
        if (userRepository.currentSession() == null) {
            return
        }
        when (_uiState.value.contentState) {
            DailyRecommendedSongsContentState.Idle,
            is DailyRecommendedSongsContentState.Error -> load(force = false)

            DailyRecommendedSongsContentState.Loading,
            DailyRecommendedSongsContentState.Empty,
            is DailyRecommendedSongsContentState.Content -> Unit
        }
    }

    private fun load(force: Boolean) {
        if (userRepository.currentSession() == null) {
            return
        }
        if (_uiState.value.contentState is DailyRecommendedSongsContentState.Loading) {
            return
        }
        _uiState.value = _uiState.value.copy(
            contentState = DailyRecommendedSongsContentState.Loading
        )
        viewModelScope.launch {
            runCatching {
                repository.fetchDailyRecommendedSongs()
            }.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    contentState = if (items.isEmpty()) {
                        DailyRecommendedSongsContentState.Empty
                    } else {
                        DailyRecommendedSongsContentState.Content(items)
                    }
                )
            }.onFailure { error ->
                if (error is UserSessionInvalidException) {
                    userRepository.logout()
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    contentState = DailyRecommendedSongsContentState.Error(
                        message = error.message ?: "每日推荐加载失败"
                    )
                )
            }
        }
    }
}

private data object NoOpDetailPlaybackGateway : DetailPlaybackGateway {
    override fun play(request: DetailPlaybackRequest): Boolean = false
}

private fun List<DailyRecommendedSongUiModel>.toPlaylistItems(): List<PlaylistItem> {
    return mapIndexed { index, item ->
        PlaylistItem(
            id = "daily-reco:$index:${item.songId}",
            displayName = item.title,
            songId = item.songId,
            title = item.title,
            artistText = item.artistText,
            primaryArtistId = item.primaryArtistId,
            albumTitle = item.albumTitle,
            coverUrl = item.coverUrl,
            durationMs = item.durationMs,
            itemType = PlaylistItemType.ONLINE,
            contextType = "daily_recommended_songs",
            contextId = "daily-reco",
            contextTitle = "每日推荐"
        )
    }
}
