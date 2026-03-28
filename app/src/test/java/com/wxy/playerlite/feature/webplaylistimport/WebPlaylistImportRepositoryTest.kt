package com.wxy.playerlite.feature.webplaylistimport

import com.wxy.playerlite.feature.playlist.PlaylistDetailRepository
import com.wxy.playerlite.feature.playlist.PlaylistDynamicInfo
import com.wxy.playerlite.feature.playlist.PlaylistHeaderContent
import com.wxy.playerlite.feature.playlist.PlaylistTrackRow
import com.wxy.playerlite.feature.search.SearchRepository
import com.wxy.playerlite.feature.search.SearchResultType
import com.wxy.playerlite.feature.search.SearchResultUiModel
import com.wxy.playerlite.feature.search.SearchRouteTarget
import kotlin.math.min
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPlaylistImportRepositoryTest {
    @Test
    fun fetchPlaylistSnapshot_shouldReuseNeteasePlaylistDetailAndMarkTracksDirect() = runBlocking {
        val neteaseTracks = List(31) { index ->
            PlaylistTrackRow(
                trackId = (1000 + index).toString(),
                title = "Song $index",
                artistText = "Artist $index",
                primaryArtistId = (2000 + index).toString(),
                albumTitle = "Album $index",
                coverUrl = "http://example.com/$index.jpg",
                durationMs = 180_000L + index
            )
        }
        val playlistDetailRepository = FakePlaylistDetailRepository(
            header = PlaylistHeaderContent(
                playlistId = "17729789137",
                title = "深夜 R&B",
                creatorName = "Buradarrr",
                description = "夜间歌单",
                coverUrl = "http://example.com/cover.jpg",
                trackCount = neteaseTracks.size,
                playCount = 0L,
                subscribedCount = 0L
            ),
            tracks = neteaseTracks
        )
        val repository = DefaultWebPlaylistImportRepository(
            urlParser = WebPlaylistImportUrlParser(),
            playlistDetailRepository = playlistDetailRepository,
            qqMusicRemoteDataSource = FakeQqMusicPlaylistRemoteDataSource(
                payload = jsonObject("""{"code":0,"cdlist":[]}""")
            ),
            searchRepository = FakeImportSearchRepository()
        )

        val snapshot = repository.fetchPlaylistSnapshot(
            "https://music.163.com/#/playlist?id=17729789137"
        )

        assertEquals(ImportedPlaylistSource.NETEASE, snapshot.source)
        assertEquals("17729789137", snapshot.playlistId)
        assertEquals("深夜 R&B", snapshot.title)
        assertEquals("Buradarrr", snapshot.creatorName)
        assertEquals(31, snapshot.tracks.size)
        assertEquals(listOf(0, 30), playlistDetailRepository.requestedOffsets)
        assertEquals(31, snapshot.summary.directCount)
        assertEquals(0, snapshot.summary.unmatchedCount)
        assertTrue(snapshot.tracks.all { it.resolution is ImportedTrackResolution.Direct })
        assertEquals("1000", snapshot.tracks.first().sourceTrackId)
        assertEquals("Song 0", snapshot.tracks.first().title)
        assertEquals("Artist 0", snapshot.tracks.first().artistText)
        assertEquals("1000", snapshot.tracks.first().resolvedSongId)
    }

    @Test
    fun fetchPlaylistSnapshot_shouldFetchQqPlaylistSnapshotAndLeaveTracksUnmatched() = runBlocking {
        val repository = DefaultWebPlaylistImportRepository(
            urlParser = WebPlaylistImportUrlParser(),
            playlistDetailRepository = FakePlaylistDetailRepository(
                header = PlaylistHeaderContent(
                    playlistId = "17729789137",
                    title = "unused",
                    creatorName = "unused",
                    description = "",
                    coverUrl = null,
                    trackCount = 0,
                    playCount = 0L,
                    subscribedCount = 0L
                ),
                tracks = emptyList()
            ),
            qqMusicRemoteDataSource = FakeQqMusicPlaylistRemoteDataSource(
                payload = jsonObject(
                    """
                    {
                      "code": 0,
                      "cdlist": [
                        {
                          "disstid": "7217720898",
                          "dissname": "晨跑活力站",
                          "nickname": "QQ音乐官方歌单",
                          "desc": "用晨跑打开一天",
                          "logo": "http://qpic.y.qq.com/cover.jpg",
                          "songnum": 2,
                          "songlist": [
                            {
                              "id": 1115500,
                              "title": "Clap Clap!! (Live)",
                              "interval": 210,
                              "singer": [
                                { "name": "西野加奈" }
                              ],
                              "album": {
                                "name": "Kanayan Tour 2011～Summer～",
                                "mid": "0049gd9u47sHnJ"
                              }
                            },
                            {
                              "id": 235905764,
                              "title": "Rooting For You",
                              "interval": 176,
                              "singer": [
                                { "name": "Alessia Cara" }
                              ],
                              "album": {
                                "name": "Rooting For You",
                                "mid": "001G2iyB2EYlry"
                              }
                            }
                          ]
                        }
                      ]
                    }
                    """
                )
            ),
            searchRepository = FakeImportSearchRepository()
        )

        val snapshot = repository.fetchPlaylistSnapshot(
            "https://i.y.qq.com/n2/m/share/details/taoge.html?platform=11&id=7217720898"
        )

        assertEquals(ImportedPlaylistSource.QQ_MUSIC, snapshot.source)
        assertEquals("7217720898", snapshot.playlistId)
        assertEquals("晨跑活力站", snapshot.title)
        assertEquals("QQ音乐官方歌单", snapshot.creatorName)
        assertEquals("用晨跑打开一天", snapshot.description)
        assertEquals("http://qpic.y.qq.com/cover.jpg", snapshot.coverUrl)
        assertEquals(2, snapshot.tracks.size)
        assertEquals(0, snapshot.summary.directCount)
        assertEquals(2, snapshot.summary.unmatchedCount)
        val firstTrack = snapshot.tracks.first()
        assertEquals("1115500", firstTrack.sourceTrackId)
        assertEquals("Clap Clap!! (Live)", firstTrack.title)
        assertEquals("西野加奈", firstTrack.artistText)
        assertEquals("Kanayan Tour 2011～Summer～", firstTrack.albumTitle)
        assertEquals(210_000L, firstTrack.durationMs)
        assertEquals(
            "https://y.gtimg.cn/music/photo_new/T002R500x500M0000049gd9u47sHnJ.jpg",
            firstTrack.coverUrl
        )
        assertTrue(firstTrack.resolution is ImportedTrackResolution.Unmatched)
    }

    @Test
    fun fetchPlaylistSnapshot_shouldAcceptQqMusicRyqqPlaylistPageUrl() = runBlocking {
        val repository = DefaultWebPlaylistImportRepository(
            urlParser = WebPlaylistImportUrlParser(),
            playlistDetailRepository = FakePlaylistDetailRepository(
                header = PlaylistHeaderContent(
                    playlistId = "17729789137",
                    title = "unused",
                    creatorName = "unused",
                    description = "",
                    coverUrl = null,
                    trackCount = 0,
                    playCount = 0L,
                    subscribedCount = 0L
                ),
                tracks = emptyList()
            ),
            qqMusicRemoteDataSource = FakeQqMusicPlaylistRemoteDataSource(
                payload = jsonObject(
                    """
                    {
                      "code": 0,
                      "cdlist": [
                        {
                          "disstid": "4204621746",
                          "dissname": "泰式浪漫：你想要的甜蜜幻想",
                          "nickname": "二哈喇雪迪苗",
                          "desc": "真实页面链接回归样本",
                          "logo": "http://qpic.y.qq.com/cover-real.jpg",
                          "songnum": 1,
                          "songlist": [
                            {
                              "id": 123456,
                              "title": "ตัวอย่างเพลง",
                              "interval": 205,
                              "singer": [
                                { "name": "Example Singer" }
                              ],
                              "album": {
                                "title": "Example Album",
                                "mid": "001G2iyB2EYlry"
                              }
                            }
                          ]
                        }
                      ]
                    }
                    """
                )
            ),
            searchRepository = FakeImportSearchRepository()
        )

        val snapshot = repository.fetchPlaylistSnapshot(
            "https://y.qq.com/n/ryqq_v2/playlist/4204621746"
        )

        assertEquals(ImportedPlaylistSource.QQ_MUSIC, snapshot.source)
        assertEquals("4204621746", snapshot.playlistId)
        assertEquals("泰式浪漫：你想要的甜蜜幻想", snapshot.title)
        assertEquals("二哈喇雪迪苗", snapshot.creatorName)
        assertEquals(1, snapshot.tracks.size)
        assertEquals(1, snapshot.summary.unmatchedCount)
        val firstTrack = snapshot.tracks.first()
        assertEquals("123456", firstTrack.sourceTrackId)
        assertEquals("ตัวอย่างเพลง", firstTrack.title)
        assertEquals("Example Singer", firstTrack.artistText)
        assertEquals("Example Album", firstTrack.albumTitle)
        assertEquals(205_000L, firstTrack.durationMs)
    }

    @Test
    fun fetchPlaylistSnapshot_shouldMatchQqTracksAndReuseDuplicateQueries() = runBlocking {
        val searchRepository = FakeImportSearchRepository(
            songResultsByKeyword = mapOf(
                "พยายาม O-Pavee" to listOf(
                    SearchResultUiModel.Song(
                        id = "35331240",
                        title = "พยายาม",
                        artistText = "O-Pavee",
                        albumTitle = "พยายาม",
                        coverUrl = null,
                        routeTarget = SearchRouteTarget.Song("35331240"),
                        durationMs = 258_409L
                    )
                ),
                "Hello Adele" to listOf(
                    SearchResultUiModel.Song(
                        id = "1",
                        title = "Hello",
                        artistText = "Adele",
                        albumTitle = "25",
                        coverUrl = null,
                        routeTarget = SearchRouteTarget.Song("1"),
                        durationMs = 300_000L
                    ),
                    SearchResultUiModel.Song(
                        id = "2",
                        title = "Hello",
                        artistText = "Adele",
                        albumTitle = "25",
                        coverUrl = null,
                        routeTarget = SearchRouteTarget.Song("2"),
                        durationMs = 300_150L
                    )
                )
            )
        )
        val repository = DefaultWebPlaylistImportRepository(
            urlParser = WebPlaylistImportUrlParser(),
            playlistDetailRepository = FakePlaylistDetailRepository(
                header = PlaylistHeaderContent(
                    playlistId = "unused",
                    title = "unused",
                    creatorName = "unused",
                    description = "",
                    coverUrl = null,
                    trackCount = 0,
                    playCount = 0L,
                    subscribedCount = 0L
                ),
                tracks = emptyList()
            ),
            qqMusicRemoteDataSource = FakeQqMusicPlaylistRemoteDataSource(
                payload = jsonObject(
                    """
                    {
                      "code": 0,
                      "cdlist": [
                        {
                          "disstid": "7217720898",
                          "dissname": "测试匹配",
                          "nickname": "Codex",
                          "songlist": [
                            {
                              "id": 1,
                              "title": "พยายาม",
                              "interval": 258,
                              "singer": [{ "name": "O-Pavee" }],
                              "album": { "name": "พยายาม", "mid": "001" }
                            },
                            {
                              "id": 2,
                              "title": "พยายาม",
                              "interval": 258,
                              "singer": [{ "name": "O-Pavee" }],
                              "album": { "name": "พยายาม", "mid": "001" }
                            },
                            {
                              "id": 3,
                              "title": "Hello",
                              "interval": 300,
                              "singer": [{ "name": "Adele" }],
                              "album": { "name": "25", "mid": "002" }
                            },
                            {
                              "id": 4,
                              "title": "Kiss Me",
                              "interval": 191,
                              "singer": [{ "name": "Mike Angelo" }],
                              "album": { "name": "Kiss Me O.S.T", "mid": "003" }
                            }
                          ]
                        }
                      ]
                    }
                    """
                )
            ),
            searchRepository = searchRepository
        )

        val snapshot = repository.fetchPlaylistSnapshot(
            "https://i.y.qq.com/n2/m/share/details/taoge.html?platform=11&id=7217720898"
        )

        assertEquals(4, snapshot.summary.totalCount)
        assertEquals(2, snapshot.summary.matchedCount)
        assertEquals(1, snapshot.summary.ambiguousCount)
        assertEquals(1, snapshot.summary.unmatchedCount)
        assertTrue(snapshot.tracks[0].resolution is ImportedTrackResolution.Matched)
        assertTrue(snapshot.tracks[1].resolution is ImportedTrackResolution.Matched)
        assertTrue(snapshot.tracks[2].resolution is ImportedTrackResolution.Ambiguous)
        assertEquals(ImportedTrackResolution.Unmatched, snapshot.tracks[3].resolution)
        assertEquals(1, searchRepository.requestCounts["พยายาม O-Pavee"])
    }

    @Test
    fun fetchPlaylistSnapshot_shouldResolveRedirectedShortLinkBeforeParsing() = runBlocking {
        val repository = DefaultWebPlaylistImportRepository(
            urlParser = WebPlaylistImportUrlParser(),
            urlResolver = FakeWebPlaylistImportUrlResolver(
                resolvedUrl = "https://y.qq.com/n/ryqq_v2/playlist/4204621746"
            ),
            playlistDetailRepository = FakePlaylistDetailRepository(
                header = PlaylistHeaderContent(
                    playlistId = "unused",
                    title = "unused",
                    creatorName = "unused",
                    description = "",
                    coverUrl = null,
                    trackCount = 0,
                    playCount = 0L,
                    subscribedCount = 0L
                ),
                tracks = emptyList()
            ),
            qqMusicRemoteDataSource = FakeQqMusicPlaylistRemoteDataSource(
                payload = jsonObject(
                    """
                    {
                      "code": 0,
                      "cdlist": [
                        {
                          "disstid": "4204621746",
                          "dissname": "短链回归样本",
                          "nickname": "Codex",
                          "songlist": []
                        }
                      ]
                    }
                    """
                )
            ),
            searchRepository = FakeImportSearchRepository()
        )

        val snapshot = repository.fetchPlaylistSnapshot("https://t.example.com/qq-playlist")

        assertEquals(ImportedPlaylistSource.QQ_MUSIC, snapshot.source)
        assertEquals("4204621746", snapshot.playlistId)
        assertEquals("短链回归样本", snapshot.title)
    }

    @Test
    fun streamPlaylistSnapshots_shouldEmitPendingPreviewThenProgressUpdates() = runBlocking {
        val repository = DefaultWebPlaylistImportRepository(
            urlParser = WebPlaylistImportUrlParser(),
            playlistDetailRepository = FakePlaylistDetailRepository(
                header = PlaylistHeaderContent(
                    playlistId = "unused",
                    title = "unused",
                    creatorName = "unused",
                    description = "",
                    coverUrl = null,
                    trackCount = 0,
                    playCount = 0L,
                    subscribedCount = 0L
                ),
                tracks = emptyList()
            ),
            qqMusicRemoteDataSource = FakeQqMusicPlaylistRemoteDataSource(
                payload = jsonObject(
                    """
                    {
                      "code": 0,
                      "cdlist": [
                        {
                          "disstid": "4204621746",
                          "dissname": "渐进预览",
                          "nickname": "Codex",
                          "songlist": [
                            {
                              "id": 1,
                              "title": "พยายาม",
                              "interval": 258,
                              "singer": [{ "name": "O-Pavee" }],
                              "album": { "name": "พยายาม", "mid": "001" }
                            }
                          ]
                        }
                      ]
                    }
                    """
                )
            ),
            searchRepository = FakeImportSearchRepository(
                songResultsByKeyword = mapOf(
                    "พยายาม O-Pavee" to listOf(
                        SearchResultUiModel.Song(
                            id = "35331240",
                            title = "พยายาม",
                            artistText = "O-Pavee",
                            albumTitle = "พยายาม",
                            coverUrl = null,
                            routeTarget = SearchRouteTarget.Song("35331240"),
                            durationMs = 258_409L
                        )
                    )
                )
            )
        )

        val snapshots = repository.streamPlaylistSnapshots(
            "https://y.qq.com/n/ryqq_v2/playlist/4204621746"
        ).toList()

        assertEquals(2, snapshots.size)
        assertEquals("0 / 1", snapshots.first().matchingProgress.progressText)
        assertEquals(ImportedTrackResolution.Pending, snapshots.first().tracks.single().resolution)
        assertEquals("1 / 1", snapshots.last().matchingProgress.progressText)
        assertTrue(snapshots.last().tracks.single().resolution is ImportedTrackResolution.Matched)
    }

    @Test
    fun streamPlaylistSnapshots_shouldPauseAndPreserveCompletedResults() = runBlocking {
        val searchRepository = FakeImportSearchRepository(
            songResultsByKeyword = mapOf(
                "พยายาม O-Pavee" to listOf(
                    SearchResultUiModel.Song(
                        id = "35331240",
                        title = "พยายาม",
                        artistText = "O-Pavee",
                        albumTitle = "พยายาม",
                        coverUrl = null,
                        routeTarget = SearchRouteTarget.Song("35331240"),
                        durationMs = 258_409L
                    )
                )
            ),
            failuresByKeyword = mapOf(
                "Kiss Me Mike Angelo" to IllegalStateException("429 Too Many Requests")
            )
        )
        val repository = DefaultWebPlaylistImportRepository(
            urlParser = WebPlaylistImportUrlParser(),
            playlistDetailRepository = FakePlaylistDetailRepository(
                header = PlaylistHeaderContent(
                    playlistId = "unused",
                    title = "unused",
                    creatorName = "unused",
                    description = "",
                    coverUrl = null,
                    trackCount = 0,
                    playCount = 0L,
                    subscribedCount = 0L
                ),
                tracks = emptyList()
            ),
            qqMusicRemoteDataSource = FakeQqMusicPlaylistRemoteDataSource(
                payload = jsonObject(
                    """
                    {
                      "code": 0,
                      "cdlist": [
                        {
                          "disstid": "4204621746",
                          "dissname": "暂停样本",
                          "nickname": "Codex",
                          "songlist": [
                            {
                              "id": 1,
                              "title": "พยายาม",
                              "interval": 258,
                              "singer": [{ "name": "O-Pavee" }],
                              "album": { "name": "พยายาม", "mid": "001" }
                            },
                            {
                              "id": 2,
                              "title": "Kiss Me",
                              "interval": 191,
                              "singer": [{ "name": "Mike Angelo" }],
                              "album": { "name": "Kiss Me O.S.T", "mid": "002" }
                            }
                          ]
                        }
                      ]
                    }
                    """
                )
            ),
            searchRepository = searchRepository
        )

        val snapshots = repository.streamPlaylistSnapshots(
            "https://y.qq.com/n/ryqq_v2/playlist/4204621746"
        ).toList()

        val last = snapshots.last()
        assertTrue(last.matchingProgress.isPaused)
        assertEquals("1 / 2", last.matchingProgress.progressText)
        assertTrue(last.tracks.first().resolution is ImportedTrackResolution.Matched)
        assertEquals(ImportedTrackResolution.Pending, last.tracks.last().resolution)
    }
}

private class FakePlaylistDetailRepository(
    private val header: PlaylistHeaderContent,
    private val tracks: List<PlaylistTrackRow>
) : PlaylistDetailRepository {
    val requestedOffsets = mutableListOf<Int>()

    override suspend fun fetchPlaylistHeader(playlistId: String): PlaylistHeaderContent = header

    override suspend fun fetchPlaylistTracks(
        playlistId: String,
        offset: Int,
        limit: Int
    ): List<PlaylistTrackRow> {
        requestedOffsets += offset
        val endExclusive = min(offset + limit, tracks.size)
        return if (offset in 0..<endExclusive) {
            tracks.subList(offset, endExclusive)
        } else {
            emptyList()
        }
    }

    override suspend fun fetchPlaylistDynamic(playlistId: String): PlaylistDynamicInfo {
        return PlaylistDynamicInfo(
            commentCount = 0,
            isSubscribed = false,
            playCount = 0L
        )
    }

    override suspend fun updatePlaylistPlayCount(playlistId: String) = Unit
}

private class FakeQqMusicPlaylistRemoteDataSource(
    private val payload: JsonObject
) : QqMusicPlaylistRemoteDataSource {
    override suspend fun fetchPlaylistDetail(playlistId: String): JsonObject = payload
}

private class FakeWebPlaylistImportUrlResolver(
    private val resolvedUrl: String
) : WebPlaylistImportUrlResolver {
    override suspend fun resolve(rawUrl: String): String = resolvedUrl
}

private class FakeImportSearchRepository(
    private val songResultsByKeyword: Map<String, List<SearchResultUiModel.Song>> = emptyMap(),
    private val failuresByKeyword: Map<String, Throwable> = emptyMap()
) : SearchRepository {
    val requestCounts = linkedMapOf<String, Int>()

    override suspend fun fetchHotKeywords() = emptyList<com.wxy.playerlite.feature.search.SearchHotKeywordUiModel>()

    override suspend fun fetchSuggestions(keyword: String) =
        emptyList<com.wxy.playerlite.feature.search.SearchSuggestionUiModel>()

    override suspend fun search(
        keyword: String,
        type: SearchResultType
    ): List<SearchResultUiModel> {
        requestCounts[keyword] = (requestCounts[keyword] ?: 0) + 1
        require(type == SearchResultType.SONG)
        failuresByKeyword[keyword]?.let { throw it }
        return songResultsByKeyword[keyword].orEmpty()
    }

    override fun readSearchHistory(): List<String> = emptyList()

    override suspend fun recordSearchHistory(keyword: String) = Unit

    override suspend fun removeSearchHistory(keyword: String) = Unit

    override suspend fun clearSearchHistory() = Unit
}

private fun jsonObject(raw: String): JsonObject {
    return Json.parseToJsonElement(raw.trimIndent()).jsonObject
}
