package com.wxy.playerlite.feature.artist

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArtistDetailAlbumsTabRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentAlbums_clickAlbumCard_shouldDispatchAlbumId() {
        var clickedAlbumId: String? = null

        composeRule.setContent {
            PlayerLiteTheme {
                LazyColumn {
                    artistAlbumsTabPanel(
                        albumsState = ArtistAlbumsUiState.Content(
                            items = listOf(
                                ArtistAlbumRow(
                                    albumId = "album-42",
                                    title = "最伟大的作品",
                                    artistText = "周杰伦",
                                    coverUrl = null,
                                    trackCount = 12,
                                    type = "录音室专辑",
                                    publishTimeText = "2022-07-15"
                                )
                            ),
                            hasMore = false
                        ),
                        onRetry = {},
                        onLoadMoreAlbums = {},
                        onAlbumClick = { clickedAlbumId = it }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("artist_album_album-42").performClick()
        composeRule.runOnIdle {
            assertEquals("album-42", clickedAlbumId)
        }
    }
}
