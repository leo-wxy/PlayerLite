package com.wxy.playerlite.feature.user

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxy.playerlite.core.AppContainer
import com.wxy.playerlite.user.model.LoginState
import com.wxy.playerlite.user.UserRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class LoginMethod {
    PHONE,
    EMAIL
}

internal data class LoginUiState(
    val loginMethod: LoginMethod = LoginMethod.PHONE,
    val phone: String = "",
    val email: String = "",
    val password: String = "",
    val countryCode: String = "86",
    val isBusy: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userTitle: String = "登录后解锁在线播放",
    val statusText: String = "登录只影响受保护的在线播放能力，本地播放不受影响",
    val loginSucceeded: Boolean = false,
    val skipRequested: Boolean = false
)

internal class LoginViewModel(
    application: Application,
    private val repository: UserRepository = AppContainer.userRepository(application.applicationContext)
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        repository = AppContainer.userRepository(application.applicationContext)
    )

    private val _uiState = MutableStateFlow(LoginUiState())

    val uiStateFlow: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.loginStateFlow.collect(::publishState)
        }
        viewModelScope.launch {
            repository.restorePersistedSession()
        }
    }

    fun updatePhone(value: String) {
        _uiState.value = _uiState.value.copy(phone = value, statusText = defaultStatus())
    }

    fun updateEmail(value: String) {
        _uiState.value = _uiState.value.copy(email = value, statusText = defaultStatus())
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value, statusText = defaultStatus())
    }

    fun selectLoginMethod(loginMethod: LoginMethod) {
        _uiState.value = _uiState.value.copy(
            loginMethod = loginMethod,
            statusText = defaultStatus()
        )
    }

    fun submitLogin() {
        val password = _uiState.value.password
        val loginMethod = _uiState.value.loginMethod
        val phone = _uiState.value.phone.trim()
        val email = _uiState.value.email.trim()
        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                statusText = if (loginMethod == LoginMethod.PHONE) {
                    "请输入手机号和密码"
                } else {
                    "请输入邮箱和密码"
                }
            )
            return
        }
        if (loginMethod == LoginMethod.PHONE && phone.isBlank()) {
            _uiState.value = _uiState.value.copy(statusText = "请输入手机号和密码")
            return
        }
        if (loginMethod == LoginMethod.EMAIL && email.isBlank()) {
            _uiState.value = _uiState.value.copy(statusText = "请输入邮箱和密码")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, loginSucceeded = false)
            runCatching {
                when (loginMethod) {
                    LoginMethod.PHONE -> repository.loginWithPhone(
                        phone = phone,
                        password = password
                    )

                    LoginMethod.EMAIL -> repository.loginWithEmail(
                        email = email,
                        password = password
                    )
                }
            }.onSuccess { session ->
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    isLoggedIn = true,
                    userTitle = session.userInfo.nickname,
                    statusText = "登录成功，已建立在线播放会话",
                    loginSucceeded = true,
                    skipRequested = false
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    loginSucceeded = false,
                    statusText = error.message ?: "登录失败，请重试"
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            repository.logout()
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                isLoggedIn = false,
                userTitle = "登录后解锁在线播放",
                statusText = "已退出登录",
                loginSucceeded = false,
                skipRequested = false
            )
        }
    }

    fun skipLogin() {
        _uiState.value = _uiState.value.copy(
            skipRequested = true,
            loginSucceeded = false,
            statusText = defaultStatus()
        )
    }

    fun consumeLoginSuccess() {
        _uiState.value = _uiState.value.copy(loginSucceeded = false)
    }

    fun consumeSkipRequested() {
        _uiState.value = _uiState.value.copy(skipRequested = false)
    }

    private fun publishState(loginState: LoginState) {
        _uiState.value = when (loginState) {
            LoginState.LoggedOut -> _uiState.value.copy(
                isBusy = _uiState.value.isBusy,
                isLoggedIn = false,
                userTitle = "登录后解锁在线播放",
                statusText = _uiState.value.statusText.ifBlank { defaultStatus() }
            )

            is LoginState.LoggedIn -> _uiState.value.copy(
                isBusy = _uiState.value.isBusy,
                isLoggedIn = true,
                userTitle = loginState.session.userInfo.nickname,
                statusText = "当前账号：${loginState.session.userInfo.accountIdentity ?: "在线用户"}"
            )
        }
    }

    private fun defaultStatus(): String {
        return "登录只影响受保护的在线播放能力，本地播放不受影响"
    }
}
