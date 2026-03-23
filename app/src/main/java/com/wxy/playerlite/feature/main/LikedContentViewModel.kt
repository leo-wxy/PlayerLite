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

internal enum class LikedContentTab(val label: String) {
    PLAYLISTS("歌单"),
    ARTISTS("歌手"),
    MVS("MV"),
    COLUMNS("专栏")
}

internal data class LikedContentUiState(
    val isLoggedIn: Boolean = false,
    val selectedTab: LikedContentTab = LikedContentTab.PLAYLISTS,
    val tabStates: Map<LikedContentTab, LikedTabContentState> = LikedContentTab.entries.associateWith {
        LikedTabContentState.Idle
    }
) {
    val currentState: LikedTabContentState
        get() = tabStates.getValue(selectedTab)
}

internal sealed interface LikedTabContentState {
    data object Idle : LikedTabContentState

    data object Loading : LikedTabContentState

    data class Content(
        val items: List<UserCenterCollectionItemUiModel>
    ) : LikedTabContentState

    data object Empty : LikedTabContentState

    data class Error(
        val message: String
    ) : LikedTabContentState
}

internal class LikedContentViewModel(
    application: Application,
    private val repository: UserCenterRepository,
    private val userRepository: UserRepository
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        repository = AppContainer.userCenterRepository(application.applicationContext),
        userRepository = AppContainer.userRepository(application.applicationContext)
    )

    private val _uiState = MutableStateFlow(LikedContentUiState())
    val uiStateFlow: StateFlow<LikedContentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.loginStateFlow.collect { loginState ->
                when (loginState) {
                    LoginState.LoggedOut -> {
                        _uiState.value = LikedContentUiState()
                    }

                    is LoginState.LoggedIn -> {
                        val selectedTab = _uiState.value.selectedTab
                        _uiState.value = LikedContentUiState(
                            isLoggedIn = true,
                            selectedTab = selectedTab
                        )
                        loadSelectedTabIfNeeded()
                    }
                }
            }
        }
    }

    fun selectTab(tab: LikedContentTab) {
        if (_uiState.value.selectedTab != tab) {
            _uiState.value = _uiState.value.copy(selectedTab = tab)
        }
        loadSelectedTabIfNeeded()
    }

    fun retry() {
        if (userRepository.currentSession() == null) {
            return
        }
        loadSelectedTab(force = true)
    }

    private fun loadSelectedTabIfNeeded() {
        if (userRepository.currentSession() == null) {
            return
        }
        when (_uiState.value.currentState) {
            LikedTabContentState.Idle,
            is LikedTabContentState.Error -> loadSelectedTab(force = false)

            LikedTabContentState.Loading,
            LikedTabContentState.Empty,
            is LikedTabContentState.Content -> Unit
        }
    }

    private fun loadSelectedTab(force: Boolean) {
        val selectedTab = _uiState.value.selectedTab
        if (userRepository.currentSession() == null) {
            return
        }
        if (!force && _uiState.value.currentState is LikedTabContentState.Loading) {
            return
        }

        updateTabState(selectedTab, LikedTabContentState.Loading)
        viewModelScope.launch {
            runCatching {
                when (selectedTab) {
                    LikedContentTab.PLAYLISTS -> {
                        val session = userRepository.currentSession() ?: return@runCatching emptyList()
                        repository.fetchCollectedPlaylists(session.userInfo.userId)
                    }
                    LikedContentTab.ARTISTS -> repository.fetchFavoriteArtists()
                    LikedContentTab.MVS -> repository.fetchFavoriteMvs()
                    LikedContentTab.COLUMNS -> repository.fetchFavoriteColumns()
                }
            }.onSuccess { items ->
                updateTabState(
                    selectedTab,
                    if (items.isEmpty()) {
                        LikedTabContentState.Empty
                    } else {
                        LikedTabContentState.Content(items)
                    }
                )
            }.onFailure { error ->
                if (error is UserSessionInvalidException) {
                    userRepository.logout()
                    return@launch
                }
                updateTabState(
                    selectedTab,
                    LikedTabContentState.Error(
                        message = error.message ?: "喜欢内容加载失败"
                    )
                )
            }
        }
    }

    private fun updateTabState(
        tab: LikedContentTab,
        state: LikedTabContentState
    ) {
        _uiState.value = _uiState.value.copy(
            tabStates = _uiState.value.tabStates + (tab to state)
        )
    }
}
