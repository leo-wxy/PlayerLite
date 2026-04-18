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
import java.util.concurrent.ConcurrentHashMap

internal data class RecentSongsUiState(
    val isLoggedIn: Boolean = false,
    val selectedTab: RecentPlaybackTab = RecentPlaybackTab.LOCAL,
    val tabStates: Map<RecentPlaybackTab, RecentPlaybackContentState> =
        RecentPlaybackTab.entries.associateWith { RecentPlaybackContentState.Idle }
) {
    val contentState: RecentPlaybackContentState
        get() = tabStates[selectedTab] ?: RecentPlaybackContentState.Idle
}

internal enum class RecentPlaybackTab(val label: String) {
    LOCAL("本机"),
    SONGS("歌曲"),
    VIDEOS("视频"),
    VOICES("声音"),
    PLAYLISTS("歌单"),
    ALBUMS("专辑"),
    PODCASTS("播客");

    val testTag: String
        get() = name.lowercase()
}

internal sealed interface RecentPlaybackContentState {
    data object Idle : RecentPlaybackContentState

    data object Loading : RecentPlaybackContentState

    data class LocalContent(
        val items: List<RecentLocalPlaybackItemUiModel>
    ) : RecentPlaybackContentState

    data class SongContent(
        val items: List<RecentSongItemUiModel>
    ) : RecentPlaybackContentState

    data class GenericContent(
        val items: List<RecentPlaybackListItemUiModel>
    ) : RecentPlaybackContentState

    data object Empty : RecentPlaybackContentState

    data class Error(
        val message: String
    ) : RecentPlaybackContentState
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
    private val tabLoadVersions = ConcurrentHashMap<RecentPlaybackTab, Int>()

    init {
        viewModelScope.launch {
            userRepository.loginStateFlow.collect { loginState ->
                when (loginState) {
                    LoginState.LoggedOut -> {
                        val localState = stateFor(RecentPlaybackTab.LOCAL)
                        _uiState.value = RecentSongsUiState(
                            isLoggedIn = false,
                            selectedTab = RecentPlaybackTab.LOCAL,
                            tabStates = RecentPlaybackTab.entries.associateWith { tab ->
                                if (tab == RecentPlaybackTab.LOCAL) {
                                    localState
                                } else {
                                    RecentPlaybackContentState.Idle
                                }
                            }
                        )
                        loadTabIfNeeded(RecentPlaybackTab.LOCAL)
                    }

                    is LoginState.LoggedIn -> {
                        _uiState.value = _uiState.value.copy(isLoggedIn = true)
                        loadTabIfNeeded(_uiState.value.selectedTab)
                    }
                }
            }
        }
    }

    fun selectTab(tab: RecentPlaybackTab) {
        if (_uiState.value.selectedTab == tab) {
            loadTabIfNeeded(tab)
            return
        }
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        loadTabIfNeeded(tab)
    }

    fun retry() {
        if (!canLoadTab(_uiState.value.selectedTab)) {
            return
        }
        loadTab(_uiState.value.selectedTab, force = true)
    }

    private fun loadTabIfNeeded(tab: RecentPlaybackTab) {
        if (!canLoadTab(tab)) {
            return
        }
        when (stateFor(tab)) {
            RecentPlaybackContentState.Idle,
            is RecentPlaybackContentState.Error -> loadTab(tab, force = false)

            RecentPlaybackContentState.Loading,
            RecentPlaybackContentState.Empty,
            is RecentPlaybackContentState.LocalContent,
            is RecentPlaybackContentState.SongContent,
            is RecentPlaybackContentState.GenericContent -> Unit
        }
    }

    private fun loadTab(tab: RecentPlaybackTab, force: Boolean) {
        if (!canLoadTab(tab)) {
            return
        }
        if (!force && stateFor(tab) is RecentPlaybackContentState.Loading) {
            return
        }
        val requestVersion = tabLoadVersions.compute(tab) { _, current -> (current ?: 0) + 1 } ?: 1
        updateTabState(tab, RecentPlaybackContentState.Loading)
        viewModelScope.launch {
            runCatching {
                when (tab) {
                    RecentPlaybackTab.LOCAL -> {
                        RecentPlaybackContentState.LocalContent(
                            repository.fetchLocalRecentPlaybackItems(limit = DEFAULT_LIMIT)
                        )
                    }

                    RecentPlaybackTab.SONGS -> {
                        RecentPlaybackContentState.SongContent(
                            repository.fetchRecentSongItems(limit = DEFAULT_LIMIT)
                        )
                    }

                    RecentPlaybackTab.VIDEOS -> {
                        RecentPlaybackContentState.GenericContent(
                            repository.fetchRecentVideos(limit = DEFAULT_LIMIT)
                        )
                    }

                    RecentPlaybackTab.VOICES -> {
                        RecentPlaybackContentState.GenericContent(
                            repository.fetchRecentVoices(limit = DEFAULT_LIMIT)
                        )
                    }

                    RecentPlaybackTab.PLAYLISTS -> {
                        RecentPlaybackContentState.GenericContent(
                            repository.fetchRecentPlaylists(limit = DEFAULT_LIMIT)
                        )
                    }

                    RecentPlaybackTab.ALBUMS -> {
                        RecentPlaybackContentState.GenericContent(
                            repository.fetchRecentAlbums(limit = DEFAULT_LIMIT)
                        )
                    }

                    RecentPlaybackTab.PODCASTS -> {
                        RecentPlaybackContentState.GenericContent(
                            repository.fetchRecentDjRadios(limit = DEFAULT_LIMIT)
                        )
                    }
                }
            }.onSuccess { items ->
                if (!isLatestRequest(tab, requestVersion)) {
                    return@launch
                }
                val nextState = when (items) {
                    is RecentPlaybackContentState.LocalContent ->
                        if (items.items.isEmpty()) RecentPlaybackContentState.Empty else items
                    is RecentPlaybackContentState.SongContent ->
                        if (items.items.isEmpty()) RecentPlaybackContentState.Empty else items
                    is RecentPlaybackContentState.GenericContent ->
                        if (items.items.isEmpty()) RecentPlaybackContentState.Empty else items
                    else -> RecentPlaybackContentState.Empty
                }
                updateTabState(tab, nextState)
            }.onFailure { error ->
                if (!isLatestRequest(tab, requestVersion)) {
                    return@launch
                }
                if (error is UserSessionInvalidException) {
                    userRepository.logout()
                    return@launch
                }
                updateTabState(
                    tab = tab,
                    state = RecentPlaybackContentState.Error(
                        message = error.message ?: "最近播放加载失败"
                    )
                )
            }
        }
    }

    private fun canLoadTab(tab: RecentPlaybackTab): Boolean {
        return tab == RecentPlaybackTab.LOCAL || userRepository.currentSession() != null
    }

    private fun isLatestRequest(tab: RecentPlaybackTab, requestVersion: Int): Boolean {
        return tabLoadVersions[tab] == requestVersion
    }

    private fun stateFor(tab: RecentPlaybackTab): RecentPlaybackContentState {
        return _uiState.value.tabStates[tab] ?: RecentPlaybackContentState.Idle
    }

    private fun updateTabState(tab: RecentPlaybackTab, state: RecentPlaybackContentState) {
        _uiState.value = _uiState.value.copy(
            tabStates = _uiState.value.tabStates.toMutableMap().apply {
                this[tab] = state
            }
        )
    }

    private companion object {
        private const val DEFAULT_LIMIT = 100
    }
}
