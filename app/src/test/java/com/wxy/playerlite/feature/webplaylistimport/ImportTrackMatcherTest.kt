package com.wxy.playerlite.feature.webplaylistimport

import com.wxy.playerlite.feature.search.SearchRepository
import com.wxy.playerlite.feature.search.SearchResultType
import com.wxy.playerlite.feature.search.SearchResultUiModel
import com.wxy.playerlite.feature.search.SearchRouteTarget
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportTrackMatcherTest {
    @Test
    fun buildQueries_shouldUseTitleAndPrimaryArtistThenAlbumFallback() {
        val builder = ImportTrackQueryBuilder()

        val queries = builder.build(
            ImportedPlaylistTrack(
                sourceTrackId = "1",
                title = "Kiss Me",
                artistNames = listOf("Mike Angelo (吴翊歌)", "Aom Sushar"),
                albumTitle = "Kiss Me O.S.T"
            )
        )

        assertEquals(
            listOf(
                "Kiss Me Mike Angelo (吴翊歌)",
                "Kiss Me Mike Angelo (吴翊歌) Kiss Me O.S.T"
            ),
            queries
        )
    }

    @Test
    fun match_shouldReturnMatchedForExactTitleArtistAndCloseDuration() = runBlocking {
        val matcher = ImportTrackMatcher(
            searchRepository = FakeMatcherSearchRepository(
                songResultsByKeyword = mapOf(
                    "พยายาม O-Pavee" to listOf(
                        songResult(
                            id = "35331240",
                            title = "พยายาม",
                            artistText = "O-Pavee",
                            albumTitle = "พยายาม",
                            durationMs = 258_409L
                        )
                    )
                )
            )
        )

        val resolution = matcher.match(
            ImportedPlaylistTrack(
                sourceTrackId = "105570781",
                title = "พยายาม",
                artistNames = listOf("O-Pavee"),
                albumTitle = "พยายาม",
                durationMs = 258_000L
            )
        )

        assertTrue(resolution is ImportedTrackResolution.Matched)
        val matched = resolution as ImportedTrackResolution.Matched
        assertEquals("35331240", matched.song.songId)
        assertEquals("พยายาม", matched.song.title)
    }

    @Test
    fun match_shouldReturnAmbiguousWhenTopCandidatesAreTooClose() = runBlocking {
        val matcher = ImportTrackMatcher(
            searchRepository = FakeMatcherSearchRepository(
                songResultsByKeyword = mapOf(
                    "Hello Adele" to listOf(
                        songResult(
                            id = "1",
                            title = "Hello",
                            artistText = "Adele",
                            albumTitle = "25",
                            durationMs = 300_000L
                        ),
                        songResult(
                            id = "2",
                            title = "Hello",
                            artistText = "Adele",
                            albumTitle = "Single",
                            durationMs = 300_200L
                        )
                    )
                )
            )
        )

        val resolution = matcher.match(
            ImportedPlaylistTrack(
                sourceTrackId = "99",
                title = "Hello",
                artistNames = listOf("Adele"),
                durationMs = 300_000L
            )
        )

        assertTrue(resolution is ImportedTrackResolution.Ambiguous)
        val ambiguous = resolution as ImportedTrackResolution.Ambiguous
        assertEquals(listOf("1", "2"), ambiguous.candidates.map { it.songId })
    }

    @Test
    fun match_shouldReturnUnmatchedWhenDurationIsTooFarOff() = runBlocking {
        val matcher = ImportTrackMatcher(
            searchRepository = FakeMatcherSearchRepository(
                songResultsByKeyword = mapOf(
                    "Kiss Me Mike Angelo (吴翊歌)" to listOf(
                        songResult(
                            id = "35497866",
                            title = "Kiss Me",
                            artistText = "Mike Angelo / Aomiz",
                            albumTitle = "Kiss Me OST",
                            durationMs = 221_335L
                        )
                    ),
                    "Kiss Me Mike Angelo (吴翊歌) Kiss Me O.S.T" to listOf(
                        songResult(
                            id = "35497866",
                            title = "Kiss Me",
                            artistText = "Mike Angelo / Aomiz",
                            albumTitle = "Kiss Me OST",
                            durationMs = 221_335L
                        )
                    )
                )
            )
        )

        val resolution = matcher.match(
            ImportedPlaylistTrack(
                sourceTrackId = "104836359",
                title = "Kiss Me",
                artistNames = listOf("Mike Angelo (吴翊歌)", "Aom Sushar"),
                albumTitle = "Kiss Me O.S.T",
                durationMs = 191_000L
            )
        )

        assertEquals(ImportedTrackResolution.Unmatched, resolution)
    }

    @Test
    fun match_shouldReuseSessionCacheAcrossRepeatedQueries() = runBlocking {
        val searchRepository = FakeMatcherSearchRepository(
            songResultsByKeyword = mapOf(
                "พยายาม O-Pavee" to listOf(
                    songResult(
                        id = "35331240",
                        title = "พยายาม",
                        artistText = "O-Pavee",
                        albumTitle = "พยายาม",
                        durationMs = 258_409L
                    )
                )
            )
        )
        val matcher = ImportTrackMatcher(searchRepository = searchRepository)
        val track = ImportedPlaylistTrack(
            sourceTrackId = "105570781",
            title = "พยายาม",
            artistNames = listOf("O-Pavee"),
            albumTitle = "พยายาม",
            durationMs = 258_000L
        )

        matcher.match(track)
        matcher.match(track)

        assertEquals(1, searchRepository.requestCounts["พยายาม O-Pavee"])
    }

    @Test
    fun matchProgressively_shouldEmitIncrementalUpdates() = runBlocking {
        val matcher = ImportTrackMatcher(
            searchRepository = FakeMatcherSearchRepository(
                songResultsByKeyword = mapOf(
                    "พยายาม O-Pavee" to listOf(
                        songResult(
                            id = "35331240",
                            title = "พยายาม",
                            artistText = "O-Pavee",
                            albumTitle = "พยายาม",
                            durationMs = 258_409L
                        )
                    )
                )
            )
        )

        val updates = matcher.matchProgressively(
            listOf(
                ImportedPlaylistTrack(
                    sourceTrackId = "105570781",
                    title = "พยายาม",
                    artistNames = listOf("O-Pavee"),
                    albumTitle = "พยายาม",
                    durationMs = 258_000L,
                    resolution = ImportedTrackResolution.Pending
                )
            )
        ).toList()

        assertEquals(1, updates.size)
        assertEquals("1 / 1", updates.single().progress.progressText)
        assertTrue(updates.single().tracks.single().resolution is ImportedTrackResolution.Matched)
    }

    @Test
    fun matchProgressively_shouldLimitConcurrentSearchRequests() = runBlocking {
        val searchRepository = FakeMatcherSearchRepository(
            songResultsByKeyword = mapOf(
                "Song 1 Artist 1" to listOf(songResult("1", "Song 1", "Artist 1", "Album", 180_000L)),
                "Song 2 Artist 2" to listOf(songResult("2", "Song 2", "Artist 2", "Album", 180_000L)),
                "Song 3 Artist 3" to listOf(songResult("3", "Song 3", "Artist 3", "Album", 180_000L))
            ),
            requestDelayMs = 50L
        )
        val matcher = ImportTrackMatcher(
            searchRepository = searchRepository,
            maxConcurrentSearches = 2
        )

        matcher.matchProgressively(
            listOf(
                pendingTrack("1", "Song 1", "Artist 1"),
                pendingTrack("2", "Song 2", "Artist 2"),
                pendingTrack("3", "Song 3", "Artist 3")
            )
        ).toList()

        assertTrue(searchRepository.maxConcurrentRequests.get() <= 2)
    }

    @Test
    fun matchProgressively_shouldPauseWhenSearchFailsRepeatedly() = runBlocking {
        val matcher = ImportTrackMatcher(
            searchRepository = FakeMatcherSearchRepository(
                failuresByKeyword = mapOf(
                    "Song 1 Artist 1" to IllegalStateException("429 Too Many Requests"),
                    "Song 2 Artist 2" to IllegalStateException("429 Too Many Requests")
                )
            ),
            maxConcurrentSearches = 1,
            maxConsecutiveFailures = 2
        )

        val updates = matcher.matchProgressively(
            listOf(
                pendingTrack("1", "Song 1", "Artist 1"),
                pendingTrack("2", "Song 2", "Artist 2")
            )
        ).toList()

        val last = updates.last()
        assertTrue(last.progress.isPaused)
        assertEquals("0 / 2", last.progress.progressText)
        assertEquals(
            "匹配请求过于频繁，已暂停后续匹配",
            last.progress.pauseMessage
        )
        assertTrue(last.tracks.all { it.resolution == ImportedTrackResolution.Pending })
    }
}

private class FakeMatcherSearchRepository(
    private val songResultsByKeyword: Map<String, List<SearchResultUiModel.Song>> = emptyMap(),
    private val failuresByKeyword: Map<String, Throwable> = emptyMap(),
    private val requestDelayMs: Long = 0L
) : SearchRepository {
    val requestCounts = linkedMapOf<String, Int>()
    val maxConcurrentRequests = AtomicInteger(0)
    private val activeRequests = AtomicInteger(0)

    override suspend fun fetchHotKeywords() = emptyList<com.wxy.playerlite.feature.search.SearchHotKeywordUiModel>()

    override suspend fun fetchSuggestions(keyword: String) =
        emptyList<com.wxy.playerlite.feature.search.SearchSuggestionUiModel>()

    override suspend fun search(
        keyword: String,
        type: SearchResultType
    ): List<SearchResultUiModel> {
        requestCounts[keyword] = (requestCounts[keyword] ?: 0) + 1
        require(type == SearchResultType.SONG)
        val active = activeRequests.incrementAndGet()
        maxConcurrentRequests.updateAndGet { current -> maxOf(current, active) }
        if (requestDelayMs > 0L) {
            delay(requestDelayMs)
        }
        failuresByKeyword[keyword]?.let { failure ->
            activeRequests.decrementAndGet()
            throw failure
        }
        activeRequests.decrementAndGet()
        return songResultsByKeyword[keyword].orEmpty()
    }

    override fun readSearchHistory(): List<String> = emptyList()

    override suspend fun recordSearchHistory(keyword: String) = Unit

    override suspend fun removeSearchHistory(keyword: String) = Unit

    override suspend fun clearSearchHistory() = Unit
}

private fun songResult(
    id: String,
    title: String,
    artistText: String,
    albumTitle: String,
    durationMs: Long
): SearchResultUiModel.Song {
    return SearchResultUiModel.Song(
        id = id,
        title = title,
        artistText = artistText,
        albumTitle = albumTitle,
        coverUrl = null,
        routeTarget = SearchRouteTarget.Song(songId = id),
        durationMs = durationMs
    )
}

private fun pendingTrack(
    sourceTrackId: String,
    title: String,
    artist: String
): ImportedPlaylistTrack {
    return ImportedPlaylistTrack(
        sourceTrackId = sourceTrackId,
        title = title,
        artistNames = listOf(artist),
        albumTitle = "Album",
        durationMs = 180_000L,
        resolution = ImportedTrackResolution.Pending
    )
}
