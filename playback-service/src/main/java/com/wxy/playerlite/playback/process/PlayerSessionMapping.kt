package com.wxy.playerlite.playback.process

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.wxy.playerlite.player.PlaybackSpeed

internal object PlayerSessionMapping {
    fun media3PlaybackState(
        nativePlaybackState: Int,
        hasMediaItem: Boolean,
        playWhenReady: Boolean,
        isPreparing: Boolean
    ): Int {
        if (!hasMediaItem) {
            return Player.STATE_IDLE
        }
        return when (nativePlaybackState) {
            PLAYBACK_STATE_PLAYING,
            PLAYBACK_STATE_PAUSED -> Player.STATE_READY

            else -> if (playWhenReady || isPreparing) {
                Player.STATE_BUFFERING
            } else {
                Player.STATE_IDLE
            }
        }
    }

    fun isPlaying(nativePlaybackState: Int): Boolean {
        return nativePlaybackState == PLAYBACK_STATE_PLAYING
    }

    fun playbackParameters(playbackSpeed: Float): PlaybackParameters {
        return PlaybackParameters(PlaybackSpeed.normalizeValue(playbackSpeed))
    }
}
