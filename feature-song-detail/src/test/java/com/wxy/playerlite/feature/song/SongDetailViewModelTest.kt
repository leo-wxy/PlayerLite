package com.wxy.playerlite.feature.song

import androidx.lifecycle.ViewModel
import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.core.playlist.PlaylistItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SongDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_withOnlineSong_shouldLoadContent() = runTest(dispatcher) {
        val viewModel = SongDetailViewModel(
            ref = SongRef.Online(songId = "123"),
            repository = FakeSongDetailFeatureRepository(
                detail = demoOnlineContent()
            ),
            actionGateway = FakeSongDetailActionGateway()
        )

        advanceUntilIdle()

        val state = viewModel.uiStateFlow.value
        val content = (state.contentState as SongDetailContentState.Content).content
        assertEquals("夜曲", content.title)
        assertEquals("123", (content.ref as SongRef.Online).songId)
        assertTrue(content.canFavorite)
    }

    @Test
    fun share_withOnlineSong_shouldEmitShareEventWithSongUrl() = runTest(dispatcher) {
        val viewModel = SongDetailViewModel(
            ref = SongRef.Online(songId = "123"),
            repository = FakeSongDetailFeatureRepository(
                detail = demoOnlineContent()
            ),
            actionGateway = FakeSongDetailActionGateway()
        )
        advanceUntilIdle()

        val eventDeferred = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.first()
        }
        viewModel.share()
        advanceUntilIdle()

        assertEquals(
            SongDetailEvent.Share("夜曲 - 周杰伦\nhttps://music.163.com/#/song?id=123"),
            eventDeferred.await()
        )
    }

    @Test
    fun favorite_withLocalSong_shouldEmitUnsupportedMessage() = runTest(dispatcher) {
        val localRef = SongRef.Local(
            playbackUri = "content://local/1",
            title = "七里香",
            artistText = "周杰伦",
            albumTitle = "七里香",
            durationMs = 269000L
        )
        val viewModel = SongDetailViewModel(
            ref = localRef,
            repository = FakeSongDetailFeatureRepository(
                detail = demoLocalContent(localRef)
            ),
            actionGateway = FakeSongDetailActionGateway()
        )
        advanceUntilIdle()

        val eventDeferred = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.first()
        }
        viewModel.favorite()
        advanceUntilIdle()

        assertEquals(
            SongDetailEvent.ShowMessage("当前歌曲不支持收藏"),
            eventDeferred.await()
        )
    }

    private class FakeSongDetailFeatureRepository(
        private val detail: SongDetailContent
    ) : SongDetailFeatureRepository {
        override suspend fun loadSongDetail(ref: SongRef): SongDetailContent = detail

        override suspend fun favoriteSong(songId: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeSongDetailActionGateway : SongDetailActionGateway {
        override fun play(item: PlaylistItem): Boolean = true

        override fun playNext(item: PlaylistItem): Boolean = true
    }
}

private fun demoOnlineContent(): SongDetailContent {
    return SongDetailContent(
        ref = SongRef.Online(songId = "123"),
        source = SongDetailSource.ONLINE,
        title = "夜曲",
        artistText = "周杰伦",
        primaryArtistId = "artist-1",
        albumTitle = "十一月的肖邦",
        albumId = "album-1",
        coverUrl = "https://example.com/cover.jpg",
        durationMs = 213000L,
        playlistItem = demoPlaylistItem(songId = "123"),
        canFavorite = true
    )
}

private fun demoLocalContent(ref: SongRef.Local): SongDetailContent {
    return SongDetailContent(
        ref = ref,
        source = SongDetailSource.LOCAL,
        title = ref.title,
        artistText = ref.artistText,
        albumTitle = ref.albumTitle,
        durationMs = ref.durationMs,
        playlistItem = demoPlaylistItem(songId = null, playbackUri = ref.playbackUri)
    )
}

private fun demoPlaylistItem(
    songId: String?,
    playbackUri: String = ""
): PlaylistItem {
    return PlaylistItem(
        id = "playlist-item-${songId ?: "local"}",
        songId = songId,
        uri = playbackUri,
        displayName = "夜曲",
        title = "夜曲",
        artistText = "周杰伦",
        albumTitle = "十一月的肖邦",
        durationMs = 213000L,
        itemType = if (songId == null) PlaylistItemType.LOCAL else PlaylistItemType.ONLINE
    )
}
