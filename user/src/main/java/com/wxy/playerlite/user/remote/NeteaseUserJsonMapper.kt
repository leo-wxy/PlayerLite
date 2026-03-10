package com.wxy.playerlite.user.remote

import com.wxy.playerlite.user.model.UserInfo
import com.wxy.playerlite.user.model.UserSession
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object NeteaseUserJsonMapper {
    fun parseLoginResponse(payload: JsonObject): UserSession {
        val account = payload["account"]?.jsonObject ?: JsonObject(emptyMap())
        val profile = payload["profile"]?.jsonObject ?: JsonObject(emptyMap())
        val cookie = payload.string("cookie").orEmpty()
        return UserSession(
            cookie = cookie,
            csrfToken = extractCsrfToken(cookie),
            userInfo = mapProfile(
                profile = profile,
                account = account,
                current = null,
                level = payload.int("level"),
                listenSongs = payload.int("listenSongs")
            ),
            lastValidatedAtMs = System.currentTimeMillis()
        )
    }

    fun mergeUserDetail(current: UserInfo, payload: JsonObject): UserInfo {
        val profile = payload["profile"]?.jsonObject ?: JsonObject(emptyMap())
        return mapProfile(
            profile = profile,
            account = null,
            current = current,
            level = payload.int("level") ?: current.level,
            listenSongs = payload.int("listenSongs") ?: current.listenSongs
        )
    }

    fun extractCsrfToken(cookie: String?): String? {
        if (cookie.isNullOrBlank()) {
            return null
        }
        return cookie.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("__csrf=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
    }

    private fun mapProfile(
        profile: JsonObject,
        account: JsonObject?,
        current: UserInfo?,
        level: Int?,
        listenSongs: Int?
    ): UserInfo {
        val userId = profile.long("userId")?.takeIf { it > 0L }
            ?: current?.userId
            ?: account?.long("id")?.takeIf { it > 0L }
            ?: 0L
        val accountId = account?.long("id")?.takeIf { it > 0L }
            ?: current?.accountId
            ?: userId
        return UserInfo(
            userId = userId,
            accountId = accountId,
            nickname = profile.string("nickname")
                ?.takeIf { it.isNotBlank() }
                ?: current?.nickname.orEmpty(),
            avatarUrl = profile.string("avatarUrl")
                ?.takeIf { it.isNotBlank() }
                ?: current?.avatarUrl.orEmpty(),
            vipType = profile.int("vipType") ?: current?.vipType ?: 0,
            level = level,
            signature = profile.string("signature")
                ?.takeIf { it.isNotBlank() }
                ?: current?.signature,
            backgroundUrl = profile.string("backgroundUrl")
                ?.takeIf { it.isNotBlank() }
                ?: current?.backgroundUrl,
            playlistCount = profile.int("playlistCount") ?: current?.playlistCount,
            followeds = profile.int("followeds") ?: current?.followeds,
            follows = profile.int("follows") ?: current?.follows,
            eventCount = profile.int("eventCount") ?: current?.eventCount,
            listenSongs = listenSongs,
            accountIdentity = account?.string("userName")
                ?.takeIf { it.isNotBlank() }
                ?: current?.accountIdentity
        )
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
