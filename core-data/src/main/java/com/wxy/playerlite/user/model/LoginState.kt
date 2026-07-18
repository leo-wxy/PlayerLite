package com.wxy.playerlite.user.model

sealed interface LoginState {
    data object LoggedOut : LoginState

    data class LoggedIn(
        val session: UserSession
    ) : LoginState
}
