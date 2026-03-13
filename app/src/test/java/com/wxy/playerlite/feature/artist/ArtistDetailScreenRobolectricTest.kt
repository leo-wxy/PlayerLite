package com.wxy.playerlite.feature.artist

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArtistDetailScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentState_shouldShowHeroAndHotSongsSection() {
        composeRule.setContent {
            PlayerLiteTheme {
                ArtistDetailScreen(
                    state = ArtistDetailUiState(
                        headerState = ArtistDetailHeaderUiState.Content(
                            ArtistDetailContent(
                                artistId = "6452",
                                name = "周杰伦",
                                aliases = listOf("Jay Chou"),
                                identities = listOf("作曲"),
                                avatarUrl = null,
                                coverUrl = "http://example.com/cover.jpg",
                                briefDesc = "简介",
                                musicCount = 568,
                                albumCount = 44
                            )
                        ),
                        hotSongsState = ArtistHotSongsUiState.Content(
                            listOf(
                                ArtistHotSongRow(
                                    trackId = "210049",
                                    title = "布拉格广场",
                                    artistText = "蔡依林 / 周杰伦",
                                    albumTitle = "看我72变",
                                    coverUrl = null,
                                    durationMs = 294600L
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("artist_detail_hero_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_detail_avatar").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_hot_songs_section").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_hot_song_210049").assertIsDisplayed()
    }

    @Test
    fun clickingHeroDescriptionPreview_shouldShowFullDescriptionSheet() {
        val longDescription = "周杰伦简介第一段。周杰伦简介第二段。周杰伦简介第三段。"

        composeRule.setContent {
            PlayerLiteTheme {
                ArtistDetailScreen(
                    state = ArtistDetailUiState(
                        headerState = ArtistDetailHeaderUiState.Content(
                            ArtistDetailContent(
                                artistId = "6452",
                                name = "周杰伦",
                                aliases = listOf("Jay Chou"),
                                identities = listOf("作曲"),
                                avatarUrl = null,
                                coverUrl = null,
                                briefDesc = longDescription,
                                musicCount = 568,
                                albumCount = 44
                            )
                        ),
                        hotSongsState = ArtistHotSongsUiState.Empty
                    ),
                    onBack = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("artist_description_preview").assertIsDisplayed().performClick()
        composeRule.onAllNodesWithTag("artist_description_card").assertCountEquals(0)

        composeRule.onNodeWithTag("artist_description_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_description_sheet_scroll").assertIsDisplayed()
        composeRule.onNodeWithText(longDescription).assertIsDisplayed()
    }

    @Test
    fun clickingAvatar_shouldShowPreviewDialog() {
        composeRule.setContent {
            PlayerLiteTheme {
                ArtistDetailScreen(
                    state = ArtistDetailUiState(
                        headerState = ArtistDetailHeaderUiState.Content(
                            ArtistDetailContent(
                                artistId = "6452",
                                name = "周杰伦",
                                aliases = emptyList(),
                                identities = emptyList(),
                                avatarUrl = "http://example.com/avatar.jpg",
                                coverUrl = null,
                                briefDesc = "简介",
                                musicCount = 568,
                                albumCount = 44
                            )
                        ),
                        hotSongsState = ArtistHotSongsUiState.Empty
                    ),
                    onBack = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("artist_detail_avatar").performClick()

        composeRule.onNodeWithTag("artist_avatar_preview_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_avatar_preview_image").assertIsDisplayed()
    }

    @Test
    fun hotSongsError_shouldKeepHeroAndShowSectionRetry() {
        composeRule.setContent {
            PlayerLiteTheme {
                ArtistDetailScreen(
                    state = ArtistDetailUiState(
                        headerState = ArtistDetailHeaderUiState.Content(
                            ArtistDetailContent(
                                artistId = "6452",
                                name = "周杰伦",
                                aliases = emptyList(),
                                identities = emptyList(),
                                avatarUrl = null,
                                coverUrl = null,
                                briefDesc = "简介",
                                musicCount = 568,
                                albumCount = 44
                            )
                        ),
                        hotSongsState = ArtistHotSongsUiState.Error("热门歌曲加载失败")
                    ),
                    onBack = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("artist_detail_hero_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_hot_songs_error").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
    }
}
