package com.wxy.playerlite.playback.process

import com.wxy.playerlite.playback.model.PlayableItemSnapshot
import com.wxy.playerlite.playback.service.R
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
