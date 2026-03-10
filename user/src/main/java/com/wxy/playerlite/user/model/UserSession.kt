package com.wxy.playerlite.user.model

data class UserSession(
    val cookie: String,
    val csrfToken: String?,
    val userInfo: UserInfo,
    val lastValidatedAtMs: Long
)
