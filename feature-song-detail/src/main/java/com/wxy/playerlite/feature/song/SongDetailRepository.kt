package com.wxy.playerlite.feature.song

interface SongDetailFeatureRepository {
    suspend fun loadSongDetail(ref: SongRef): SongDetailContent

    suspend fun favoriteSong(songId: String): Result<Unit>
}

interface SongDetailActionGateway {
    fun play(item: com.wxy.playerlite.core.playlist.PlaylistItem): Boolean

    fun playNext(item: com.wxy.playerlite.core.playlist.PlaylistItem): Boolean
}
