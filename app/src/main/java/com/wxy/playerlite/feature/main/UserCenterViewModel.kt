package com.wxy.playerlite.feature.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.user.UserRepository
import com.wxy.playerlite.user.UserSessionInvalidException
import com.wxy.playerlite.user.model.LoginState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class UserCenterUiState(
    val playlistsState: UserCenterPlaylistsState = UserCenterPlaylistsState.Idle,
    val likedPlaylistId: String? = null
)

internal sealed interface UserCenterPlaylistsState {
    data object Idle : UserCenterPlaylistsState

    data object Loading : UserCenterPlaylistsState

    data class Content(
        val items: List<UserCenterCollectionItemUiModel>
    ) : UserCenterPlaylistsState

    data class Error(
        val message: String
    ) : UserCenterPlaylistsState

    data object Empty : UserCenterPlaylistsState
}

internal class UserCenterViewModel(
    application: Application,
    private val repository: UserCenterRepository,
    private val userRepository: UserRepository
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        repository = AppContainer.userCenterRepository(application.applicationContext),
        userRepository = AppContainer.userRepository(application.applicationContext)
    )

    private val _uiState = MutableStateFlow(UserCenterUiState())
    val uiStateFlow: StateFlow<UserCenterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.loginStateFlow.collect { loginState ->
                when (loginState) {
                    LoginState.LoggedOut -> resetState()
                    is LoginState.LoggedIn -> loadPlaylistsIfNeeded()
                }
            }
        }
    }

    fun retryPlaylists() {
        if (userRepository.currentSession() == null) {
            return
        }
        loadPlaylists(force = true)
    }

    private fun loadPlaylistsIfNeeded() {
        if (userRepository.currentSession() == null) {
            return
        }
        when (_uiState.value.playlistsState) {
            UserCenterPlaylistsState.Idle,
            is UserCenterPlaylistsState.Error -> loadPlaylists(force = false)

            UserCenterPlaylistsState.Loading,
            UserCenterPlaylistsState.Empty,
            is UserCenterPlaylistsState.Content -> Unit
        }
    }

    private fun loadPlaylists(force: Boolean) {
        val session = userRepository.currentSession() ?: return
        if (!force && _uiState.value.playlistsState is UserCenterPlaylistsState.Loading) {
            return
        }
        _uiState.value = _uiState.value.copy(
            playlistsState = UserCenterPlaylistsState.Loading
        )
        viewModelScope.launch {
            runCatching {
                val userId = session.userInfo.userId
                repository.fetchCreatedPlaylists(userId)
            }.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    likedPlaylistId = null,
                    playlistsState = if (items.isEmpty()) {
                        UserCenterPlaylistsState.Empty
                    } else {
                        UserCenterPlaylistsState.Content(items)
                    }
                )
            }.onFailure { error ->
                if (error is UserSessionInvalidException) {
                    userRepository.logout()
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    playlistsState = UserCenterPlaylistsState.Error(
                        message = error.message ?: "用户歌单加载失败"
                    )
                )
            }
        }
    }

    private fun resetState() {
        _uiState.value = UserCenterUiState()
    }
}
