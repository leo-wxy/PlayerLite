package com.wxy.playerlite.feature.player

data class ParsedLyrics(
    val songId: String,
    val lines: List<LyricLine>,
    val rawText: String
)

data class LyricLine(
    val timestampMs: Long,
    val text: String
)
