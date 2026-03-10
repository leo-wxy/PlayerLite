package com.wxy.playerlite.user.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeteaseUserJsonMapperTest {
    @Test
    fun parseLoginResponse_shouldMapStableUserInfoFields() {
        val payload = Json.parseToJsonElement(
            """
            {
              "code": 200,
              "cookie": "MUSIC_U=token; __csrf=csrf-token;",
              "account": {
                "id": 88,
                "userName": "13800138000"
              },
              "profile": {
                "userId": 77,
                "nickname": "Codex",
                "avatarUrl": "https://example.com/avatar.jpg",
                "vipType": 11,
                "signature": "hello",
                "backgroundUrl": "https://example.com/bg.jpg",
                "playlistCount": 9,
                "followeds": 8,
                "follows": 7,
                "eventCount": 6
              }
            }
            """.trimIndent()
        ).jsonObject

        val session = NeteaseUserJsonMapper.parseLoginResponse(payload)

        assertEquals(77L, session.userInfo.userId)
        assertEquals(88L, session.userInfo.accountId)
        assertEquals("Codex", session.userInfo.nickname)
        assertEquals("https://example.com/avatar.jpg", session.userInfo.avatarUrl)
        assertEquals(11, session.userInfo.vipType)
        assertEquals("13800138000", session.userInfo.accountIdentity)
        assertEquals("csrf-token", session.csrfToken)
        assertEquals(9, session.userInfo.playlistCount)
        assertEquals(8, session.userInfo.followeds)
        assertEquals(7, session.userInfo.follows)
        assertEquals(6, session.userInfo.eventCount)
    }

    @Test
    fun mergeUserDetail_shouldOverlayExtendedFieldsWithoutLosingStableIdentity() {
        val current = UserInfoFixture.loggedIn()
        val payload = Json.parseToJsonElement(
            """
            {
              "level": 10,
              "listenSongs": 123,
              "profile": {
                "userId": 77,
                "nickname": "Codex Pro",
                "avatarUrl": "https://example.com/avatar-2.jpg",
                "vipType": 12,
                "signature": "updated",
                "backgroundUrl": "https://example.com/bg-2.jpg",
                "playlistCount": 11,
                "followeds": 22,
                "follows": 33,
                "eventCount": 44
              }
            }
            """.trimIndent()
        ).jsonObject

        val merged = NeteaseUserJsonMapper.mergeUserDetail(current, payload)

        assertEquals(77L, merged.userId)
        assertEquals(88L, merged.accountId)
        assertEquals("Codex Pro", merged.nickname)
        assertEquals("https://example.com/avatar-2.jpg", merged.avatarUrl)
        assertEquals(12, merged.vipType)
        assertEquals(10, merged.level)
        assertEquals(123, merged.listenSongs)
        assertEquals("updated", merged.signature)
        assertEquals("https://example.com/bg-2.jpg", merged.backgroundUrl)
        assertEquals(11, merged.playlistCount)
        assertEquals(22, merged.followeds)
        assertEquals(33, merged.follows)
        assertEquals(44, merged.eventCount)
    }

    @Test
    fun extractCsrfToken_shouldReturnNullWhenCookieMissingToken() {
        assertNull(NeteaseUserJsonMapper.extractCsrfToken("MUSIC_U=token_only;"))
    }
}
