package com.wxy.playerlite.playback.process

import android.app.ForegroundServiceStartNotAllowedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundNotificationControllerTest {
    @Test
    fun update_whenForegroundPromotionIsRejected_shouldFallBackToRegularNotification() {
        var notifyCalls = 0
        var rejectedMessage: String? = null
        val controller = ForegroundNotificationController(
            startForeground = {
                throw ForegroundServiceStartNotAllowedException("rejected")
            },
            stopForeground = {},
            notify = { notifyCalls += 1 },
            cancel = {},
            onForegroundStartRejected = { rejectedMessage = it }
        )

        val result = controller.update(
            runningInForeground = false,
            shouldForeground = true,
            hasPlayableContext = true
        )

        assertFalse(result.runningInForeground)
        assertTrue(result.notified)
        assertEquals(1, notifyCalls)
        assertTrue(rejectedMessage?.isNotBlank() == true)
    }
}
