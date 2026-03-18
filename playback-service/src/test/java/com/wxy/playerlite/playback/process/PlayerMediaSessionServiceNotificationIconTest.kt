package com.wxy.playerlite.playback.process

import com.wxy.playerlite.playback.service.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMediaSessionServiceNotificationIconTest {
    @Test
    fun resolveNotificationSmallIcon_shouldAlwaysUseDedicatedNotificationIcon() {
        assertEquals(
            R.drawable.ic_playerlite_notification_small,
            resolveNotificationSmallIcon()
        )
    }

    @Test
    fun resolveNotificationSmallIcon_shouldNotFallBackToSystemMediaPlayIcon() {
        assertEquals(
            R.drawable.ic_playerlite_notification_small,
            resolveNotificationSmallIcon()
        )
    }
}
