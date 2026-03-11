package com.wxy.playerlite.feature.main

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
