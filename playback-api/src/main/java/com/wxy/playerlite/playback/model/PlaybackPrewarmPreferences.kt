package com.wxy.playerlite.playback.model

data class PlaybackPrewarmPreferences(
    val enabled: Boolean = true,
    val budgetDurationMs: Long = DEFAULT_BUDGET_DURATION_MS,
    val budgetBytes: Long = DEFAULT_BUDGET_BYTES,
    val readyThresholdDurationMs: Long = DEFAULT_READY_THRESHOLD_DURATION_MS,
    val readyThresholdBytes: Long = DEFAULT_READY_THRESHOLD_BYTES
) {
    fun sanitized(): PlaybackPrewarmPreferences {
        val normalizedBudgetDurationMs = budgetDurationMs.coerceIn(
            MIN_BUDGET_DURATION_MS,
            MAX_BUDGET_DURATION_MS
        )
        val normalizedBudgetBytes = budgetBytes.coerceIn(
            MIN_BUDGET_BYTES,
            MAX_BUDGET_BYTES
        )
        return copy(
            budgetDurationMs = normalizedBudgetDurationMs,
            budgetBytes = normalizedBudgetBytes,
            readyThresholdDurationMs = readyThresholdDurationMs.coerceIn(
                MIN_READY_THRESHOLD_DURATION_MS,
                normalizedBudgetDurationMs
            ),
            readyThresholdBytes = readyThresholdBytes.coerceIn(
                MIN_READY_THRESHOLD_BYTES,
                normalizedBudgetBytes
            )
        )
    }

    companion object {
        const val DEFAULT_BUDGET_DURATION_MS = 60_000L
        const val DEFAULT_BUDGET_BYTES = 8L * 1024L * 1024L
        const val DEFAULT_READY_THRESHOLD_DURATION_MS = 5_000L
        const val DEFAULT_READY_THRESHOLD_BYTES = 512L * 1024L

        const val MIN_BUDGET_DURATION_MS = 5_000L
        const val MAX_BUDGET_DURATION_MS = 300_000L
        const val MIN_BUDGET_BYTES = 512L * 1024L
        const val MAX_BUDGET_BYTES = 64L * 1024L * 1024L
        const val MIN_READY_THRESHOLD_DURATION_MS = 1_000L
        const val MIN_READY_THRESHOLD_BYTES = 64L * 1024L
    }
}
