package com.wxy.playerlite.player

enum class AudioEffectPreset(
    val nativeCode: Int,
    val wireValue: String,
    val displayName: String
) {
    OFF(
        nativeCode = 0,
        wireValue = "off",
        displayName = "原声"
    ),
    BASS_BOOST(
        nativeCode = 1,
        wireValue = "bass-boost",
        displayName = "低音增强"
    ),
    VOCAL_BOOST(
        nativeCode = 2,
        wireValue = "vocal-boost",
        displayName = "人声增强"
    ),
    BRIGHT(
        nativeCode = 3,
        wireValue = "bright",
        displayName = "清亮高频"
    ),
    WARM(
        nativeCode = 4,
        wireValue = "warm",
        displayName = "温暖柔和"
    );

    companion object {
        val DEFAULT: AudioEffectPreset = OFF

        fun fromWireValue(wireValue: String?): AudioEffectPreset {
            return entries.firstOrNull { it.wireValue == wireValue } ?: DEFAULT
        }

        fun fromNativeCode(nativeCode: Int): AudioEffectPreset {
            return entries.firstOrNull { it.nativeCode == nativeCode } ?: DEFAULT
        }
    }
}
