package com.wxy.playerlite.feature.main

import com.wxy.playerlite.feature.search.SearchRouteTarget
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
    fun fetchHomeOverview_shouldFallbackKeywordsWhenSearchDefaultFails() = runBlocking {
        val repository = DefaultHomeDiscoveryRepository(
            remoteDataSource = FakeHomeDiscoveryRemoteDataSource(
                homepagePayload = jsonObject(
                    """
                    {
                      "data": {
                        "blocks": []
                      }
                    }
                    """
                ),
                searchDefaultError = IllegalStateException("boom")
            )
        )

        val result = repository.fetchHomeOverview()

        assertEquals(HomeDefaults.fallbackSearchKeywords, result.searchKeywords)
        assertTrue(result.sections.isEmpty())
    }

    @Test
    fun fetchHomeOverview_shouldMapContentActionsForDetailAndUriTargets() = runBlocking {
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
                                  "bannerId": "banner-album",
                                  "pic": "http://example.com/banner.jpg",
                                  "mainTitle": "新碟推荐",
                                  "typeTitle": "专辑",
                                  "targetType": 10,
                                  "targetId": 32311
                                }
                              ]
                            }
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
                                    "resourceId": 3778678,
                                    "uiElement": {
                                      "mainTitle": {
                                        "title": "热歌榜"
                                      },
                                      "subTitle": {
                                        "title": "官方榜单"
                                      }
                                    }
                                  }
                                ]
                              }
                            ]
                          },
                          {
                            "blockCode": "HOMEPAGE_SHORTCUT",
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
                                    "resourceId": "shortcut-1",
                                    "action": {
                                      "url": "https://music.163.com/topic?id=1"
                                    },
                                    "uiElement": {
                                      "mainTitle": {
                                        "title": "专题活动"
                                      },
                                      "subTitle": {
                                        "title": "外部链接"
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

        val bannerAction = result.sections[0].items.single().action
        assertTrue(bannerAction is HomeEntryAction.OpenContent)
        assertEquals(
            ContentEntryAction.OpenDetail(
                SearchRouteTarget.Album(albumId = "32311")
            ),
            (bannerAction as HomeEntryAction.OpenContent).entry
        )

        val playlistAction = result.sections[1].items.single().action
        assertTrue(playlistAction is HomeEntryAction.OpenContent)
        assertEquals(
            ContentEntryAction.OpenDetail(
                SearchRouteTarget.Playlist(playlistId = "3778678")
            ),
            (playlistAction as HomeEntryAction.OpenContent).entry
        )

        val shortcutAction = result.sections[2].items.single().action
        assertTrue(shortcutAction is HomeEntryAction.OpenContent)
        assertEquals(
            ContentEntryAction.OpenUri(
                uri = "https://music.163.com/topic?id=1"
            ),
            (shortcutAction as HomeEntryAction.OpenContent).entry
        )
    }

    @Test
    fun fetchHomeOverview_shouldMapArtistDetailTargetsFromHomepageBlocksAndResources() = runBlocking {
        val repository = DefaultHomeDiscoveryRepository(
            remoteDataSource = FakeHomeDiscoveryRemoteDataSource(
                homepagePayload = jsonObject(
                    """
                    {
                      "data": {
                        "blocks": [
                          {
                            "blockCode": "HOMEPAGE_BLOCK_ARTIST_RECOMMEND",
                            "showType": "HOMEPAGE_SLIDE_PLAYLIST",
                            "uiElement": {
                              "subTitle": {
                                "title": "推荐歌手"
                              }
                            },
                            "creatives": [
                              {
                                "resources": [
                                  {
                                    "resourceId": 6452,
                                    "uiElement": {
                                      "mainTitle": {
                                        "title": "羊文学"
                                      },
                                      "subTitle": {
                                        "title": "另类摇滚"
                                      },
                                      "image": {
                                        "imageUrl": "http://example.com/hitsujibungaku.jpg"
                                      },
                                      "labelTexts": ["乐队", "J-Rock"]
                                    }
                                  }
                                ]
                              }
                            ]
                          },
                          {
                            "blockCode": "HOMEPAGE_SHORTCUT",
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
                                    "resourceId": "2116",
                                    "resourceType": 100,
                                    "uiElement": {
                                      "mainTitle": {
                                        "title": "RADWIMPS"
                                      },
                                      "image": {
                                        "imageUrl": "http://example.com/radwimps.jpg"
                                      },
                                      "labelTexts": ["日本摇滚", "乐队"]
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

        assertEquals(2, result.sections.size)
        assertEquals("推荐歌手", result.sections[0].title)
        assertEquals(HomeSectionLayout.HORIZONTAL_LIST, result.sections[0].layout)
        assertEquals("羊文学", result.sections[0].items.single().title)
        assertEquals("另类摇滚", result.sections[0].items.single().subtitle)
        assertEquals(
            "http://example.com/hitsujibungaku.jpg",
            result.sections[0].items.single().imageUrl
        )
        val artistBlockAction = result.sections[0].items.single().action
        assertTrue(artistBlockAction is HomeEntryAction.OpenContent)
        assertEquals(
            ContentEntryAction.OpenDetail(
                SearchRouteTarget.Artist(artistId = "6452")
            ),
            (artistBlockAction as HomeEntryAction.OpenContent).entry
        )

        assertEquals("快捷入口", result.sections[1].title)
        assertEquals(HomeSectionLayout.ICON_GRID, result.sections[1].layout)
        assertEquals("RADWIMPS", result.sections[1].items.single().title)
        assertEquals("日本摇滚 · 乐队", result.sections[1].items.single().subtitle)
        assertEquals("日本摇滚", result.sections[1].items.single().badge)
        assertEquals(
            "http://example.com/radwimps.jpg",
            result.sections[1].items.single().imageUrl
        )
        val artistResourceAction = result.sections[1].items.single().action
        assertTrue(artistResourceAction is HomeEntryAction.OpenContent)
        assertEquals(
            ContentEntryAction.OpenDetail(
                SearchRouteTarget.Artist(artistId = "2116")
            ),
            (artistResourceAction as HomeEntryAction.OpenContent).entry
        )
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

        assertEquals(1, result.sections.size)
        assertEquals(
            HomeEntryAction.OpenContent(ContentEntryAction.OpenDailyRecommendedSongs),
            result.sections.single().items.single().action
        )
    }

    @Test
    fun fetchHomeOverview_shouldMapSongResourcesIntoPlayableSongCards() = runBlocking {
        val repository = DefaultHomeDiscoveryRepository(
            remoteDataSource = FakeHomeDiscoveryRemoteDataSource(
                homepagePayload = jsonObject(
                    """
                    {
                      "data": {
                        "blocks": [
                          {
                            "blockCode": "HOMEPAGE_BLOCK_STYLE_RCMD",
                            "showType": "HOMEPAGE_SLIDE_SONGLIST",
                            "uiElement": {
                              "subTitle": {
                                "title": "微醺爵士 邀你起舞"
                              }
                            },
                            "creatives": [
                              {
                                "creativeType": "SONG_LIST_HOMEPAGE",
                                "resources": [
                                  {
                                    "resourceType": "song",
                                    "resourceId": "song-1",
                                    "action": "play_all_song_from_current_index",
                                    "uiElement": {
                                      "mainTitle": {
                                        "title": "MASCARA"
                                      },
                                      "subTitle": {
                                        "title": "超71%人播放"
                                      },
                                      "image": {
                                        "imageUrl": "http://example.com/song-1.jpg"
                                      }
                                    },
                                    "resourceExtInfo": {
                                      "artists": [
                                        {
                                          "id": 51849113,
                                          "name": "XG"
                                        }
                                      ],
                                      "songData": {
                                        "duration": 191439,
                                        "album": {
                                          "id": 147030576,
                                          "name": "MASCARA",
                                          "picUrl": "http://example.com/song-1.jpg"
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "resourceType": "song",
                                    "resourceId": "song-2",
                                    "action": "play_all_song_from_current_index",
                                    "uiElement": {
                                      "mainTitle": {
                                        "title": "Lover Girl"
                                      },
                                      "subTitle": {
                                        "title": "今日推荐"
                                      },
                                      "image": {
                                        "imageUrl": "http://example.com/song-2.jpg"
                                      }
                                    },
                                    "resourceExtInfo": {
                                      "artists": [
                                        {
                                          "id": 42,
                                          "name": "Luna"
                                        }
                                      ],
                                      "songData": {
                                        "duration": 205000,
                                        "album": {
                                          "id": 222,
                                          "name": "Midnight Club",
                                          "picUrl": "http://example.com/song-2.jpg"
                                        }
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

        val section = result.sections.single()
        assertEquals("微醺爵士 邀你起舞", section.title)
        assertEquals(HomeSectionLayout.HORIZONTAL_LIST, section.layout)

        val firstItem = section.items.first()
        val songCard = firstItem.songCard
        assertNotNull(songCard)
        assertEquals("XG · MASCARA", songCard?.metadataLine)
        assertEquals("超71%人播放", songCard?.recommendReason)
        assertEquals(191_439L, songCard?.durationMs)

        val playAction = firstItem.action as HomeEntryAction.ReplaceQueueAndOpenPlayer
        assertEquals(0, playAction.activeIndex)
        assertEquals(listOf("song-1", "song-2"), playAction.items.map(PlaylistItem::songId))
        assertEquals("song-1", playAction.items.first().songId)
        assertEquals("XG", playAction.items.first().artistText)
        assertEquals("MASCARA", playAction.items.first().albumTitle)

        val menuActions = songCard?.menuActions.orEmpty()
        assertEquals(listOf("下一首播放", "查看专辑", "查看歌手"), menuActions.map { it.label })
        assertTrue(menuActions[0].action is HomeEntryAction.InsertNext)
        assertEquals(
            HomeEntryAction.OpenContent(
                ContentEntryAction.OpenDetail(SearchRouteTarget.Album(albumId = "147030576"))
            ),
            menuActions[1].action
        )
        assertEquals(
            HomeEntryAction.OpenContent(
                ContentEntryAction.OpenDetail(SearchRouteTarget.Artist(artistId = "51849113"))
            ),
            menuActions[2].action
        )
    }

    @Test
    fun fetchHomeOverview_shouldBuildBlockWideQueueForMultiCreativeSongBlocks() = runBlocking {
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
        assertEquals(12, section.items.size)

        val expectedSongIds = (1..12).map { "song-$it" }

        val firstAction = section.items.first().action as HomeEntryAction.ReplaceQueueAndOpenPlayer
        assertEquals(expectedSongIds, firstAction.items.map(PlaylistItem::songId))
        assertEquals(0, firstAction.activeIndex)

        val fourthAction = section.items[3].action as HomeEntryAction.ReplaceQueueAndOpenPlayer
        assertEquals(expectedSongIds, fourthAction.items.map(PlaylistItem::songId))
        assertEquals(3, fourthAction.activeIndex)

        val lastAction = section.items.last().action as HomeEntryAction.ReplaceQueueAndOpenPlayer
        assertEquals(expectedSongIds, lastAction.items.map(PlaylistItem::songId))
        assertEquals(11, lastAction.activeIndex)
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
                "showType": "HOMEPAGE_SLIDE_SONGLIST_ALIGN",
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
