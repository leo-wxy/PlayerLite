package com.wxy.playerlite.feature.main

import com.wxy.playerlite.feature.search.SearchRouteTarget
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
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
        assertTrue(bannerAction is ContentEntryAction.OpenDetail)
        assertEquals(
            SearchRouteTarget.Album(albumId = "32311"),
            (bannerAction as ContentEntryAction.OpenDetail).target
        )

        val playlistAction = result.sections[1].items.single().action
        assertTrue(playlistAction is ContentEntryAction.OpenDetail)
        assertEquals(
            SearchRouteTarget.Playlist(playlistId = "3778678"),
            (playlistAction as ContentEntryAction.OpenDetail).target
        )

        val shortcutAction = result.sections[2].items.single().action
        assertTrue(shortcutAction is ContentEntryAction.OpenUri)
        assertEquals(
            "https://music.163.com/topic?id=1",
            (shortcutAction as ContentEntryAction.OpenUri).uri
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
        assertTrue(artistBlockAction is ContentEntryAction.OpenDetail)
        assertEquals(
            SearchRouteTarget.Artist(artistId = "6452"),
            (artistBlockAction as ContentEntryAction.OpenDetail).target
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
        assertTrue(artistResourceAction is ContentEntryAction.OpenDetail)
        assertEquals(
            SearchRouteTarget.Artist(artistId = "2116"),
            (artistResourceAction as ContentEntryAction.OpenDetail).target
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
