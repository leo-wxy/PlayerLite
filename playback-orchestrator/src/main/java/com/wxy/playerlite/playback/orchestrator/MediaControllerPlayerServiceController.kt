package com.wxy.playerlite.playback.orchestrator

import android.content.Context
import com.wxy.playerlite.playback.client.PlayerServiceBridge
import com.wxy.playerlite.playback.client.RemotePlaybackSnapshot
import com.wxy.playerlite.playback.model.PlayableItem
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.player.AudioEffectPreset

class MediaControllerPlayerServiceController(
    context: Context,
    onControllerError: (String) -> Unit
) : PlayerServiceController {
    private val delegate = PlayerServiceBridge(
        context = context,
        onControllerError = onControllerError
    )

    override fun prewarmConnection() = delegate.prewarmConnection()

    override fun ensurePlaybackServiceStartedForPlayback() =
        delegate.ensurePlaybackServiceStartedForPlayback()

    override fun connectIfNeeded() = delegate.connectIfNeeded()

    override fun syncQueue(
        queue: List<PlayableItem>,
        activeIndex: Int,
        playWhenReady: Boolean,
        startPositionMs: Long
    ): Boolean = delegate.syncQueue(queue, activeIndex, playWhenReady, startPositionMs)

    override fun play(): Boolean = delegate.play()

    override fun pause(): Boolean = delegate.pause()

    override fun seekTo(positionMs: Long): Boolean = delegate.seekTo(positionMs)

    override fun seekToNextMediaItem(): Boolean = delegate.seekToNextMediaItem()

    override fun seekToPreviousMediaItem(): Boolean = delegate.seekToPreviousMediaItem()

    override fun stop(): Boolean = delegate.stop()

    override fun clearCache(): Boolean = delegate.clearCache()

    override fun setPlaybackSpeed(speed: Float, onResult: ((Boolean) -> Unit)?): Boolean {
        return delegate.setPlaybackSpeed(speed, onResult)
    }

    override fun setAudioEffectPreset(
        audioEffectPreset: AudioEffectPreset,
        onResult: ((Boolean) -> Unit)?
    ): Boolean {
        return delegate.setAudioEffectPreset(audioEffectPreset, onResult)
    }

    override fun setPlaybackMode(playbackMode: PlaybackMode, onResult: ((Boolean) -> Unit)?): Boolean {
        return delegate.setPlaybackMode(playbackMode, onResult)
    }

    override fun setDisplayMetadata(title: String?, subtitle: String?): Boolean {
        return delegate.setDisplayMetadata(title = title, subtitle = subtitle)
    }

    override fun currentSnapshot(): RemotePlaybackSnapshot? = delegate.currentSnapshot()

    override fun release() = delegate.release()
}
