package com.wxy.playerlite.feature.song

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import com.wxy.playerlite.feature.song.SongDetailContentState.Content
import com.wxy.playerlite.feature.song.SongDetailSource.ONLINE
import com.wxy.playerlite.ui.theme.PlayerLiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SongDetailScreenRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onlineSong_shouldRenderInformationHeaderAndWikiFactsSection() {
        composeRule.setContent {
            PlayerLiteTheme {
                SongDetailScreen(
                    state = SongDetailUiState(
                        contentState = Content(
                            SongDetailContent(
                                ref = SongRef.Online(songId = "1973665667"),
                                source = ONLINE,
                                recentRecordKey = "online:1973665667",
                                title = "夜曲",
                                artistText = "周杰伦",
                                primaryArtistId = "artist-1",
                                albumTitle = "十一月的肖邦",
                                albumId = "album-1",
                                coverUrl = "https://example.com/cover.jpg",
                                durationMs = 213000L,
                                playlistItem = PlaylistItem(
                                    id = "song-detail:1973665667",
                                    songId = "1973665667",
                                    displayName = "夜曲",
                                    title = "夜曲",
                                    artistText = "周杰伦",
                                    albumTitle = "十一月的肖邦",
                                    durationMs = 213000L,
                                    itemType = PlaylistItemType.ONLINE
                                ),
                                wiki = SongDetailWikiUi(
                                    title = "歌曲简要百科",
                                    contributionText = "参与共建",
                                    sections = listOf(
                                        SongDetailWikiSectionUi(
                                            title = "曲风",
                                            values = listOf("流行", "华语流行")
                                        )
                                    ),
                                    similarSongs = listOf(
                                        SongDetailRelatedSongUi(
                                            songId = "song-1",
                                            title = "夜的第七章",
                                            subtitle = "周杰伦",
                                            coverUrl = "https://example.com/song-1.jpg"
                                        )
                                    ),
                                    relatedPlaylists = listOf(
                                        SongDetailRelatedPlaylistUi(
                                            playlistId = "playlist-1",
                                            title = "深夜循环",
                                            subtitle = "12.3 万播放",
                                            coverUrl = "https://example.com/playlist-1.jpg"
                                        )
                                    )
                                ),
                                canFavorite = true
                            )
                        )
                    ),
                    onBack = {},
                    onRetry = {},
                    onPlayClick = {},
                    onPlayNextClick = {},
                    onOpenLandscapeClick = {},
                    onOpenArtistClick = {},
                    onOpenAlbumClick = {},
                    onRemoveFromRecentClick = {},
                    onOpenSongClick = {},
                    onOpenPlaylistClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("song_detail_root").assertIsDisplayed()
        composeRule.onNodeWithTag("song_detail_hero_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("song_detail_info_header").assertIsDisplayed()
        composeRule.onNodeWithTag("song_detail_info_cover").assertIsDisplayed()
        composeRule.onNodeWithTag("song_detail_primary_action").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("song_detail_action_list"))
        composeRule.onNodeWithTag("song_detail_action_list").assertIsDisplayed()
        composeRule.onNodeWithTag("song_detail_secondary_action").assertIsDisplayed()
        composeRule.onNodeWithTag("song_detail_landscape_action").assertIsDisplayed()
        composeRule.onNodeWithTag("song_detail_open_artist").assertIsDisplayed()
        composeRule.onNodeWithTag("song_detail_open_album").assertIsDisplayed()
        composeRule.onNodeWithTag("song_detail_remove_from_recent").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("song_detail_similar_songs_section"))
        composeRule.onNodeWithTag("song_detail_similar_songs_section").assertIsDisplayed()
        composeRule.onNodeWithText("相似歌曲").assertIsDisplayed()
        composeRule.onNodeWithText("夜的第七章").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_scaffold_list")
            .performScrollToNode(hasTestTag("song_detail_related_playlists_section"))
        composeRule.onNodeWithTag("song_detail_related_playlists_section").assertIsDisplayed()
        composeRule.onNodeWithText("相关歌单").assertIsDisplayed()
        composeRule.onNodeWithText("深夜循环").assertIsDisplayed()
    }

    @Test
    fun nonRecentSong_shouldHideRemoveFromRecentAction() {
        composeRule.setContent {
            PlayerLiteTheme {
                SongDetailScreen(
                    state = SongDetailUiState(
                        contentState = Content(
                            SongDetailContent(
                                ref = SongRef.Online(songId = "1973665667"),
                                source = ONLINE,
                                title = "夜曲",
                                artistText = "周杰伦",
                                playlistItem = PlaylistItem(
                                    id = "song-detail:1973665667",
                                    songId = "1973665667",
                                    displayName = "夜曲",
                                    title = "夜曲",
                                    artistText = "周杰伦",
                                    durationMs = 213000L,
                                    itemType = PlaylistItemType.ONLINE
                                )
                            )
                        )
                    ),
                    onBack = {},
                    onRetry = {},
                    onPlayClick = {},
                    onPlayNextClick = {},
                    onOpenLandscapeClick = {},
                    onOpenArtistClick = {},
                    onOpenAlbumClick = {},
                    onRemoveFromRecentClick = {},
                    onOpenSongClick = {},
                    onOpenPlaylistClick = {}
                )
            }
        }

        composeRule.onAllNodesWithTag("song_detail_remove_from_recent").assertCountEquals(0)
    }
}
