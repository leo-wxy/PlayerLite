package com.wxy.playerlite.core.playback

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongDetailJsonMapperTest {
    @Test
    fun parseSongs_mapsRawSongDetailFieldsToSemanticMusicInfo() {
        val payload = Json.parseToJsonElement(
            """
            {
              "songs": [
                {
                  "id": 347230,
                  "name": "晴天",
                  "ar": [
                    { "id": 6452, "name": "周杰伦" },
                    { "id": 4493, "name": "杨瑞代" }
                  ],
                  "al": {
                    "name": "叶惠美",
                    "picUrl": "https://example.com/qingtian.jpg"
                  },
                  "dt": 269000
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val songs = SongDetailJsonMapper.parseSongs(payload)

        assertEquals(1, songs.size)
        val song = songs.single()
        assertEquals("347230", song.id)
        assertEquals("347230", song.songId)
        assertEquals("晴天", song.title)
        assertEquals(listOf("周杰伦", "杨瑞代"), song.artistNames)
        assertEquals(listOf("6452", "4493"), song.artistIds)
        assertEquals("叶惠美", song.albumTitle)
        assertEquals("https://example.com/qingtian.jpg", song.coverUrl)
        assertEquals(269_000L, song.durationMs)
        assertEquals("周杰伦 / 杨瑞代", song.toPlayableItem().artistText)
    }

    @Test
    fun parseSongs_handlesMissingOptionalFields() {
        val payload = Json.parseToJsonElement(
            """
            {
              "songs": [
                {
                  "id": 1,
                  "name": "纯音乐"
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val song = SongDetailJsonMapper.parseSongs(payload).single()

        assertEquals("1", song.songId)
        assertEquals(emptyList<String>(), song.artistNames)
        assertNull(song.albumTitle)
        assertNull(song.coverUrl)
        assertEquals(0L, song.durationMs)
    }
}
