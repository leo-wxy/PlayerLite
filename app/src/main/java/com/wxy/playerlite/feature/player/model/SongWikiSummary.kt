package com.wxy.playerlite.feature.player.model

internal data class SongWikiSummary(
    val title: String,
    val coverUrl: String?,
    val contributionText: String?,
    val sections: List<SongWikiSummarySection>
)

internal data class SongWikiSummarySection(
    val title: String,
    val values: List<String>
)
