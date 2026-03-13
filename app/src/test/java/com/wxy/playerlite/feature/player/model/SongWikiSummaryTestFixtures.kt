package com.wxy.playerlite.feature.player.model

internal fun demoSongWikiSummary(): SongWikiSummary {
    return SongWikiSummary(
        title = "音乐百科",
        coverUrl = "http://example.com/wiki-cover.jpg",
        contributionText = "参与共建",
        sections = listOf(
            SongWikiSummarySection(
                title = "曲风",
                values = listOf("流行-华语流行")
            ),
            SongWikiSummarySection(
                title = "推荐标签",
                values = listOf("治愈", "悲伤")
            ),
            SongWikiSummarySection(
                title = "语种",
                values = listOf("国语")
            )
        )
    )
}
