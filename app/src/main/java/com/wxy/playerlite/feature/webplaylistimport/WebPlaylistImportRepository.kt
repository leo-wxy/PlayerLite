package com.wxy.playerlite.feature.webplaylistimport

import com.wxy.playerlite.feature.playlist.DEFAULT_DETAIL_TRACK_PAGE_SIZE
import com.wxy.playerlite.feature.playlist.PlaylistDetailRepository
import com.wxy.playerlite.feature.search.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last

interface WebPlaylistImportRepository {
    fun streamPlaylistSnapshots(rawUrl: String): Flow<ImportedPlaylistSnapshot>

    suspend fun fetchPlaylistSnapshot(rawUrl: String): ImportedPlaylistSnapshot {
        return streamPlaylistSnapshots(rawUrl).last()
    }
}

class DefaultWebPlaylistImportRepository(
    private val urlParser: WebPlaylistImportUrlParser,
    private val urlResolver: WebPlaylistImportUrlResolver = DefaultWebPlaylistImportUrlResolver(),
    private val playlistDetailRepository: PlaylistDetailRepository,
    private val qqMusicRemoteDataSource: QqMusicPlaylistRemoteDataSource,
    private val searchRepository: SearchRepository
) : WebPlaylistImportRepository {
    override fun streamPlaylistSnapshots(rawUrl: String): Flow<ImportedPlaylistSnapshot> = flow {
        val normalizedUrl = normalizeInputUrl(rawUrl)
        val ref = urlParser.parse(normalizedUrl)
        when (ref.source) {
            ImportedPlaylistSource.NETEASE -> emit(
                fetchNeteaseSnapshot(
                    ref = ref,
                    sourceUrl = normalizedUrl
                )
            )

            ImportedPlaylistSource.QQ_MUSIC -> {
                val snapshot = QqMusicPlaylistJsonMapper.parseSnapshot(
                    payload = qqMusicRemoteDataSource.fetchPlaylistDetail(ref.playlistId),
                    ref = ref,
                    sourceUrl = normalizedUrl
                )
                val initialSnapshot = snapshot.asPendingPreview()
                emit(initialSnapshot)
                ImportTrackMatcher(searchRepository)
                    .matchProgressively(initialSnapshot.tracks)
                    .collect { update ->
                        emit(
                            snapshot.copy(
                                tracks = update.tracks,
                                matchingProgress = update.progress
                            )
                        )
                    }
            }
        }
    }

    override suspend fun fetchPlaylistSnapshot(rawUrl: String): ImportedPlaylistSnapshot {
        return streamPlaylistSnapshots(rawUrl).last()
    }

    private fun ImportedPlaylistSnapshot.asPendingPreview(): ImportedPlaylistSnapshot {
        return copy(
            tracks = tracks.map { track ->
                if (track.resolution == ImportedTrackResolution.Unmatched) {
                    track.copy(resolution = ImportedTrackResolution.Pending)
                } else {
                    track
                }
            },
            matchingProgress = ImportedPlaylistMatchingProgress(
                completedCount = tracks.count { track ->
                    track.resolution != ImportedTrackResolution.Unmatched
                },
                totalCount = tracks.size
            )
        )
    }

    private suspend fun normalizeInputUrl(rawUrl: String): String {
        return runCatching {
            urlParser.parse(rawUrl)
            rawUrl.trim()
        }.getOrElse {
            urlResolver.resolve(rawUrl)
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
