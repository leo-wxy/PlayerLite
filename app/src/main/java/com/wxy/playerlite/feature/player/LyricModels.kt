package com.wxy.playerlite.feature.player

internal data class ParsedLyrics(
    val songId: String,
    val lines: List<LyricLine>,
    val rawText: String
)

internal data class LyricLine(
    val timestampMs: Long,
    val text: String
)
