package com.wxy.playerlite.feature.user.model

import com.wxy.playerlite.user.model.LoginState
import com.wxy.playerlite.user.model.UserInfo
import com.wxy.playerlite.user.model.UserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserSessionUiStateMapperTest {
    @Test
    fun loggedInState_shouldExposeAvatarUrl() {
        val loginState = LoginState.LoggedIn(
            session = UserSession(
                cookie = "cookie",
                csrfToken = "csrf",
                userInfo = UserInfo(
                    userId = 1L,
                    accountId = 2L,
                    nickname = "Codex",
                    avatarUrl = "https://example.com/avatar.jpg",
                    vipType = 1,
                    level = 10,
                    signature = null,
                    backgroundUrl = null,
                    playlistCount = null,
                    followeds = null,
                    follows = null,
                    eventCount = null,
                    listenSongs = null,
                    accountIdentity = "1_14026001177"
                ),
                lastValidatedAtMs = 1L
            )
        )

        val uiState = loginState.toUserSessionUiState(isBusy = false)

        assertEquals("https://example.com/avatar.jpg", uiState.avatarUrl)
        assertEquals("Codex", uiState.title)
    }

    @Test
    fun loggedOutState_shouldClearAvatarUrl() {
        val uiState = LoginState.LoggedOut.toUserSessionUiState(isBusy = false)

        assertNull(uiState.avatarUrl)
    }
}
