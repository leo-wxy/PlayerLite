package com.wxy.playerlite.feature.artist

import com.wxy.playerlite.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_shouldPublishHeaderAndHotSongs() = runTest {
        val repository = FakeArtistDetailRepository(
            detailResult = Result.success(
                ArtistDetailContent(
                    artistId = "6452",
                    name = "周杰伦",
                    aliases = listOf("Jay Chou"),
                    identities = listOf("作曲"),
                    avatarUrl = "http://example.com/avatar.jpg",
                    coverUrl = "http://example.com/cover.jpg",
                    briefDesc = "简介",
                    encyclopediaSummary = "周杰伦，中国台湾流行乐男歌手。",
                    encyclopediaSections = listOf(
                        ArtistEncyclopediaSection(
                            title = "人物简介",
                            body = "周杰伦是华语流行音乐代表人物。"
                        )
                    ),
                    musicCount = 568,
                    albumCount = 44
                )
            ),
            encyclopediaResult = Result.success(
                ArtistEncyclopediaContent(
                    summary = "周杰伦（Jay Chou），中国台湾流行乐男歌手。",
                    sections = listOf(
                        ArtistEncyclopediaSection(
                            title = "人物简介",
                            body = "周杰伦是华语流行音乐代表人物。"
                        )
                    )
                )
            ),
            hotSongsResult = Result.success(
                listOf(
                    ArtistHotSongRow(
                        trackId = "210049",
                        title = "布拉格广场",
                        artistText = "蔡依林 / 周杰伦",
                        albumTitle = "看我72变",
                        coverUrl = "http://example.com/song.jpg",
                        durationMs = 294600L
                    )
                )
            )
        )

        val viewModel = ArtistDetailViewModel(
            artistId = "6452",
            repository = repository
        )
        advanceUntilIdle()

        val state = viewModel.uiStateFlow.value
        assertTrue(state.headerState is ArtistDetailHeaderUiState.Content)
        assertTrue(state.encyclopediaState is ArtistEncyclopediaUiState.Content)
        assertTrue(state.hotSongsState is ArtistHotSongsUiState.Content)
        assertEquals(
            "周杰伦",
            (state.headerState as ArtistDetailHeaderUiState.Content).content.name
        )
        assertEquals(
            "人物简介",
            (state.encyclopediaState as ArtistEncyclopediaUiState.Content).content.sections.single().title
        )
        assertEquals(
            "布拉格广场",
            (state.hotSongsState as ArtistHotSongsUiState.Content).items.single().title
        )
    }

    @Test
    fun retry_shouldKeepHeaderWhenHotSongsFail() = runTest {
        val repository = FakeArtistDetailRepository(
            detailResult = Result.success(
                ArtistDetailContent(
                    artistId = "6452",
                    name = "周杰伦",
                    aliases = emptyList(),
                    identities = emptyList(),
                    avatarUrl = null,
                    coverUrl = null,
                    briefDesc = "简介",
                    encyclopediaSummary = "",
                    encyclopediaSections = emptyList(),
                    musicCount = 568,
                    albumCount = 44
                )
            ),
            encyclopediaResult = Result.failure(IllegalStateException("歌手百科加载失败")),
            hotSongsResult = Result.failure(IllegalStateException("热门歌曲加载失败"))
        )

        val viewModel = ArtistDetailViewModel(
            artistId = "6452",
            repository = repository
        )
        advanceUntilIdle()

        val state = viewModel.uiStateFlow.value
        assertTrue(state.headerState is ArtistDetailHeaderUiState.Content)
        assertEquals(
            "歌手百科加载失败",
            (state.encyclopediaState as ArtistEncyclopediaUiState.Error).message
        )
        assertEquals(
            "热门歌曲加载失败",
            (state.hotSongsState as ArtistHotSongsUiState.Error).message
        )
    }
}

private class FakeArtistDetailRepository(
    private val detailResult: Result<ArtistDetailContent>,
    private val encyclopediaResult: Result<ArtistEncyclopediaContent>,
    private val hotSongsResult: Result<List<ArtistHotSongRow>>
) : ArtistDetailRepository {
    override suspend fun fetchArtistDetail(artistId: String): ArtistDetailContent {
        return detailResult.getOrThrow()
    }

    override suspend fun fetchArtistEncyclopedia(artistId: String): ArtistEncyclopediaContent {
        return encyclopediaResult.getOrThrow()
    }

    override suspend fun fetchArtistHotSongs(artistId: String): List<ArtistHotSongRow> {
        return hotSongsResult.getOrThrow()
    }
}
