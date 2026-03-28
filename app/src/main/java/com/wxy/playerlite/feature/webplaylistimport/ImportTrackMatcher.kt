package com.wxy.playerlite.feature.webplaylistimport

import com.wxy.playerlite.feature.search.SearchRepository
import com.wxy.playerlite.feature.search.SearchResultType
import com.wxy.playerlite.feature.search.SearchResultUiModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.supervisorScope
import kotlin.math.abs

internal class ImportTrackQueryBuilder {
    fun build(track: ImportedPlaylistTrack): List<String> {
        val title = track.title.trim()
        if (title.isBlank()) {
            return emptyList()
        }
        val primaryArtist = track.artistNames.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: track.artistText.split('/')
                .map(String::trim)
                .firstOrNull { it.isNotBlank() }
        val primaryQuery = listOfNotNull(title, primaryArtist)
            .joinToString(separator = " ")
            .trim()
        val fallbackQuery = track.albumTitle
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { albumTitle ->
                listOfNotNull(title, primaryArtist, albumTitle)
                    .joinToString(separator = " ")
                    .trim()
            }
        return buildList {
            add(primaryQuery.ifBlank { title })
            fallbackQuery
                ?.takeIf { it.isNotBlank() && it != first() }
                ?.let(::add)
        }
    }
}

internal class ImportTrackMatcher(
    private val searchRepository: SearchRepository,
    private val queryBuilder: ImportTrackQueryBuilder = ImportTrackQueryBuilder(),
    private val maxConcurrentSearches: Int = 2,
    private val maxConsecutiveFailures: Int = 2
) {
    private val queryCache = linkedMapOf<String, List<SearchResultUiModel.Song>>()

    suspend fun matchAll(tracks: List<ImportedPlaylistTrack>): List<ImportedPlaylistTrack> {
        return matchProgressively(tracks).lastOrOriginal(tracks).tracks
    }

    fun matchProgressively(tracks: List<ImportedPlaylistTrack>): Flow<ImportTrackMatchingUpdate> = flow {
        if (tracks.isEmpty()) {
            emit(
                ImportTrackMatchingUpdate(
                    tracks = emptyList(),
                    progress = ImportedPlaylistMatchingProgress(completedCount = 0, totalCount = 0)
                )
            )
            return@flow
        }
        val workingTracks = tracks.toMutableList()
        val totalCount = workingTracks.size
        var completedCount = workingTracks.count {
            !it.resolution.isPending() && !it.resolution.needsMatch()
        }
        var consecutiveFailures = 0
        val pendingIndices = workingTracks.indices.filter {
            workingTracks[it].resolution.needsMatch()
        }
        if (pendingIndices.isEmpty()) {
            emit(
                ImportTrackMatchingUpdate(
                    tracks = workingTracks.toList(),
                    progress = ImportedPlaylistMatchingProgress.completed(totalCount)
                )
            )
            return@flow
        }
        var remainingIndices = pendingIndices
        while (remainingIndices.isNotEmpty()) {
            val failedIndices = mutableListOf<Int>()
            var progressedInRound = false
            remainingIndices.chunked(maxConcurrentSearches.coerceAtLeast(1)).forEach { chunk ->
                val attempts = supervisorScope {
                    chunk.map { index ->
                        async {
                            MatchAttempt(
                                index = index,
                                result = runCatching {
                                    match(workingTracks[index])
                                }
                            )
                        }
                    }.awaitAll()
                }
                attempts.forEach { attempt ->
                    val resolution = attempt.result.getOrNull()
                    if (resolution != null) {
                        workingTracks[attempt.index] = workingTracks[attempt.index].copy(
                            resolution = resolution
                        )
                        completedCount += 1
                        consecutiveFailures = 0
                        progressedInRound = true
                        emit(
                            ImportTrackMatchingUpdate(
                                tracks = workingTracks.toList(),
                                progress = ImportedPlaylistMatchingProgress(
                                    completedCount = completedCount,
                                    totalCount = totalCount
                                )
                            )
                        )
                    } else {
                        failedIndices += attempt.index
                        consecutiveFailures += 1
                        val error = attempt.result.exceptionOrNull()
                        if (shouldPause(error, consecutiveFailures)) {
                            emit(
                                ImportTrackMatchingUpdate(
                                    tracks = workingTracks.toList(),
                                    progress = ImportedPlaylistMatchingProgress(
                                        completedCount = completedCount,
                                        totalCount = totalCount,
                                        isPaused = true,
                                        pauseMessage = buildPauseMessage(error)
                                    )
                                )
                            )
                            return@flow
                        }
                    }
                }
            }
            if (failedIndices.isEmpty()) {
                break
            }
            if (!progressedInRound) {
                emit(
                    ImportTrackMatchingUpdate(
                        tracks = workingTracks.toList(),
                        progress = ImportedPlaylistMatchingProgress(
                            completedCount = completedCount,
                            totalCount = totalCount,
                            isPaused = true,
                            pauseMessage = "匹配已暂停，请稍后重试"
                        )
                    )
                )
                return@flow
            }
            remainingIndices = failedIndices
        }
    }

    suspend fun match(track: ImportedPlaylistTrack): ImportedTrackResolution {
        val queries = queryBuilder.build(track)
        if (queries.isEmpty()) {
            return ImportedTrackResolution.Unmatched
        }
        val candidates = linkedMapOf<String, SearchResultUiModel.Song>()
        queries.forEach { query ->
            searchSongs(query).forEach { song ->
                candidates.putIfAbsent(song.id, song)
            }
        }
        if (candidates.isEmpty()) {
            return ImportedTrackResolution.Unmatched
        }
        val scored = candidates.values
            .map { song ->
                ScoredSong(
                    song = song,
                    score = score(track, song)
                )
            }
            .sortedByDescending { it.score }
        val top = scored.firstOrNull() ?: return ImportedTrackResolution.Unmatched
        if (top.score < MATCH_THRESHOLD) {
            return ImportedTrackResolution.Unmatched
        }
        val second = scored.getOrNull(1)
        return if (second != null &&
            second.score >= MATCH_THRESHOLD &&
            top.score - second.score <= AMBIGUOUS_GAP_THRESHOLD
        ) {
            ImportedTrackResolution.Ambiguous(
                candidates = scored
                    .takeWhile { candidate -> top.score - candidate.score <= AMBIGUOUS_GAP_THRESHOLD }
                    .map { it.song.toResolvedImportedSong() }
            )
        } else {
            ImportedTrackResolution.Matched(
                song = top.song.toResolvedImportedSong()
            )
        }
    }

    private suspend fun searchSongs(query: String): List<SearchResultUiModel.Song> {
        return queryCache.getOrPut(query) {
            searchRepository.search(query, SearchResultType.SONG)
                .filterIsInstance<SearchResultUiModel.Song>()
        }
    }

    private fun score(
        track: ImportedPlaylistTrack,
        song: SearchResultUiModel.Song
    ): Int {
        return titleScore(track.title, song.title) +
            artistScore(track, song) +
            albumScore(track.albumTitle, song.albumTitle) +
            durationScore(track.durationMs, song.durationMs)
    }

    private fun titleScore(sourceTitle: String, candidateTitle: String): Int {
        val normalizedSource = normalizeText(sourceTitle)
        val normalizedCandidate = normalizeText(candidateTitle)
        if (normalizedSource.isBlank() || normalizedCandidate.isBlank()) {
            return 0
        }
        return when {
            normalizedSource == normalizedCandidate -> 40
            normalizedSource.contains(normalizedCandidate) ||
                normalizedCandidate.contains(normalizedSource) -> 24

            else -> 0
        }
    }

    private fun artistScore(
        track: ImportedPlaylistTrack,
        song: SearchResultUiModel.Song
    ): Int {
        val sourceArtists = track.artistNames
            .ifEmpty {
                track.artistText.split('/').map(String::trim)
            }
            .map(::normalizeText)
            .filter(String::isNotBlank)
        val candidateArtists = song.artistText.split('/')
            .map(String::trim)
            .map(::normalizeText)
            .filter(String::isNotBlank)
        if (sourceArtists.isEmpty() || candidateArtists.isEmpty()) {
            return 0
        }
        val sourcePrimary = sourceArtists.first()
        val candidatePrimary = candidateArtists.first()
        return when {
            textMatches(sourcePrimary, candidatePrimary) -> 25
            sourceArtists.any { source ->
                candidateArtists.any { candidate -> textMatches(source, candidate) }
            } -> 15

            else -> 0
        }
    }

    private fun albumScore(
        sourceAlbum: String,
        candidateAlbum: String
    ): Int {
        val normalizedSource = normalizeText(sourceAlbum)
        val normalizedCandidate = normalizeText(candidateAlbum)
        if (normalizedSource.isBlank() || normalizedCandidate.isBlank()) {
            return 0
        }
        return when {
            normalizedSource == normalizedCandidate -> 10
            normalizedSource.contains(normalizedCandidate) ||
                normalizedCandidate.contains(normalizedSource) -> 4

            else -> 0
        }
    }

    private fun durationScore(
        sourceDurationMs: Long,
        candidateDurationMs: Long
    ): Int {
        if (sourceDurationMs <= 0L || candidateDurationMs <= 0L) {
            return 0
        }
        val delta = abs(sourceDurationMs - candidateDurationMs)
        return when {
            delta <= 3_000L -> 20
            delta <= 5_000L -> 14
            delta <= 8_000L -> 6
            delta <= 15_000L -> -6
            else -> -30
        }
    }

    private fun normalizeText(value: String): String {
        return value.lowercase()
            .replace(NON_WORD_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun textMatches(source: String, candidate: String): Boolean {
        if (source == candidate || source.contains(candidate) || candidate.contains(source)) {
            return true
        }
        val sourceTokens = source.split(' ').filter(String::isNotBlank).toSet()
        val candidateTokens = candidate.split(' ').filter(String::isNotBlank).toSet()
        return sourceTokens.intersect(candidateTokens).isNotEmpty()
    }

    private data class ScoredSong(
        val song: SearchResultUiModel.Song,
        val score: Int
    )

    private data class MatchAttempt(
        val index: Int,
        val result: Result<ImportedTrackResolution>
    )

    private fun shouldPause(
        error: Throwable?,
        consecutiveFailures: Int
    ): Boolean {
        return isRateLimited(error) || consecutiveFailures >= maxConsecutiveFailures
    }

    private fun isRateLimited(error: Throwable?): Boolean {
        val message = error?.message.orEmpty()
        return message.contains("429") ||
            message.contains("rate", ignoreCase = true) ||
            message.contains("too many", ignoreCase = true) ||
            message.contains("频繁") ||
            message.contains("限制")
    }

    private fun buildPauseMessage(error: Throwable?): String {
        return if (isRateLimited(error)) {
            "匹配请求过于频繁，已暂停后续匹配"
        } else {
            "匹配出现连续失败，已暂停后续匹配"
        }
    }

    private companion object {
        val NON_WORD_REGEX = Regex("[()（）\\[\\]【】,，.!！?？:：'‘’\"“”~～]")
        val WHITESPACE_REGEX = Regex("\\s+")
        const val MATCH_THRESHOLD = 55
        const val AMBIGUOUS_GAP_THRESHOLD = 5
    }
}

internal data class ImportTrackMatchingUpdate(
    val tracks: List<ImportedPlaylistTrack>,
    val progress: ImportedPlaylistMatchingProgress
)

private fun ImportedTrackResolution.needsMatch(): Boolean {
    return this == ImportedTrackResolution.Pending || this == ImportedTrackResolution.Unmatched
}

private suspend fun Flow<ImportTrackMatchingUpdate>.lastOrOriginal(
    originalTracks: List<ImportedPlaylistTrack>
): ImportTrackMatchingUpdate {
    var latest: ImportTrackMatchingUpdate? = null
    collect { latest = it }
    return latest ?: ImportTrackMatchingUpdate(
        tracks = originalTracks,
        progress = ImportedPlaylistMatchingProgress.completed(originalTracks.size)
    )
}
