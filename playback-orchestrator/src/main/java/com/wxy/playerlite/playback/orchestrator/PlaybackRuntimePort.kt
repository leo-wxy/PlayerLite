package com.wxy.playerlite.playback.orchestrator

import com.wxy.playerlite.core.playlist.PlaylistItem
import com.wxy.playerlite.playback.model.PlaybackAudioQuality
import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.PlaybackOutputInfo

interface PlaybackRuntimePort {
    fun playbackQueueItems(): List<PlaylistItem>
    fun playbackQueueActiveIndex(): Int
    fun currentPlaybackMode(): PlaybackMode
    fun playbackQueueItemsInOriginalOrder(): List<PlaylistItem>
    fun replaceQueueFromDetail(items: List<PlaylistItem>, activeIndex: Int)
    fun updatePlaylistItemsMetadata(updatesById: Map<String, PlaylistItem>)
    fun updateLocalPlaybackMode(playbackMode: PlaybackMode)
    fun selectPlaylistItem(index: Int)
    fun removePlaylistItem(index: Int)
    fun clearPlaylist()
    fun movePlaylistItem(fromIndex: Int, toIndex: Int)
    fun updateLocalPlaybackSpeed(playbackSpeed: Float)
    fun revertPendingPlaybackSpeed(playbackSpeed: Float)
    fun updateLocalAudioEffectPreset(audioEffectPreset: AudioEffectPreset)
    fun revertPendingAudioEffectPreset(audioEffectPreset: AudioEffectPreset)
    fun updateLocalPreferredAudioQuality(audioQuality: PlaybackAudioQuality)
    fun revertPendingPreferredAudioQuality(audioQuality: PlaybackAudioQuality)

    fun updateRemotePlaybackState(
        playbackState: Int,
        positionMs: Long,
        durationMs: Long,
        isSeekSupported: Boolean,
        isPreparing: Boolean = false,
        playbackSpeed: Float,
        playbackMode: PlaybackMode,
        currentMediaId: String?,
        isProgressAdvancing: Boolean,
        currentPlayable: PlayableItemSnapshot?,
        playbackOutputInfo: PlaybackOutputInfo?,
        audioMeta: AudioMetaDisplay?,
        audioEffectPreset: AudioEffectPreset? = null,
        preferredAudioQuality: PlaybackAudioQuality? = null,
        appliedAudioQuality: PlaybackAudioQuality? = null
    )

    fun syncActiveItemById(itemId: String?)
    fun setStatusText(statusText: String)
}
