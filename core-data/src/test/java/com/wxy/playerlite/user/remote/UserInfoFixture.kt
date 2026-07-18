package com.wxy.playerlite.user.remote

import com.wxy.playerlite.user.model.UserInfo

object UserInfoFixture {
    fun loggedIn(): UserInfo {
        return UserInfo(
            userId = 77L,
            accountId = 88L,
            nickname = "Codex",
            avatarUrl = "https://example.com/avatar.jpg",
            vipType = 11,
            level = null,
            signature = "hello",
            backgroundUrl = "https://example.com/bg.jpg",
            playlistCount = 9,
            followeds = 8,
            follows = 7,
            eventCount = 6,
            listenSongs = null,
            accountIdentity = "13800138000"
        )
    }
}
