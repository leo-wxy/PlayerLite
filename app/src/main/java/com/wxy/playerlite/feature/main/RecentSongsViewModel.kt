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

internal data class RecentSongsUiState(
    val isLoggedIn: Boolean = false,
    val contentState: RecentSongsContentState = RecentSongsContentState.Idle
)

internal sealed interface RecentSongsContentState {
    data object Idle : RecentSongsContentState

    data object Loading : RecentSongsContentState

    data class Content(
        val items: List<UserCenterCollectionItemUiModel>
    ) : RecentSongsContentState

    data object Empty : RecentSongsContentState

    data class Error(
        val message: String
    ) : RecentSongsContentState
}

internal class RecentSongsViewModel(
    application: Application,
    private val repository: UserCenterRepository,
    private val userRepository: UserRepository
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        repository = AppContainer.userCenterRepository(application.applicationContext),
        userRepository = AppContainer.userRepository(application.applicationContext)
    )

    private val _uiState = MutableStateFlow(RecentSongsUiState())
    val uiStateFlow: StateFlow<RecentSongsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.loginStateFlow.collect { loginState ->
                when (loginState) {
                    LoginState.LoggedOut -> {
                        _uiState.value = RecentSongsUiState(
                            isLoggedIn = false,
                            contentState = RecentSongsContentState.Idle
                        )
                    }

                    is LoginState.LoggedIn -> {
                        _uiState.value = _uiState.value.copy(isLoggedIn = true)
                        loadRecentSongsIfNeeded()
                    }
                }
            }
        }
    }

    fun retry() {
        if (userRepository.currentSession() == null) {
            return
        }
        loadRecentSongs(force = true)
    }

    private fun loadRecentSongsIfNeeded() {
        if (userRepository.currentSession() == null) {
            return
        }
        when (_uiState.value.contentState) {
            RecentSongsContentState.Idle,
            is RecentSongsContentState.Error -> loadRecentSongs(force = false)

            RecentSongsContentState.Loading,
            RecentSongsContentState.Empty,
            is RecentSongsContentState.Content -> Unit
        }
    }

    private fun loadRecentSongs(force: Boolean) {
        if (userRepository.currentSession() == null) {
            return
        }
        if (!force && _uiState.value.contentState is RecentSongsContentState.Loading) {
            return
        }
        _uiState.value = _uiState.value.copy(
            contentState = RecentSongsContentState.Loading
        )
        viewModelScope.launch {
            runCatching {
                repository.fetchRecentSongs(limit = DEFAULT_LIMIT)
            }.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    contentState = if (items.isEmpty()) {
                        RecentSongsContentState.Empty
                    } else {
                        RecentSongsContentState.Content(items)
                    }
                )
            }.onFailure { error ->
                if (error is UserSessionInvalidException) {
                    userRepository.logout()
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    contentState = RecentSongsContentState.Error(
                        message = error.message ?: "最近播放加载失败"
                    )
                )
            }
        }
    }

    private companion object {
        private const val DEFAULT_LIMIT = 100
    }
}

