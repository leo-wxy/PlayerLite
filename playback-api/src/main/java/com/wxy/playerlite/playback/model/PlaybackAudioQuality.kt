package com.wxy.playerlite.playback.model

enum class PlaybackAudioQuality(
    val wireValue: String,
    val displayName: String,
    val sortOrder: Int
) {
    STANDARD("standard", "标准", 10),
    HIGHER("higher", "较高", 20),
    EXHIGH("exhigh", "极高", 30),
    LOSSLESS("lossless", "无损", 40),
    HIRES("hires", "Hi-Res", 50),
    JYEFFECT("jyeffect", "高清环绕", 60),
    SKY("sky", "沉浸环绕", 70),
    DOLBY("dolby", "杜比全景声", 80),
    VIVID("vivid", "Vivid", 90),
    JYMASTER("jymaster", "超清母带", 100),
    UNKNOWN("unknown", "未知音质", 0);

    companion object {
        val descendingPreference: List<PlaybackAudioQuality> = entries
            .filterNot { it == UNKNOWN }
            .sortedByDescending { it.sortOrder }

        fun fromWireValue(value: String?): PlaybackAudioQuality? {
            if (value.isNullOrBlank()) {
                return null
            }
            return entries.firstOrNull { it.wireValue == value }
        }

        fun fromApiKey(value: String?): PlaybackAudioQuality? {
            if (value.isNullOrBlank()) {
                return null
            }
            val normalized = value.trim().lowercase().replace("_", "")
            return when (normalized) {
                STANDARD.wireValue -> STANDARD
                HIGHER.wireValue -> HIGHER
                EXHIGH.wireValue -> EXHIGH
                LOSSLESS.wireValue -> LOSSLESS
                HIRES.wireValue -> HIRES
                JYEFFECT.wireValue -> JYEFFECT
                SKY.wireValue -> SKY
                DOLBY.wireValue -> DOLBY
                VIVID.wireValue -> VIVID
                JYMASTER.wireValue -> JYMASTER
                "h" -> EXHIGH
                "m" -> STANDARD
                "sq" -> LOSSLESS
                "hr" -> HIRES
                "db" -> DOLBY
                "jm" -> JYMASTER
                "je" -> JYEFFECT
                "sk" -> SKY
                "vi" -> VIVID
                else -> UNKNOWN
            }
        }
    }
}
