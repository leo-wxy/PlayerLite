package com.wxy.playerlite.player

import java.util.Locale
import kotlin.math.roundToInt

data class PlaybackSpeedOption(
    val index: Int,
    val value: Float
) {
    val label: String
        get() = PlaybackSpeed.format(value)
}

object PlaybackSpeed {
    private const val MIN_TENTHS = 5
    private const val MAX_TENTHS = 20

    val DEFAULT: PlaybackSpeedOption = fromValue(1.0f)

    fun fromIndex(index: Int): PlaybackSpeedOption {
        val normalizedIndex = index.coerceIn(0, MAX_TENTHS - MIN_TENTHS)
        val value = (MIN_TENTHS + normalizedIndex) / 10f
        return PlaybackSpeedOption(
            index = normalizedIndex,
            value = value
        )
    }

    fun fromValue(value: Float): PlaybackSpeedOption {
        return fromIndex(indexFromValue(value))
    }

    fun normalizeValue(value: Float): Float {
        return fromValue(value).value
    }

    fun indexFromValue(value: Float): Int {
        val tenths = (value * 10f).roundToInt().coerceIn(MIN_TENTHS, MAX_TENTHS)
        return tenths - MIN_TENTHS
    }

    fun format(value: Float): String {
        return String.format(Locale.US, "%.1fX", normalizeValue(value))
    }
}
