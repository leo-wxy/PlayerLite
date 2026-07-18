package com.wxy.playerlite.user.model

data class UserInfo(
    val userId: Long,
    val accountId: Long,
    val nickname: String,
    val avatarUrl: String,
    val vipType: Int,
    val level: Int?,
    val signature: String?,
    val backgroundUrl: String?,
    val playlistCount: Int?,
    val followeds: Int?,
    val follows: Int?,
    val eventCount: Int?,
    val listenSongs: Int?,
    val accountIdentity: String?
)
