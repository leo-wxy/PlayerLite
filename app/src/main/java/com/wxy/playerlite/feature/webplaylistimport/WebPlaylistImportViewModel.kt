package com.wxy.playerlite.feature.webplaylistimport

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.user.UserRepository
import com.wxy.playerlite.user.model.LoginState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class WebPlaylistImportUiState(
    val inputUrl: String = "",
    val inputErrorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val stage: WebPlaylistImportStage = WebPlaylistImportStage.Input
)

internal sealed interface WebPlaylistImportStage {
    data object Input : WebPlaylistImportStage

    data object LoginRequired : WebPlaylistImportStage

    data class Loading(
        val message: String = "正在读取歌单信息"
    ) : WebPlaylistImportStage

    data class Preview(
        val snapshot: ImportedPlaylistSnapshot
    ) : WebPlaylistImportStage

    data class Error(
        val title: String,
        val message: String
    ) : WebPlaylistImportStage
}

internal class WebPlaylistImportViewModel(
    application: Application,
    private val repository: WebPlaylistImportRepository,
    private val userRepository: UserRepository
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(
        WebPlaylistImportUiState(
            isLoggedIn = userRepository.currentSession() != null
        )
    )
    val uiStateFlow: StateFlow<WebPlaylistImportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.loginStateFlow.collect { loginState ->
                val isLoggedIn = loginState is LoginState.LoggedIn
                val currentStage = _uiState.value.stage
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = isLoggedIn,
                    stage = if (isLoggedIn && currentStage is WebPlaylistImportStage.LoginRequired) {
                        WebPlaylistImportStage.Input
                    } else {
                        currentStage
                    }
                )
            }
        }
    }

    fun onUrlChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            inputUrl = value,
            inputErrorMessage = null
        )
    }

    fun submitUrl() {
        val rawUrl = _uiState.value.inputUrl.trim()
        if (rawUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(
                inputErrorMessage = "请输入歌单网页链接",
                stage = WebPlaylistImportStage.Input
            )
            return
        }
        if (userRepository.currentSession() == null) {
            _uiState.value = _uiState.value.copy(
                inputUrl = rawUrl,
                inputErrorMessage = null,
                stage = WebPlaylistImportStage.LoginRequired
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            inputUrl = rawUrl,
            inputErrorMessage = null,
            stage = WebPlaylistImportStage.Loading()
        )
        viewModelScope.launch {
            runCatching {
                repository.fetchPlaylistSnapshot(rawUrl)
            }.onSuccess { snapshot ->
                _uiState.value = _uiState.value.copy(
                    stage = WebPlaylistImportStage.Preview(snapshot)
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    stage = error.toImportErrorStage()
                )
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val application = appContext as Application
                    return WebPlaylistImportViewModel(
                        application = application,
                        repository = AppContainer.webPlaylistImportRepository(appContext),
                        userRepository = AppContainer.userRepository(appContext)
                    ) as T
                }
            }
        }
    }
}

private fun Throwable.toImportErrorStage(): WebPlaylistImportStage.Error {
    val rawMessage = message.orEmpty()
    return when {
        rawMessage.contains("Unsupported playlist source") -> WebPlaylistImportStage.Error(
            title = "链接暂不支持",
            message = "当前仅支持网易云和 QQ 音乐歌单链接"
        )

        rawMessage.contains("Missing") || rawMessage.contains("blank", ignoreCase = true) -> {
            WebPlaylistImportStage.Error(
                title = "链接解析失败",
                message = "未能解析歌单地址，请检查链接是否完整"
            )
        }

        else -> WebPlaylistImportStage.Error(
            title = "歌单读取失败",
            message = rawMessage.ifBlank { "当前无法读取歌单信息，请稍后重试" }
        )
    }
}
