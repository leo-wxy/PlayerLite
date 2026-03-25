package com.wxy.playerlite.feature.webplaylistimport

import com.wxy.playerlite.feature.playlist.PlaylistDetailRepository
import com.wxy.playerlite.feature.playlist.PlaylistDynamicInfo
import com.wxy.playerlite.feature.playlist.PlaylistHeaderContent
import com.wxy.playerlite.feature.playlist.PlaylistTrackRow
import kotlin.math.min
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
            )
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
            )
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

private fun jsonObject(raw: String): JsonObject {
    return Json.parseToJsonElement(raw.trimIndent()).jsonObject
}
