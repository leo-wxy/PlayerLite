package com.wxy.playerlite.playback.orchestrator.detail

import com.wxy.playerlite.playback.model.MusicInfo

interface PlaybackQueueMetadataRepository {
    suspend fun fetchSongs(songIds: List<String>): List<MusicInfo>
}
