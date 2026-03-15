package com.wxy.playerlite.playback.process

import android.app.ForegroundServiceStartNotAllowedException

internal data class ForegroundNotificationUpdateResult(
    val runningInForeground: Boolean,
    val notified: Boolean,
    val foregroundPromotionRejected: Boolean = false
)

internal class ForegroundNotificationController(
    private val startForeground: () -> Unit,
    private val stopForeground: () -> Unit,
    private val notify: () -> Unit,
    private val cancel: () -> Unit,
    private val onForegroundStartRejected: (String) -> Unit = {}
) {
    fun update(
        runningInForeground: Boolean,
        shouldForeground: Boolean,
        hasPlayableContext: Boolean,
        allowForegroundPromotion: Boolean = true
    ): ForegroundNotificationUpdateResult {
        if (!hasPlayableContext && !shouldForeground) {
            if (runningInForeground) {
                stopForeground()
            }
            cancel()
            return ForegroundNotificationUpdateResult(
                runningInForeground = false,
                notified = false
            )
        }

        if (shouldForeground) {
            if (runningInForeground) {
                notify()
                return ForegroundNotificationUpdateResult(
                    runningInForeground = true,
                    notified = true
                )
            }
            if (!allowForegroundPromotion) {
                notify()
                return ForegroundNotificationUpdateResult(
                    runningInForeground = false,
                    notified = true
                )
            }
            return try {
                startForeground()
                ForegroundNotificationUpdateResult(
                    runningInForeground = true,
                    notified = false
                )
            } catch (error: ForegroundServiceStartNotAllowedException) {
                onForegroundStartRejected(
                    error.message.orEmpty().ifBlank { error.toString() }
                )
                notify()
                ForegroundNotificationUpdateResult(
                    runningInForeground = false,
                    notified = true,
                    foregroundPromotionRejected = true
                )
            }
        }

        if (runningInForeground) {
            stopForeground()
        }
        notify()
        return ForegroundNotificationUpdateResult(
            runningInForeground = false,
            notified = true
        )
    }
}
