package com.wxy.playerlite.feature.webplaylistimport

import org.junit.Assert.assertEquals
import org.junit.Test

class WebPlaylistImportUrlParserTest {
    @Test
    fun parse_shouldExtractPlaylistIdFromNeteaseShareUrl() {
        val result = WebPlaylistImportUrlParser().parse(
            "https://music.163.com/#/playlist?id=17729789137"
        )

        assertEquals(ImportedPlaylistSource.NETEASE, result.source)
        assertEquals("17729789137", result.playlistId)
    }

    @Test
    fun parse_shouldExtractPlaylistIdFromQqMusicShareUrl() {
        val result = WebPlaylistImportUrlParser().parse(
            "https://i.y.qq.com/n2/m/share/details/taoge.html?platform=11&id=7217720898"
        )

        assertEquals(ImportedPlaylistSource.QQ_MUSIC, result.source)
        assertEquals("7217720898", result.playlistId)
    }
}
