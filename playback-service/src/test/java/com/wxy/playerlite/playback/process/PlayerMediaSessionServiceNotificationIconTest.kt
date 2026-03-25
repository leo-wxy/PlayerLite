package com.wxy.playerlite.playback.process

import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.model.PlaybackMetadataExtras
import com.wxy.playerlite.playback.model.PlaybackMode
import com.wxy.playerlite.player.AudioEffectPreset
import com.wxy.playerlite.playback.service.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerMediaSessionServiceNotificationIconTest {
    @Test
    fun resolveNotificationSmallIcon_shouldAlwaysUseDedicatedNotificationIcon() {
        assertEquals(
            R.drawable.ic_playerlite_notification_small,
            resolveNotificationSmallIcon()
        )
    }

    @Test
    fun resolveNotificationSmallIcon_shouldNotFallBackToSystemMediaPlayIcon() {
        assertEquals(
            R.drawable.ic_playerlite_notification_small,
            resolveNotificationSmallIcon()
        )
    }

    @Test
    fun resolveNotificationTitle_shouldPreferDisplayOverride() {
        val state = PlaybackProcessState(
            tracks = listOf(
                PlaybackTrack(
                    playable = PlayableItemSnapshot(
                        id = "track-1",
                        title = "夜曲",
                        artistText = "周杰伦",
                        playbackUri = "https://example.com/night.mp3"
                    )
                )
            ),
            activeIndex = 0,
            displayTitleOverride = "第二句歌词"
        )

        assertEquals(
            "第二句歌词",
            resolveNotificationTitle(state, fallbackPackageName = "com.wxy.playerlite")
        )
    }

    @Test
    fun resolveNotificationSubtitle_shouldPreferDisplayOverride() {
        val state = PlaybackProcessState(
            tracks = listOf(
                PlaybackTrack(
                    playable = PlayableItemSnapshot(
                        id = "track-1",
                        title = "夜曲",
                        artistText = "周杰伦",
                        playbackUri = "https://example.com/night.mp3"
                    )
                )
            ),
            activeIndex = 0,
            statusText = "Playing",
            displaySubtitleOverride = "夜曲 - 周杰伦"
        )

        assertEquals("夜曲 - 周杰伦", resolveNotificationSubtitle(state))
    }

    @Test
    fun resolveNotificationSubtitle_shouldFallBackToTrackSummaryWhenOverrideMissing() {
        val state = PlaybackProcessState(
            tracks = listOf(
                PlaybackTrack(
                    playable = PlayableItemSnapshot(
                        id = "track-1",
                        title = "夜曲",
                        artistText = "周杰伦",
                        playbackUri = "https://example.com/night.mp3"
                    )
                )
            ),
            activeIndex = 0,
            statusText = "Playing"
        )

        assertEquals("夜曲 - 周杰伦", resolveNotificationSubtitle(state))
    }

    @Test
    fun buildSessionExtras_shouldPublishAudioEffectPresetAlongsidePlaybackFields() {
        val extras = buildSessionExtras(
            PlaybackProcessState(
                statusText = "Playing",
                isSeekSupported = true,
                playbackSpeed = 1.5f,
                playbackMode = PlaybackMode.SINGLE_LOOP,
                audioEffectPreset = AudioEffectPreset.WARM
            )
        )

        assertEquals("Playing", PlaybackMetadataExtras.readStatusText(extras))
        assertEquals(true, PlaybackMetadataExtras.readSeekSupported(extras))
        assertEquals(1.5f, PlaybackMetadataExtras.readPlaybackSpeed(extras) ?: 0f, 0f)
        assertEquals(PlaybackMode.SINGLE_LOOP, PlaybackMetadataExtras.readPlaybackMode(extras))
        assertEquals(AudioEffectPreset.WARM, PlaybackMetadataExtras.readAudioEffectPreset(extras))
    }
}
