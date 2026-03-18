package com.wxy.playerlite.feature.artist

import com.wxy.playerlite.feature.player.runtime.DetailPlaybackGateway
import com.wxy.playerlite.feature.player.runtime.DetailPlaybackRequest
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
    fun init_shouldPublishHeaderHotSongsAndAlbums() = runTest {
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
                    albumCount = 44,
                    isFollowed = true,
                    videoCount = 27,
                    fansCount = 1558L
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
            ),
            albumResultsByOffset = mapOf(
                0 to Result.success(
                    ArtistAlbumPage(
                        items = listOf(
                            ArtistAlbumRow(
                                albumId = "274336916",
                                title = "即兴曲",
                                artistText = "周杰伦",
                                coverUrl = "http://example.com/album.jpg",
                                trackCount = 1,
                                type = "Single",
                                publishTimeText = "2025-06-06"
                            )
                        ),
                        hasMore = true
                    )
                )
            )
        )
        val playbackGateway = FakeArtistDetailPlaybackGateway()

        val viewModel = ArtistDetailViewModel(
            artistId = "6452",
            repository = repository,
            playbackGateway = playbackGateway
        )
        advanceUntilIdle()

        val state = viewModel.uiStateFlow.value
        assertTrue(state.headerState is ArtistDetailHeaderUiState.Content)
        assertTrue(state.encyclopediaState is ArtistEncyclopediaUiState.Content)
        assertTrue(state.hotSongsState is ArtistHotSongsUiState.Content)
        assertTrue(state.albumsState is ArtistAlbumsUiState.Content)
        assertEquals(
            "周杰伦",
            (state.headerState as ArtistDetailHeaderUiState.Content).content.name
        )
        assertEquals(
            true,
            (state.headerState as ArtistDetailHeaderUiState.Content).content.isFollowed
        )
        assertEquals(
            27,
            (state.headerState as ArtistDetailHeaderUiState.Content).content.videoCount
        )
        assertEquals(
            1558L,
            (state.headerState as ArtistDetailHeaderUiState.Content).content.fansCount
        )
        assertEquals(
            "人物简介",
            (state.encyclopediaState as ArtistEncyclopediaUiState.Content).content.sections.single().title
        )
        assertEquals(
            "布拉格广场",
            (state.hotSongsState as ArtistHotSongsUiState.Content).items.single().title
        )
        assertEquals(
            "即兴曲",
            (state.albumsState as ArtistAlbumsUiState.Content).items.single().title
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
        val playbackGateway = FakeArtistDetailPlaybackGateway()

        val viewModel = ArtistDetailViewModel(
            artistId = "6452",
            repository = repository,
            playbackGateway = playbackGateway
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

    @Test
    fun init_shouldKeepHeaderContentWhenEnhancementFieldsFallbackToDefaults() = runTest {
        val repository = FakeArtistDetailRepository(
            detailResult = Result.success(
                ArtistDetailContent(
                    artistId = "15396",
                    name = "田馥甄",
                    aliases = emptyList(),
                    identities = emptyList(),
                    avatarUrl = null,
                    coverUrl = null,
                    briefDesc = "简介",
                    encyclopediaSummary = "",
                    encyclopediaSections = emptyList(),
                    musicCount = 120,
                    albumCount = 14,
                    isFollowed = null,
                    videoCount = 0,
                    fansCount = 0L
                )
            ),
            encyclopediaResult = Result.success(
                ArtistEncyclopediaContent(
                    summary = "",
                    sections = emptyList()
                )
            ),
            hotSongsResult = Result.success(emptyList())
        )
        val playbackGateway = FakeArtistDetailPlaybackGateway()

        val viewModel = ArtistDetailViewModel(
            artistId = "15396",
            repository = repository,
            playbackGateway = playbackGateway
        )
        advanceUntilIdle()

        val headerState = viewModel.uiStateFlow.value.headerState
        assertTrue(headerState is ArtistDetailHeaderUiState.Content)
        assertEquals(
            "15396",
            (headerState as ArtistDetailHeaderUiState.Content).content.artistId
        )
        assertEquals(null, headerState.content.isFollowed)
        assertEquals(0, headerState.content.videoCount)
        assertEquals(0L, headerState.content.fansCount)
    }

    @Test
    fun playAllAndTrack_shouldUseCurrentHotSongListAndClickedIndex() = runTest {
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
                    encyclopediaSections = emptyList(),
                    musicCount = 568,
                    albumCount = 44
                )
            ),
            encyclopediaResult = Result.success(
                ArtistEncyclopediaContent(
                    summary = "周杰伦（Jay Chou），中国台湾流行乐男歌手。",
                    sections = emptyList()
                )
            ),
            hotSongsResult = Result.success(
                listOf(
                    ArtistHotSongRow(
                        trackId = "210049",
                        title = "布拉格广场",
                        artistText = "蔡依林 / 周杰伦",
                        albumTitle = "看我72变",
                        coverUrl = "http://example.com/song-1.jpg",
                        durationMs = 294600L
                    ),
                    ArtistHotSongRow(
                        trackId = "185809",
                        title = "夜曲",
                        artistText = "周杰伦",
                        albumTitle = "十一月的萧邦",
                        coverUrl = "http://example.com/song-2.jpg",
                        durationMs = 228000L
                    )
                )
            )
        )
        val playbackGateway = FakeArtistDetailPlaybackGateway(playResult = true)

        val viewModel = ArtistDetailViewModel(
            artistId = "6452",
            repository = repository,
            playbackGateway = playbackGateway
        )
        advanceUntilIdle()

        viewModel.playAll()
        viewModel.playTrack(1)

        assertEquals(listOf(0, 1), playbackGateway.requests.map { it.activeIndex })
        assertEquals(2, playbackGateway.requests.first().items.size)
        assertEquals("artist", playbackGateway.requests.first().items.first().contextType)
        assertEquals("6452", playbackGateway.requests.first().items.first().contextId)
        assertEquals("周杰伦", playbackGateway.requests.first().items.first().contextTitle)
        assertEquals("6452", playbackGateway.requests.first().items.first().primaryArtistId)
        assertEquals("6452", playbackGateway.requests.last().items[1].primaryArtistId)
    }

    @Test
    fun loadMoreAlbums_shouldKeepExistingItemsWhenNextPageFails() = runTest {
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
            encyclopediaResult = Result.success(
                ArtistEncyclopediaContent(
                    summary = "",
                    sections = emptyList()
                )
            ),
            hotSongsResult = Result.success(emptyList()),
            albumResultsByOffset = mapOf(
                0 to Result.success(
                    ArtistAlbumPage(
                        items = listOf(
                            ArtistAlbumRow(
                                albumId = "274336916",
                                title = "即兴曲",
                                artistText = "周杰伦",
                                coverUrl = "http://example.com/album-1.jpg",
                                trackCount = 1,
                                type = "Single",
                                publishTimeText = "2025-06-06"
                            )
                        ),
                        hasMore = true
                    )
                ),
                DEFAULT_ARTIST_ALBUM_PAGE_SIZE to Result.failure(
                    IllegalStateException("专辑列表加载失败")
                )
            )
        )
        val playbackGateway = FakeArtistDetailPlaybackGateway()

        val viewModel = ArtistDetailViewModel(
            artistId = "6452",
            repository = repository,
            playbackGateway = playbackGateway
        )
        advanceUntilIdle()

        viewModel.loadMoreAlbums()
        advanceUntilIdle()

        val albumsState = viewModel.uiStateFlow.value.albumsState as ArtistAlbumsUiState.Content
        assertEquals(1, albumsState.items.size)
        assertEquals("即兴曲", albumsState.items.single().title)
        assertEquals("专辑列表加载失败", albumsState.loadMoreErrorMessage)
    }
}

private class FakeArtistDetailRepository(
    private val detailResult: Result<ArtistDetailContent>,
    private val encyclopediaResult: Result<ArtistEncyclopediaContent>,
    private val hotSongsResult: Result<List<ArtistHotSongRow>>,
    private val albumResultsByOffset: Map<Int, Result<ArtistAlbumPage>> = mapOf(
        0 to Result.success(
            ArtistAlbumPage(
                items = emptyList(),
                hasMore = false
            )
        )
    )
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

    override suspend fun fetchArtistAlbums(
        artistId: String,
        offset: Int,
        limit: Int
    ): ArtistAlbumPage {
        return albumResultsByOffset.getValue(offset).getOrThrow()
    }
}

private class FakeArtistDetailPlaybackGateway(
    private val playResult: Boolean = true
) : DetailPlaybackGateway {
    val requests = mutableListOf<DetailPlaybackRequest>()

    override fun play(request: DetailPlaybackRequest): Boolean {
        requests += request
        return playResult
    }
}
