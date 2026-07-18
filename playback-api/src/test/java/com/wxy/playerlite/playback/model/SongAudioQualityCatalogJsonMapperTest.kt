package com.wxy.playerlite.playback.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongAudioQualityCatalogJsonMapperTest {
    @Test
    fun parseCatalog_mapsCurrentMusicDetailObjectShape() {
        val payload = Json.parseToJsonElement(
            """
            {
              "data": {
                "songId": 296839,
                "h": {
                  "br": 320000,
                  "size": 9894182,
                  "vd": -29754,
                  "sr": 44100
                },
                "m": {
                  "br": 192000,
                  "size": 5936527,
                  "vd": -27110,
                  "sr": 44100
                },
                "l": {
                  "br": 128000,
                  "size": 3957699,
                  "vd": -25346,
                  "sr": 44100
                },
                "sq": {
                  "br": 810381,
                  "size": 25048891,
                  "vd": -29751,
                  "sr": 44100
                },
                "hr": null,
                "vi": {
                  "br": 832042,
                  "size": 25718154,
                  "vd": 78,
                  "sr": 44100
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val catalog = SongAudioQualityCatalogJsonMapper.parseCatalog(
            payload = payload,
            songId = "296839"
        )

        assertEquals("296839", catalog.songId)
        assertEquals(
            listOf(
                PlaybackAudioQuality.LOSSLESS,
                PlaybackAudioQuality.EXHIGH,
                PlaybackAudioQuality.STANDARD
            ),
            catalog.options.map { it.quality }
        )
        assertEquals(listOf("sq", "h", "m"), catalog.options.map { it.rawKey })
        assertEquals(810_381, catalog.options.first().bitRate)
    }

    @Test
    fun parseCatalog_sortsAvailableQualitiesFromHighToLowAndMapsMetadata() {
        val payload = Json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": 347230,
                  "standard": {
                    "br": 128000,
                    "size": 3210000,
                    "vd": -120,
                    "sr": 44100
                  },
                  "lossless": {
                    "br": 999000,
                    "size": 21321000,
                    "vd": -85,
                    "sr": 44100
                  },
                  "jymaster": {
                    "br": 1999000,
                    "size": 48321000,
                    "vd": -60,
                    "sr": 192000
                  }
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val catalog = SongAudioQualityCatalogJsonMapper.parseCatalog(
            payload = payload,
            songId = "347230"
        )

        assertEquals("347230", catalog.songId)
        assertEquals(
            listOf(
                PlaybackAudioQuality.JYMASTER,
                PlaybackAudioQuality.LOSSLESS,
                PlaybackAudioQuality.STANDARD
            ),
            catalog.options.map { it.quality }
        )
        assertEquals(1_999_000, catalog.options.first().bitRate)
        assertEquals(48_321_000L, catalog.options.first().sizeBytes)
        assertEquals(-60.0, catalog.options.first().volumeDelta ?: 0.0, 0.0)
        assertEquals(192_000, catalog.options.first().sampleRate)
    }

    @Test
    fun parseCatalog_keepsQualityWhenSomeMetadataIsMissing() {
        val payload = Json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": 1,
                  "higher": {
                    "br": 192000
                  }
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val catalog = SongAudioQualityCatalogJsonMapper.parseCatalog(
            payload = payload,
            songId = "1"
        )

        val option = catalog.options.single()
        assertEquals(PlaybackAudioQuality.HIGHER, option.quality)
        assertEquals(192_000, option.bitRate)
        assertNull(option.sizeBytes)
        assertNull(option.volumeDelta)
        assertNull(option.sampleRate)
    }
}
