package com.wxy.playerlite.feature.player.runtime

import com.wxy.playerlite.feature.player.model.AUDIO_TRACK_PLAYSTATE_STOPPED
import com.wxy.playerlite.player.AudioMeta
import com.wxy.playerlite.player.AudioMetaDisplay
import com.wxy.playerlite.player.INativePlayer
import com.wxy.playerlite.player.PlaybackOutputInfo
import com.wxy.playerlite.player.source.IPlaysource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCoordinatorTest {
    @Test
    fun setPlaybackSpeed_delegatesToPlayer() {
        val player = FakeNativePlayer()
        val coordinator = PlaybackCoordinator(
            player = player,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        val result = coordinator.setPlaybackSpeed(1.4f)

        assertEquals(0, result)
        assertEquals(1.4f, player.lastPlaybackSpeed, 0f)
    }

    private class FakeNativePlayer : INativePlayer {
        var lastPlaybackSpeed: Float = 1.0f

        override fun setProgressListener(listener: ((Long) -> Unit)?) = Unit

        override fun setPlaybackOutputInfoListener(listener: ((PlaybackOutputInfo) -> Unit)?) = Unit

        override fun setPlaybackSpeed(speed: Float): Int {
            lastPlaybackSpeed = speed
            return 0
        }

        override fun playFromSource(source: IPlaysource): Int = 0

        override fun pause(): Int = 0

        override fun resume(): Int = 0

        override fun seek(positionMs: Long): Int = 0

        override fun getDurationFromSource(source: IPlaysource): Long = 0L

        override fun loadAudioMetaFromSource(source: IPlaysource): AudioMeta {
            return AudioMeta("-", 0, 0, 0L, 0L)
        }

        override fun loadAudioMetaDisplayFromSource(source: IPlaysource): AudioMetaDisplay {
            return AudioMetaDisplay("-", "-", "-", "-", 0L)
        }

        override fun playbackState(): Int = AUDIO_TRACK_PLAYSTATE_STOPPED

        override fun stop() = Unit

        override fun close() = Unit

        override fun lastError(): String = "ok"
    }
}
