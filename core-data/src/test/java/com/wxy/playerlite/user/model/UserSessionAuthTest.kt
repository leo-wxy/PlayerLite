package com.wxy.playerlite.user.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UserSessionAuthTest {
    @Test
    fun toAuthHeaders_shouldStripCookieAttributesAndKeepStableCookiePairs() {
        val session = UserSession(
            cookie = buildString {
                append("MUSIC_U=token; Max-Age=15552000; Expires=Sun, 06 Sep 2026 15:41:51 GMT; Path=/; ")
                append("__csrf=csrf-token; Max-Age=1296010; Path=/; ")
                append("NMTID=device-token; Max-Age=315360000; Path=/")
            },
            csrfToken = "csrf-token",
            userInfo = UserInfo(
                userId = 1L,
                accountId = 1L,
                nickname = "tester",
                avatarUrl = "http://example.com/avatar.jpg",
                vipType = 0,
                level = null,
                signature = null,
                backgroundUrl = null,
                playlistCount = null,
                followeds = null,
                follows = null,
                eventCount = null,
                listenSongs = null,
                accountIdentity = null
            ),
            lastValidatedAtMs = 0L
        )

        val headers = session.toAuthHeaders()

        assertEquals(
            "MUSIC_U=token; __csrf=csrf-token; NMTID=device-token",
            headers["Cookie"]
        )
        assertEquals("csrf-token", headers["X-CSRF-Token"])
    }
}
