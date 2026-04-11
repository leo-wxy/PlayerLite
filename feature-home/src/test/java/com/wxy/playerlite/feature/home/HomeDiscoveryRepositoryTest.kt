package com.wxy.playerlite.feature.home

import com.wxy.playerlite.core.playlist.PlaylistItem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDiscoveryRepositoryTest {
    @Test
    fun fetchHomeOverview_shouldMapSupportedBlocksAndPreserveOrder() = runBlocking {
        val repository = DefaultHomeDiscoveryRepository(
            remoteDataSource = FakeHomeDiscoveryRemoteDataSource(
                homepagePayload = jsonObject(
                    """
                    {
                      "data": {
                        "blocks": [
                          {
                            "blockCode": "HOMEPAGE_BANNER",
                            "showType": "BANNER",
                            "extInfo": {
                              "banners": [
                                {
                                  "bannerId": "b-1",
                                  "pic": "http://example.com/banner.jpg",
                                  "typeTitle": "独家策划",
                                  "mainTitle": null
                                }
                              ]
                            }
                          },
                          {
                            "blockCode": "UNSUPPORTED_EMPTY",
                            "showType": "UNKNOWN",
                            "creatives": []
                          },
                          {
                            "blockCode": "HOMEPAGE_BLOCK_PLAYLIST_RCMD",
                            "showType": "HOMEPAGE_SLIDE_PLAYLIST",
                            "uiElement": {
                              "subTitle": {
                                "title": "推荐歌单"
                              }
                            },
                            "creatives": [
                              {
                                "resources": [
                                  {
                                    "resourceId": "playlist-1",
                                    "uiElement": {
                                      "mainTitle": {
                                        "title": "歌单 A"
                                      },
                                      "subTitle": {
                                        "title": "晚安歌单"
                                      },
                                      "image": {
                                        "imageUrl": "http://example.com/playlist.jpg"
                                      },
                                      "labelTexts": ["民谣", "夜晚"]
                                    }
                                  }
                                ]
                              }
                            ]
                          }
                        ]
                      }
                    }
                    """
                ),
                searchDefaultPayload = jsonObject(
                    """
                    {
                      "data": {
                        "showKeyword": "默认热搜 A",
                        "realkeyword": "热搜A"
                      }
                    }
                    """
                )
            )
        )

        val result = repository.fetchHomeOverview()

        assertEquals(2, result.sections.size)
        assertEquals("HOMEPAGE_BANNER", result.sections[0].code)
        assertEquals(HomeSectionLayout.BANNER, result.sections[0].layout)
        assertEquals("独家策划", result.sections[0].items.first().title)
        assertEquals("HOMEPAGE_BLOCK_PLAYLIST_RCMD", result.sections[1].code)
        assertEquals("推荐歌单", result.sections[1].title)
        assertEquals("歌单 A", result.sections[1].items.first().title)
        assertEquals(listOf("默认热搜 A", "热搜A"), result.searchKeywords)
    }

    @Test
    fun fetchHomeOverview_shouldMapDailyRecommendedShortcutToInternalDestination() = runBlocking {
        val repository = DefaultHomeDiscoveryRepository(
            remoteDataSource = FakeHomeDiscoveryRemoteDataSource(
                homepagePayload = jsonObject(
                    """
                    {
                      "data": {
                        "blocks": [
                          {
                            "blockCode": "HOMEPAGE_BLOCK_OLD_DRAGON_BALL",
                            "showType": "DRAGON_BALL",
                            "uiElement": {
                              "subTitle": {
                                "title": "快捷入口"
                              }
                            },
                            "creatives": [
                              {
                                "resources": [
                                  {
                                    "resourceId": "-1",
                                    "action": "orpheus://songrcmd",
                                    "actionType": "orpheus",
                                    "uiElement": {
                                      "mainTitle": {
                                        "title": "每日推荐"
                                      },
                                      "image": {
                                        "imageUrl": "http://example.com/daily.jpg"
                                      }
                                    }
                                  }
                                ]
                              }
                            ]
                          }
                        ]
                      }
                    }
                    """
                ),
                searchDefaultPayload = jsonObject("""{"data":{"showKeyword":"默认热搜"}}""")
            )
        )

        val result = repository.fetchHomeOverview()

        assertEquals(
            HomeAction.OpenContent(HomeContentTarget.DailyRecommendedSongs),
            result.sections.single().items.single().action
        )
    }

    @Test
    fun fetchHomeOverview_shouldMapSongResourcesIntoPlayableSongCardsAndBlockWideQueue() = runBlocking {
        val repository = DefaultHomeDiscoveryRepository(
            remoteDataSource = FakeHomeDiscoveryRemoteDataSource(
                homepagePayload = homepageSongBlockPayload(
                    blockCode = "HOMEPAGE_BLOCK_STYLE_RCMD",
                    title = "微醺爵士 邀你起舞",
                    creativeCount = 4,
                    songsPerCreative = 3
                ),
                searchDefaultPayload = jsonObject("""{"data":{"showKeyword":"默认热搜"}}""")
            )
        )

        val result = repository.fetchHomeOverview()

        val section = result.sections.single()
        assertEquals("微醺爵士 邀你起舞", section.title)
        assertEquals(HomeSectionLayout.HORIZONTAL_LIST, section.layout)
        assertEquals(12, section.items.size)

        val firstItem = section.items.first()
        val firstSongCard = firstItem.songCard
        assertNotNull(firstSongCard)
        assertEquals("Artist 1 · Album 1", firstSongCard?.metadataLine)
        assertEquals("超71%人播放", firstSongCard?.recommendReason)
        assertEquals(180_001L, firstSongCard?.durationMs)

        val playAction = firstItem.action as HomeAction.ReplaceQueueAndOpenPlayer
        val expectedSongIds = (1..12).map { "song-$it" }
        assertEquals(expectedSongIds, playAction.items.map(PlaylistItem::songId))
        assertEquals(0, playAction.activeIndex)

        val fourthAction = section.items[3].action as HomeAction.ReplaceQueueAndOpenPlayer
        assertEquals(expectedSongIds, fourthAction.items.map(PlaylistItem::songId))
        assertEquals(3, fourthAction.activeIndex)

        val menuActions = firstSongCard?.menuActions.orEmpty()
        assertEquals(listOf("下一首播放", "查看专辑", "查看歌手"), menuActions.map { it.label })
        assertTrue(menuActions[0].action is HomeAction.InsertNext)
        assertEquals(
            HomeAction.OpenContent(HomeContentTarget.Album(albumId = "10001")),
            menuActions[1].action
        )
        assertEquals(
            HomeAction.OpenContent(HomeContentTarget.Artist(artistId = "1")),
            menuActions[2].action
        )
    }
}

private class FakeHomeDiscoveryRemoteDataSource(
    private val homepagePayload: JsonObject,
    private val searchDefaultPayload: JsonObject? = null,
    private val searchDefaultError: Throwable? = null
) : HomeDiscoveryRemoteDataSource {
    override suspend fun fetchHomepageBlocks(): JsonObject = homepagePayload

    override suspend fun fetchDefaultSearch(): JsonObject {
        searchDefaultError?.let { throw it }
        return requireNotNull(searchDefaultPayload) { "searchDefaultPayload must be provided" }
    }
}

private fun jsonObject(raw: String): JsonObject {
    return Json.parseToJsonElement(raw.trimIndent()).jsonObject
}

private fun homepageSongBlockPayload(
    blockCode: String,
    title: String,
    creativeCount: Int,
    songsPerCreative: Int
): JsonObject {
    val creatives = (0 until creativeCount).joinToString(separator = ",") { creativeIndex ->
        val resources = (0 until songsPerCreative).joinToString(separator = ",") { songOffset ->
            val songIndex = creativeIndex * songsPerCreative + songOffset + 1
            """
            {
              "resourceType": "song",
              "resourceId": "song-$songIndex",
              "action": "play_all_song_from_current_index",
              "uiElement": {
                "mainTitle": {
                  "title": "Song $songIndex"
                },
                "subTitle": {
                  "title": "超${70 + songIndex}%人播放"
                },
                "image": {
                  "imageUrl": "http://example.com/song-$songIndex.jpg"
                }
              },
              "resourceExtInfo": {
                "artists": [
                  {
                    "id": $songIndex,
                    "name": "Artist $songIndex"
                  }
                ],
                "songData": {
                  "duration": ${180000 + songIndex},
                  "album": {
                    "id": ${10_000 + songIndex},
                    "name": "Album $songIndex",
                    "picUrl": "http://example.com/song-$songIndex.jpg"
                  }
                }
              }
            }
            """
        }
        """
        {
          "creativeType": "SONG_LIST_HOMEPAGE",
          "resources": [
            $resources
          ]
        }
        """
    }
    return jsonObject(
        """
        {
          "data": {
            "blocks": [
              {
                "blockCode": "$blockCode",
                "showType": "HOMEPAGE_SLIDE_SONGLIST",
                "uiElement": {
                  "subTitle": {
                    "title": "$title"
                  }
                },
                "creatives": [
                  $creatives
                ]
              }
            ]
          }
        }
        """
    )
}
