package com.wxy.playerlite.user.storage

import com.wxy.playerlite.user.model.UserInfo
import com.wxy.playerlite.user.model.UserSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object UserSessionSnapshotCodec {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun encode(session: UserSession): String {
        return buildJsonObject {
            put("cookie", JsonPrimitive(session.cookie))
            session.csrfToken?.let { put("csrfToken", JsonPrimitive(it)) }
            put("lastValidatedAtMs", JsonPrimitive(session.lastValidatedAtMs))
            put("userInfo", buildJsonObject {
                put("userId", JsonPrimitive(session.userInfo.userId))
                put("accountId", JsonPrimitive(session.userInfo.accountId))
                put("nickname", JsonPrimitive(session.userInfo.nickname))
                put("avatarUrl", JsonPrimitive(session.userInfo.avatarUrl))
                put("vipType", JsonPrimitive(session.userInfo.vipType))
                session.userInfo.level?.let { put("level", JsonPrimitive(it)) }
                session.userInfo.signature?.let { put("signature", JsonPrimitive(it)) }
                session.userInfo.backgroundUrl?.let { put("backgroundUrl", JsonPrimitive(it)) }
                session.userInfo.playlistCount?.let { put("playlistCount", JsonPrimitive(it)) }
                session.userInfo.followeds?.let { put("followeds", JsonPrimitive(it)) }
                session.userInfo.follows?.let { put("follows", JsonPrimitive(it)) }
                session.userInfo.eventCount?.let { put("eventCount", JsonPrimitive(it)) }
                session.userInfo.listenSongs?.let { put("listenSongs", JsonPrimitive(it)) }
                session.userInfo.accountIdentity?.let { put("accountIdentity", JsonPrimitive(it)) }
            })
        }.toString()
    }

    fun decode(raw: String): UserSession? {
        return runCatching {
            val payload = json.parseToJsonElement(raw).jsonObject
            val userInfo = payload.requiredObject("userInfo")
            UserSession(
                cookie = payload.requiredString("cookie"),
                csrfToken = payload.optionalString("csrfToken"),
                userInfo = UserInfo(
                    userId = userInfo.requiredLong("userId"),
                    accountId = userInfo.requiredLong("accountId"),
                    nickname = userInfo.requiredString("nickname"),
                    avatarUrl = userInfo.requiredString("avatarUrl"),
                    vipType = userInfo.requiredInt("vipType"),
                    level = userInfo.optionalInt("level"),
                    signature = userInfo.optionalString("signature"),
                    backgroundUrl = userInfo.optionalString("backgroundUrl"),
                    playlistCount = userInfo.optionalInt("playlistCount"),
                    followeds = userInfo.optionalInt("followeds"),
                    follows = userInfo.optionalInt("follows"),
                    eventCount = userInfo.optionalInt("eventCount"),
                    listenSongs = userInfo.optionalInt("listenSongs"),
                    accountIdentity = userInfo.optionalString("accountIdentity")
                ),
                lastValidatedAtMs = payload.requiredLong("lastValidatedAtMs")
            )
        }.getOrNull()
    }
}

private fun JsonObject.requiredObject(key: String): JsonObject = getValue(key).jsonObject

private fun JsonObject.requiredString(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.optionalString(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.requiredLong(key: String): Long = getValue(key).jsonPrimitive.longOrNull
    ?: error("Missing long for $key")

private fun JsonObject.requiredInt(key: String): Int = getValue(key).jsonPrimitive.intOrNull
    ?: error("Missing int for $key")

private fun JsonObject.optionalInt(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
