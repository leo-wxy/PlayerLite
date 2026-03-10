package com.wxy.playerlite.user.storage

import com.wxy.playerlite.user.model.UserSession
import com.wxy.playerlite.user.remote.UserInfoFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserSessionSnapshotCodecTest {
    @Test
    fun encodeDecode_shouldRoundTripSessionSnapshot() {
        val session = UserSession(
            cookie = "MUSIC_U=token; __csrf=csrf-token;",
            csrfToken = "csrf-token",
            userInfo = UserInfoFixture.loggedIn(),
            lastValidatedAtMs = 1_234L
        )

        val encoded = UserSessionSnapshotCodec.encode(session)
        val decoded = UserSessionSnapshotCodec.decode(encoded)

        assertEquals(session, decoded)
    }

    @Test
    fun decode_shouldReturnNullForBrokenPayload() {
        assertNull(UserSessionSnapshotCodec.decode("not-json"))
    }
}
