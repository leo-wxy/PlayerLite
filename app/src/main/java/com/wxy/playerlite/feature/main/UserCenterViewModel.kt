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

internal enum class UserCenterTab(
    val label: String,
    val tagSuffix: String
) {
    PLAYLISTS(label = "用户歌单", tagSuffix = "playlists"),
    ARTISTS(label = "收藏歌手", tagSuffix = "artists"),
    COLUMNS(label = "收藏专栏", tagSuffix = "columns")
}

internal data class UserCenterUiState(
    val selectedTab: UserCenterTab = UserCenterTab.PLAYLISTS,
    val artistsState: UserCenterTabContentState = UserCenterTabContentState.Idle,
    val columnsState: UserCenterTabContentState = UserCenterTabContentState.Idle,
    val playlistsState: UserCenterTabContentState = UserCenterTabContentState.Idle
) {
    val currentTabState: UserCenterTabContentState
        get() = stateFor(selectedTab)

    fun stateFor(tab: UserCenterTab): UserCenterTabContentState {
        return when (tab) {
            UserCenterTab.ARTISTS -> artistsState
            UserCenterTab.COLUMNS -> columnsState
            UserCenterTab.PLAYLISTS -> playlistsState
        }
    }

    fun withTabState(
        tab: UserCenterTab,
        state: UserCenterTabContentState
    ): UserCenterUiState {
        return when (tab) {
            UserCenterTab.ARTISTS -> copy(artistsState = state)
            UserCenterTab.COLUMNS -> copy(columnsState = state)
            UserCenterTab.PLAYLISTS -> copy(playlistsState = state)
        }
    }
}

internal sealed interface UserCenterTabContentState {
    data object Idle : UserCenterTabContentState

    data object Loading : UserCenterTabContentState

    data class Content(
        val items: List<UserCenterCollectionItemUiModel>
    ) : UserCenterTabContentState

    data class Error(
        val message: String
    ) : UserCenterTabContentState

    data object Empty : UserCenterTabContentState
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
                    LoginState.LoggedOut -> resetContentStates()
                    is LoginState.LoggedIn -> loadSelectedTabIfNeeded()
                }
            }
        }
    }

    fun onTabSelected(tab: UserCenterTab) {
        if (_uiState.value.selectedTab != tab) {
            _uiState.value = _uiState.value.copy(selectedTab = tab)
        }
        loadSelectedTabIfNeeded()
    }

    fun retryCurrentTab() {
        if (userRepository.currentSession() == null) {
            return
        }
        loadTab(tab = _uiState.value.selectedTab, force = true)
    }

    private fun loadSelectedTabIfNeeded() {
        if (userRepository.currentSession() == null) {
            return
        }
        val tab = _uiState.value.selectedTab
        when (_uiState.value.stateFor(tab)) {
            UserCenterTabContentState.Idle,
            is UserCenterTabContentState.Error -> loadTab(tab = tab, force = false)

            UserCenterTabContentState.Loading,
            UserCenterTabContentState.Empty,
            is UserCenterTabContentState.Content -> Unit
        }
    }

    private fun loadTab(
        tab: UserCenterTab,
        force: Boolean
    ) {
        val session = userRepository.currentSession() ?: return
        if (!force && _uiState.value.stateFor(tab) is UserCenterTabContentState.Loading) {
            return
        }
        _uiState.value = _uiState.value.withTabState(
            tab = tab,
            state = UserCenterTabContentState.Loading
        )
        viewModelScope.launch {
            runCatching {
                when (tab) {
                    UserCenterTab.ARTISTS -> repository.fetchFavoriteArtists()
                    UserCenterTab.COLUMNS -> repository.fetchFavoriteColumns()
                    UserCenterTab.PLAYLISTS -> repository.fetchUserPlaylists(session.userInfo.userId)
                }
            }.onSuccess { items ->
                _uiState.value = _uiState.value.withTabState(
                    tab = tab,
                    state = if (items.isEmpty()) {
                        UserCenterTabContentState.Empty
                    } else {
                        UserCenterTabContentState.Content(items)
                    }
                )
            }.onFailure { error ->
                if (error is UserSessionInvalidException) {
                    userRepository.logout()
                    return@launch
                }
                _uiState.value = _uiState.value.withTabState(
                    tab = tab,
                    state = UserCenterTabContentState.Error(
                        message = error.message ?: defaultErrorMessage(tab)
                    )
                )
            }
        }
    }

    private fun resetContentStates() {
        _uiState.value = UserCenterUiState(
            selectedTab = _uiState.value.selectedTab
        )
    }

    private fun defaultErrorMessage(tab: UserCenterTab): String {
        return when (tab) {
            UserCenterTab.ARTISTS -> "收藏歌手加载失败"
            UserCenterTab.COLUMNS -> "收藏专栏加载失败"
            UserCenterTab.PLAYLISTS -> "用户歌单加载失败"
        }
    }
}
