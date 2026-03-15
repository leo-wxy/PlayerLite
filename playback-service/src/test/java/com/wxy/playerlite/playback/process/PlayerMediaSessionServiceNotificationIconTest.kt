package com.wxy.playerlite.playback.process

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMediaSessionServiceNotificationIconTest {
    @Test
    fun resolveNotificationSmallIcon_whenAppIconExists_shouldUseAppIcon() {
        assertEquals(42, resolveNotificationSmallIcon(42))
    }

    @Test
    fun resolveNotificationSmallIcon_whenAppIconMissing_shouldFallBackToSystemMediaIcon() {
        assertEquals(
            android.R.drawable.ic_media_play,
            resolveNotificationSmallIcon(0)
        )
    }
}
