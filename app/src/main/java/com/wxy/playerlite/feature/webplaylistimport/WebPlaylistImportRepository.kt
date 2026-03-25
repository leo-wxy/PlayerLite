package com.wxy.playerlite.feature.webplaylistimport

import com.wxy.playerlite.feature.playlist.DEFAULT_DETAIL_TRACK_PAGE_SIZE
import com.wxy.playerlite.feature.playlist.PlaylistDetailRepository

interface WebPlaylistImportRepository {
    suspend fun fetchPlaylistSnapshot(rawUrl: String): ImportedPlaylistSnapshot
}

class DefaultWebPlaylistImportRepository(
    private val urlParser: WebPlaylistImportUrlParser,
    private val playlistDetailRepository: PlaylistDetailRepository,
    private val qqMusicRemoteDataSource: QqMusicPlaylistRemoteDataSource
) : WebPlaylistImportRepository {
    override suspend fun fetchPlaylistSnapshot(rawUrl: String): ImportedPlaylistSnapshot {
        val ref = urlParser.parse(rawUrl)
        return when (ref.source) {
            ImportedPlaylistSource.NETEASE -> fetchNeteaseSnapshot(
                ref = ref,
                sourceUrl = rawUrl
            )

            ImportedPlaylistSource.QQ_MUSIC -> QqMusicPlaylistJsonMapper.parseSnapshot(
                payload = qqMusicRemoteDataSource.fetchPlaylistDetail(ref.playlistId),
                ref = ref,
                sourceUrl = rawUrl
            )
        }
    }

    private suspend fun fetchNeteaseSnapshot(
        ref: ParsedPlaylistRef,
        sourceUrl: String
    ): ImportedPlaylistSnapshot {
        val header = playlistDetailRepository.fetchPlaylistHeader(ref.playlistId)
        val tracks = buildList {
            var offset = 0
            while (true) {
                val page = playlistDetailRepository.fetchPlaylistTracks(
                    playlistId = ref.playlistId,
                    offset = offset,
                    limit = DEFAULT_DETAIL_TRACK_PAGE_SIZE
                )
                if (page.isEmpty()) {
                    break
                }
                addAll(page.map { row ->
                    ImportedPlaylistTrack(
                        sourceTrackId = row.trackId,
                        title = row.title,
                        artistNames = row.artistText.split('/')
                            .map(String::trim)
                            .filter(String::isNotBlank),
                        artistText = row.artistText,
                        albumTitle = row.albumTitle,
                        coverUrl = row.coverUrl,
                        durationMs = row.durationMs,
                        resolution = ImportedTrackResolution.Direct(
                            song = ResolvedImportedSong(
                                songId = row.trackId,
                                title = row.title,
                                artistText = row.artistText,
                                primaryArtistId = row.primaryArtistId,
                                albumTitle = row.albumTitle,
                                coverUrl = row.coverUrl,
                                durationMs = row.durationMs
                            )
                        )
                    )
                })
                if (page.size < DEFAULT_DETAIL_TRACK_PAGE_SIZE || size >= header.trackCount) {
                    break
                }
                offset += page.size
            }
        }
        return ImportedPlaylistSnapshot(
            source = ref.source,
            playlistId = ref.playlistId,
            sourceUrl = sourceUrl,
            title = header.title,
            creatorName = header.creatorName,
            description = header.description,
            coverUrl = header.coverUrl,
            tracks = tracks
        )
    }
}
